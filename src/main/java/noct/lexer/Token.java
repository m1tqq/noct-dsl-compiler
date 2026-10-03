package noct.lexer;

import noct.diagnostic.Span;

/**
 * A single token.
 *
 * @param type   what kind of token this is
 * @param lexeme the exact source text (empty for synthetic INDENT, DEDENT and EOF tokens)
 * @param value  the literal value: {@link Integer} for NUMBER, {@link Boolean} for BOOL,
 *               {@link StringTemplate} for STRING, otherwise {@code null}
 * @param line   1-based line
 * @param column 1-based column of the first character
 */
public record Token(TokenType type, String lexeme, Object value, int line, int column) {

    public Span span() {
        return new Span(line, column, lexeme.length());
    }

    /** A short human-readable description, used in "expected X, found Y" messages. */
    public String describe() {
        return switch (type) {
            case EOF -> "end of file";
            case INDENT -> "an indented block";
            case DEDENT -> "end of block";
            case IDENTIFIER -> "'" + lexeme + "'";
            case NUMBER -> "number " + lexeme;
            case BOOL -> "'" + lexeme + "'";
            case STRING -> "a string";
            default -> type.isKeyword() ? "keyword '" + lexeme + "'" : "'" + lexeme + "'";
        };
    }

    @Override
    public String toString() {
        return type + " '" + lexeme + "' at " + line + ":" + column;
    }
}
