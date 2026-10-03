# Design Notes

Why Noct looks the way it does. This is a condensed English version of the
original language specification, written for the *Programming Translators*
course. The original Serbian documents are kept in [original/](original/).

## The domain

Survival-horror games are less about reflexes and open-world exploration and
more about atmosphere, tension, limited resources and puzzle progression.
Their key ingredients are:

- scenes (rooms) and the passages between them,
- interactive things: doors, keys, safes, objects,
- game state: health, inventory and progress flags,
- conditions under which an action can or cannot happen,
- events triggered by entering a room or interacting with something.

Most of the logic of such a game is a set of rules about how the world
reacts to the player. That makes it a good fit for a small, dedicated
language.

## Why a language and not a config file

A config format such as JSON or YAML can describe static facts ("this door
leads to the hall and is locked"), but not behaviour: conditional branches,
sequences of events, and changes to game state. Embedding that behaviour in
a general-purpose language works, but mixes the story with engine code.

A domain-specific language gives you:

- vocabulary that matches the domain (`room`, `door`, `on inspect`),
- scripts that are easier to read than the equivalent Java or Python,
- **static checking of the whole scenario before it runs**: every door leads
  somewhere real, every key exists, every condition is well typed,
- a clean separation between the story and the engine that runs it.

The goal is not to implement a whole game engine, but to describe the rules
and scenario of a game at a high level.

## Paradigm: imperative and event-driven

Noct is **imperative**: handlers are sequences of statements that change
state (assign, give, take, lock, unlock), executed in order.

It is also **event-driven**: there are no loops, functions or a `main`. A
program is a set of reactions to events (entering a room, inspecting an
object, using an item). The player's actions decide which code runs and
when, which mirrors how these games actually work.

Other paradigms were considered and rejected:

- **Object-oriented** would mean modelling class hierarchies and methods,
  adding complexity without making scenarios easier to express.
- **Functional** emphasises immutable data, which fights a domain that is
  all about changing state.
- **Logic** programming is good at describing rules, but awkward for the
  step-by-step sequences of events that make up gameplay scripting.

## State: one global world

All mutable state lives in a single global environment: vars, flags, the
inventory, the player's current room, and the locked/unlocked state of every
door. Every handler in every room sees and modifies the same state.

This matches the domain. In these games a change in one place often affects
another: turning on a generator in the basement powers the gate upstairs.
Local scopes would add machinery without helping to express that.

Names, however, are resolved **statically**. The checker knows what every
identifier refers to before the game starts. Only the *values* are dynamic,
and conditions such as `has(key)` or `health <= 0` are evaluated at the
moment the handler runs.

## Types

The type system is deliberately small: integers for quantities (health,
ammo), booleans for flags and conditions, and string literals for narration.
Domain entities (rooms, doors, objects, items) are separate kinds of names
that can only appear where they make sense: you can `give` an item, but not
a room.

Users cannot define new types, and there are no casts. Neither is needed to
describe a scenario, and leaving them out removes a whole class of mistakes:
`set health, "ten"` or `unlock health` simply cannot be written.

Types are never declared; they are inferred. A `var` is always a number and
a `flag` is always a boolean, and every expression's type follows from its
operands. The checker rejects any mismatch before the game starts.

## Static vs. dynamic checks

| Checked before running                         | Checked while playing             |
|------------------------------------------------|-----------------------------------|
| every name refers to the right kind of thing   | which items the player carries    |
| types of every expression and assignment       | current values of vars and flags  |
| doors lead to real rooms, keys exist           | whether a door is locked right now |
| no duplicate names or handlers                 | division by zero                  |

Everything that can be known from the text alone is checked up front. What
remains depends on the player's choices, which is exactly what the game is
supposed to leave open.

## Why indentation

Scenarios are deeply nested (room → handler → `if` → actions), and they are
written by people who think in scenes, not braces. Python-style indentation
keeps scripts short and makes their structure visible at a glance. In the
implementation, the lexer converts indentation into `INDENT` and `DEDENT`
tokens, so the parser still works with an ordinary block-structured grammar.

## Error messages

A language is only as pleasant as its error messages, and a scenario writer
is not necessarily a programmer. Every error points at the exact source
location, shows the line with the problem underlined, and where possible
explains the rule or suggests a fix, in the style of the Rust compiler:
misspelled names get a "did you mean ...?" suggestion based on edit
distance, and common mistakes (`set x = 1` instead of `set x, 1`, a keyword
used as a name, chained comparisons) get a dedicated explanation.

## Implementation pipeline

```
source → preprocessor → lexer → parser → checker → interpreter
                         tokens    AST     typed AST    game
```

- **Preprocessor**: normalises line endings and tabs.
- **Lexer**: produces tokens, including synthetic `INDENT` and `DEDENT`.
- **Parser**: hand-written recursive descent, building an abstract syntax tree.
- **Checker**: two passes. The first collects all global declarations, so
  rooms and items can be referenced before they are declared. The second
  resolves every name and infers and checks every type.
- **Interpreter**: walks the AST in response to player commands.
