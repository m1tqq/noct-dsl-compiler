# Noct Language Reference

Noct is a small, statically checked language for describing survival-horror
text adventures. A Noct program declares a world (rooms, doors, objects,
items and game state) and attaches event handlers to it. When the game is
played, the player's commands fire those handlers.

For the exact syntax, see [grammar.md](grammar.md). For the reasoning behind
the design, see [design.md](design.md).

## Contents

1. [A first program](#a-first-program)
2. [Lexical structure](#lexical-structure)
3. [Types](#types)
4. [Declarations](#declarations)
5. [Rooms, doors and objects](#rooms-doors-and-objects)
6. [Event handlers](#event-handlers)
7. [Actions](#actions)
8. [Expressions](#expressions)
9. [Playing a game](#playing-a-game)
10. [Static checks](#static-checks)
11. [Runtime errors](#runtime-errors)
12. [Exit codes](#exit-codes)

## A first program

```noct
item key "Rusty Key"
var health = 100

room cell "Cell":
    door exit -> hall locked requires key
    object bed "Iron Bed"

    on enter:
        say "You wake up on a cold floor. Health: {health}"

    on inspect bed:
        if not has(key):
            give key
            say "Something is taped under the bed. A key."

room hall "Hallway":
    on enter:
        end "You made it out."
```

Running `noct play` on this file puts the player in `cell`, the first room
declared. Typing `inspect bed` gives them the key, and `go exit` unlocks the
door with it and moves them to `hall`, which ends the game.

## Lexical structure

**Comments** start with `#` and run to the end of the line.

**Identifiers** start with a letter or `_`, followed by letters, digits or
`_`. They are case-sensitive: `Key` and `key` are different names.

**Keywords** are reserved, lower-case and case-sensitive. The full list is in
[grammar.md](grammar.md#lexical-grammar).

**Number literals** are non-negative decimal integers up to `2147483647`.
Negative numbers are written with unary minus: `-5`.

**Boolean literals** are `true` and `false`.

**String literals** are enclosed in double quotes and must fit on one line.
They support these escapes:

| Escape | Meaning         |
|--------|-----------------|
| `\"`   | double quote    |
| `\\`   | backslash       |
| `\n`   | newline         |
| `\t`   | tab             |
| `\{`   | literal `{`     |
| `\}`   | literal `}`     |

**Indentation** delimits blocks. A line ending in `:` opens a block, and the
following lines must be indented deeper. A tab counts as 4 spaces. See
[grammar.md](grammar.md#indentation) for the exact rules.

## Types

Noct has two value types:

| Type     | Values                       | Used for                          |
|----------|------------------------------|-----------------------------------|
| `NUMBER` | 32-bit signed integers       | health, ammo, counters            |
| `BOOL`   | `true`, `false`              | flags, conditions                 |

Strings exist only as literals. They are used for narration (`say`, `end`)
and display names, and cannot be stored, compared or computed.

Types are never written in the source. Every expression's type is inferred
during static analysis, and every operator, assignment and condition is
type-checked before the game starts. There are no implicit conversions and
no casts: `set health, true` is rejected.

Arithmetic is integer arithmetic. Division truncates toward zero
(`7 / 2 == 3`, `-7 / 2 == -3`) and overflow wraps around in two's complement.

## Declarations

Top-level declarations introduce global names. Items, flags, vars and rooms
share a single namespace, so a flag and a room cannot both be called `power`.

### `item`

```noct
item key "Rusty Key"
```

Declares an item the player can carry. The string is its display name, shown
in the inventory. Items are given and taken with `give` and `take`, tested
with `has(...)`, and used with `on use` handlers.

### `flag`

```noct
flag power = false
```

Declares a global `BOOL` variable. The initial value must be a literal.

### `var`

```noct
var health = 100
var damage = 10 + 5 * 2
```

Declares a global `NUMBER` variable. The initializer is any `NUMBER`
expression and may refer to vars and flags declared **above** it. Forward
references are a static error, so initialization order is always the order
in the file.

### `room`

```noct
room lab "Laboratory":
    ...
```

Declares a room. The string is its display name, printed when the player
enters. The body holds doors, objects and handlers, in any order, and must
contain at least one of them. The **first room declared is the starting
room**.

Rooms may be referenced (as door targets) before they are declared.

## Rooms, doors and objects

Doors and objects belong to the room they are declared in. Their names must
be unique within that room, so a room cannot have a door and an object with
the same name. They may reuse names from other rooms.

### `door`

```noct
door exit -> hall
door gate -> courtyard locked
door cell_door -> corridor locked requires key
```

A door connects the current room to a target room. Doors are one-way: to let
the player come back, declare a door in the target room too.

| Option              | Meaning                                                |
|---------------------|--------------------------------------------------------|
| `locked`            | The door starts locked.                                |
| `requires <item>`   | The item that opens this door.                         |

The options can appear in either order, each at most once.

When the player tries to go through a door:

- If the door is unlocked, the player moves to the target room.
- If the door is locked and has `requires <item>`, and the player carries
  that item, the door unlocks automatically and the player moves through.
- Otherwise the door stays locked and the player stays put.

A locked door without `requires` can only be opened by an `unlock` action in
a handler. That is the tool for puzzles such as "the gate opens once the
power is on".

### `object`

```noct
object generator "Power Generator"
```

An object is something in the room the player can inspect or use items on.
The string is its display name.

## Event handlers

Handlers connect player actions to code. They are declared inside a room and
only fire while the player is in that room.

### `on enter`

```noct
on enter:
    say "The air smells of rust."
```

Runs every time the player enters the room, including at the start of the
game for the starting room. A room may have several `on enter` handlers;
they run in declaration order.

To run something only on the first visit, use a flag:

```noct
flag seen_lab = false

room lab "Laboratory":
    on enter:
        if not seen_lab:
            set seen_lab, true
            say "Something moves behind the glass."
```

### `on inspect`

```noct
on inspect generator:
    say "It's silent."
```

Runs when the player types `inspect generator`. The target must be a door or
object in the same room. Each target may have at most one `inspect` handler.

### `on use`

```noct
on use key on gate:
    unlock gate
    say "The lock gives way."
```

Runs when the player types `use key on gate` while carrying `key`. The first
name must be an item, and the second a door or object in the same room. Each
item/target pair may have at most one `use` handler.

## Actions

Actions are the statements inside handlers. They run top to bottom.

| Action                | Effect                                                    |
|-----------------------|-----------------------------------------------------------|
| `say "text"`          | Prints text to the player.                                |
| `set name, expr`      | Assigns a new value to a var or flag.                     |
| `give item`           | Adds the item to the inventory. No effect if already held. |
| `take item`           | Removes the item from the inventory. No effect if not held. |
| `lock door`           | Locks a door in the current room.                         |
| `unlock door`         | Unlocks a door in the current room.                       |
| `end "text"`          | Prints text and ends the game immediately.                |
| `if cond: ... else: ...` | Runs one of two blocks. `else` is optional.            |

### `say` and string interpolation

Strings in `say` and `end` can include the current value of a var or flag
inside braces:

```noct
say "Health: {health}. Power on: {power}"
```

Only a single var or flag name may appear inside braces, not a general
expression, and there may be no spaces around it. The name is checked
statically. Use `\{` and `\}` for literal braces.

Interpolation only works in `say` and `end`. Display names of items, rooms
and objects are fixed text, so they cannot contain `{...}`.

### `set`

```noct
set health, health - 20
set power, true
```

The target must be a declared var or flag, and the expression's type must
match: `NUMBER` for vars, `BOOL` for flags.

### `end`

```noct
if health <= 0:
    end "Everything goes dark."
```

Prints the text and ends the game. No further actions run, including the
rest of the current handler.

### `if` / `else`

```noct
if has(key) and not power:
    say "The key is useless without power."
else:
    say "Nothing to do here."
```

The condition must be a `BOOL` expression. For multiple branches, nest an
`if` inside `else`.

## Expressions

| Expression        | Operand types   | Result   |
|-------------------|-----------------|----------|
| `a + b`, `a - b`, `a * b`, `a / b` | `NUMBER`        | `NUMBER` |
| `-a`              | `NUMBER`        | `NUMBER` |
| `a < b`, `a <= b`, `a > b`, `a >= b` | `NUMBER`        | `BOOL`   |
| `a == b`, `a != b` | both `NUMBER` or both `BOOL` | `BOOL`   |
| `a and b`, `a or b` | `BOOL`          | `BOOL`   |
| `not a`           | `BOOL`          | `BOOL`   |
| `has(item)`       | an item name    | `BOOL`   |
| `name`            | a var or flag   | its type |

`and` and `or` short-circuit: the right operand is only evaluated when it can
change the result.

Precedence, from lowest to highest: `or`, `and`, `not`, comparisons, `+ -`,
`* /`, unary `-`. Parentheses override it.

## Playing a game

`noct play game.noct` checks the program and, if there are no errors, starts
an interactive session in the first room. The player types commands at the
`>` prompt:

| Command                  | Effect                                                |
|--------------------------|-------------------------------------------------------|
| `look`                   | Shows the room's name, objects and exits.            |
| `inspect <target>`       | Fires the `on inspect` handler for the target.        |
| `use <item> on <target>` | Fires the `on use` handler for the pair.              |
| `go <door>`              | Walks through a door (see [door rules](#door)).        |
| `inventory` (or `inv`)   | Lists carried items.                                  |
| `help`                   | Lists commands.                                       |
| `quit`                   | Leaves the game.                                      |

Doors, objects and items are referred to by their names from the source
(`bed`, `gate_key`), not their display names. `look` and `inventory` show
both, e.g. `Rusted Bed (bed)`, so the player always knows what to type.

When the player enters a room, its display name is printed as a header
(`== Laboratory ==`), followed by the output of its `on enter` handlers.

If no handler matches an `inspect` or `use` command, the game prints a
default response: inspecting a door says whether it is locked, inspecting
an object says there is nothing unusual about it, and using an item says
nothing happens. Using an item requires carrying it.

The game ends when an `end` action runs, when the player types `quit`, or at
end of input.

Because commands are read from standard input, a game can be played from a
file of commands, which is useful for testing:

```sh
java -jar noct.jar play examples/asylum.noct < walkthrough.txt
```

## Static checks

Before anything runs, the checker verifies the whole program and reports the
first error with its location, the offending source line, and often a hint:

```
error: cannot assign BOOL to var 'health'
 --> examples/errors/type-mismatch.noct:7:21
  |
7 |         set health, poisoned
  |                     ^^^^^^^^
  = note: 'health' is a var, so it holds a NUMBER
```

A program that passes these checks cannot fail at runtime due to a name or
type mistake. The [examples/errors](../examples/errors) directory has one
small program for many of the errors below.

**Names**

- Global names (items, flags, vars, rooms) are unique.
- Door and object names are unique within their room.
- Every door target is a declared room.
- Every `requires` names a declared item.
- `inspect` targets and `use` targets are doors or objects in the same room.
- `use`, `give`, `take` and `has(...)` name declared items.
- `lock` and `unlock` name a door in the same room.
- `set` targets, identifiers in expressions, and names inside `{...}` in
  strings are declared vars or flags.
- Display names of items, rooms and objects contain no `{...}`.
- A var initializer only refers to vars and flags declared above it.
- The program declares at least one room.

**Handlers**

- At most one `on inspect` handler per target.
- At most one `on use` handler per item/target pair.

**Types**

- Every operator receives operands of the types listed in
  [Expressions](#expressions).
- Var initializers are `NUMBER`.
- `set` assigns `NUMBER` to vars and `BOOL` to flags.
- `if` conditions are `BOOL`.

## Runtime errors

The only error that can occur during play is **division by zero**, because
it depends on values only known at runtime. It stops the game and reports
the location of the `/` operator.

## Exit codes

`noct` follows the BSD `sysexits` convention:

| Code | Meaning                                         |
|------|-------------------------------------------------|
| 0    | success                                         |
| 64   | wrong command-line usage                        |
| 65   | the program has a syntax or semantic error      |
| 66   | the input file cannot be read                   |
| 70   | runtime error (division by zero) during play    |
