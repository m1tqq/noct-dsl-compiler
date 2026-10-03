package noct.lexer;

public enum TokenType {
    // Declarations
    ITEM, FLAG, VAR, ROOM,

    // Room members
    DOOR, OBJECT, ON,

    // Triggers
    ENTER, INSPECT, USE,

    // Door options
    LOCKED, REQUIRES,

    // Actions
    SAY, SET, GIVE, TAKE, LOCK, UNLOCK, END,

    // Control flow and conditions
    IF, ELSE, HAS, NOT, AND, OR,

    // Literals and names
    IDENTIFIER, NUMBER, STRING, BOOL,

    // Punctuation
    LPAREN, RPAREN, COMMA, COLON, ARROW, ASSIGN,

    // Operators
    PLUS, MINUS, STAR, SLASH,
    EQ, NEQ, LT, LE, GT, GE,

    // Layout: synthetic tokens produced from indentation
    INDENT, DEDENT,

    EOF;

    public boolean isKeyword() {
        return ordinal() <= OR.ordinal();
    }
}
