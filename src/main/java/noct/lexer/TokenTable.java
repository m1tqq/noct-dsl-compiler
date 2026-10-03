package noct.lexer;

import java.util.List;

/** Formats a token stream as an aligned table, for {@code noct tokens}. */
public final class TokenTable {

    private TokenTable() {}

    public static String format(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-9s %-11s %s\n", "LOCATION", "TYPE", "LEXEME"));

        for (Token t : tokens) {
            String location = t.line() + ":" + t.column();
            String lexeme = switch (t.type()) {
                case INDENT, DEDENT, EOF -> "";
                default -> t.lexeme();
            };
            sb.append(String.format("%-9s %-11s %s", location, t.type(), lexeme).stripTrailing())
              .append('\n');
        }
        return sb.toString();
    }
}
