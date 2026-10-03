# Noct Grammar

This is the complete grammar of Noct, written in EBNF. The parser
([Parser.java](../src/main/java/noct/parser/Parser.java)) is a hand-written
recursive-descent implementation of exactly this grammar: one method per
rule, with the rule written as a comment above each method.

Notation: `{ x }` means zero or more, `[ x ]` means optional, `( a | b )` is a
choice, and `"text"` is a literal keyword or symbol. Upper-case names are
tokens produced by the lexer.

## Lexical grammar

```ebnf
IDENTIFIER  = letter , { letter | digit | "_" } ;        (* not a keyword *)
letter      = "a".."z" | "A".."Z" | "_" ;
digit       = "0".."9" ;

NUMBER      = digit , { digit } ;                        (* 0 .. 2147483647 *)
BOOL        = "true" | "false" ;
STRING      = '"' , { string_char } , '"' ;              (* single line *)
string_char = any character except '"', '\', '{', '}' and newline
            | escape
            | interpolation ;
escape      = '\"' | '\\' | '\n' | '\t' | '\{' | '\}' ;
interpolation = "{" , IDENTIFIER , "}" ;                 (* no spaces inside *)

COMMENT     = "#" , { any character except newline } ;   (* ignored *)
```

Keywords are reserved and case-sensitive:

```
item  flag  var  room  door  object  on  enter  inspect  use
locked  requires  say  set  give  take  lock  unlock  end
if  else  has  not  and  or  true  false
```

Symbols: `:` `,` `(` `)` `->` `=` `+` `-` `*` `/` `==` `!=` `<` `<=` `>` `>=`

### Indentation

Blocks are delimited by indentation, like in Python. The lexer turns changes
in leading whitespace into synthetic `INDENT` and `DEDENT` tokens:

- A line indented deeper than the previous non-blank line emits one `INDENT`.
- A line indented less emits one `DEDENT` for every level it closes. Its
  indentation must match an enclosing level exactly; otherwise it is an
  indentation error.
- Blank lines and comment-only lines are ignored.
- A tab counts as 4 spaces. Spaces are recommended.
- At end of file, all open blocks are closed.

Apart from indentation, line breaks are not significant. By convention each
statement goes on its own line.

## Syntactic grammar

### Program and declarations

```ebnf
program      = { declaration } , EOF ;

declaration  = item_decl | flag_decl | var_decl | room_decl ;

item_decl    = "item" , IDENTIFIER , STRING ;
flag_decl    = "flag" , IDENTIFIER , "=" , BOOL ;
var_decl     = "var"  , IDENTIFIER , "=" , expression ;
room_decl    = "room" , IDENTIFIER , STRING , ":" ,
               INDENT , room_stmt , { room_stmt } , DEDENT ;
```

### Room contents

```ebnf
room_stmt    = door_decl | object_decl | handler ;

door_decl    = "door" , IDENTIFIER , "->" , IDENTIFIER , { door_option } ;
door_option  = "locked" | "requires" , IDENTIFIER ;     (* each at most once *)

object_decl  = "object" , IDENTIFIER , STRING ;

handler      = "on" , trigger , ":" , block ;
trigger      = "enter"
             | "inspect" , IDENTIFIER
             | "use" , IDENTIFIER , "on" , IDENTIFIER ;
```

### Actions

```ebnf
block        = INDENT , action , { action } , DEDENT ;

action       = say_stmt | set_stmt | give_stmt | take_stmt
             | lock_stmt | unlock_stmt | end_stmt | if_stmt ;

say_stmt     = "say" , STRING ;
set_stmt     = "set" , IDENTIFIER , "," , expression ;
give_stmt    = "give" , IDENTIFIER ;
take_stmt    = "take" , IDENTIFIER ;
lock_stmt    = "lock" , IDENTIFIER ;
unlock_stmt  = "unlock" , IDENTIFIER ;
end_stmt     = "end" , STRING ;
if_stmt      = "if" , expression , ":" , block ,
               [ "else" , ":" , block ] ;
```

### Expressions

Rules are listed from lowest to highest precedence.

```ebnf
expression     = or_expr ;
or_expr        = and_expr , { "or" , and_expr } ;
and_expr       = not_expr , { "and" , not_expr } ;
not_expr       = "not" , not_expr | comparison ;
comparison     = additive , [ comp_op , additive ] ;     (* non-associative *)
comp_op        = "==" | "!=" | "<" | "<=" | ">" | ">=" ;
additive       = multiplicative , { ( "+" | "-" ) , multiplicative } ;
multiplicative = unary , { ( "*" | "/" ) , unary } ;
unary          = "-" , unary | primary ;
primary        = NUMBER
               | BOOL
               | IDENTIFIER
               | "has" , "(" , IDENTIFIER , ")"
               | "(" , expression , ")" ;
```

| Precedence  | Operators                        | Associativity |
|-------------|----------------------------------|---------------|
| 1 (lowest)  | `or`                             | left          |
| 2           | `and`                            | left          |
| 3           | `not`                            | prefix        |
| 4           | `==` `!=` `<` `<=` `>` `>=`      | none          |
| 5           | `+` `-`                          | left          |
| 6           | `*` `/`                          | left          |
| 7 (highest) | unary `-`                        | prefix        |

Because comparison is non-associative, `a < b < c` is a syntax error. Write
`a < b and b < c` instead.

Because `not` binds looser than comparison, `not health > 50` means
`not (health > 50)`, as in Python.

## Notes on the grammar

The grammar is LL(1): every choice can be made by looking at the next token
only. That is what makes a simple recursive-descent parser possible without
backtracking.

Both `if (health <= 0):` and `if health <= 0:` are valid. The parentheses are
an ordinary parenthesised expression, not part of the `if` syntax.
