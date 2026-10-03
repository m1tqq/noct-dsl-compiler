package noct.lexer;

import noct.diagnostic.NoctError;
import noct.diagnostic.Span;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static noct.lexer.TokenType.*;

/**
 * Turns preprocessed source text into a list of tokens.
 *
 * <p>The lexer works one line at a time. At the start of each non-blank line
 * it compares the line's indentation with a stack of open indentation
 * levels and emits synthetic {@code INDENT} / {@code DEDENT} tokens, the same
 * way CPython's tokenizer does. That way the parser can treat indented
 * blocks exactly like blocks delimited by brackets.
 *
 * <p>Strings must fit on one line, so a line can always be tokenized on its own.
 */
public final class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("item", ITEM),
            Map.entry("flag", FLAG),
            Map.entry("var", VAR),
            Map.entry("room", ROOM),
            Map.entry("door", DOOR),
            Map.entry("object", OBJECT),
            Map.entry("on", ON),
            Map.entry("enter", ENTER),
            Map.entry("inspect", INSPECT),
            Map.entry("use", USE),
            Map.entry("locked", LOCKED),
            Map.entry("requires", REQUIRES),
            Map.entry("say", SAY),
            Map.entry("set", SET),
            Map.entry("give", GIVE),
            Map.entry("take", TAKE),
            Map.entry("lock", LOCK),
            Map.entry("unlock", UNLOCK),
            Map.entry("end", END),
            Map.entry("if", IF),
            Map.entry("else", ELSE),
            Map.entry("has", HAS),
            Map.entry("not", NOT),
            Map.entry("and", AND),
            Map.entry("or", OR),
            Map.entry("true", BOOL),
            Map.entry("false", BOOL)
    );

    private final String[] lines;
    private final List<Token> tokens = new ArrayList<>();

    /** Indentation levels of the currently open blocks, innermost on top. Always contains 0. */
    private final Deque<Integer> indents = new ArrayDeque<>();

    // Cursor within the current line.
    private String line;
    private int lineNumber;
    private int pos;
    private int start;

    private Lexer(String source) {
        this.lines = source.split("\n", -1);
        this.indents.push(0);
    }

    /** Tokenizes preprocessed source (see {@link noct.preprocessor.Preprocessor}). */
    public static List<Token> tokenize(String source) {
        return new Lexer(source).run();
    }

    public static boolean isKeyword(String word) {
        return KEYWORDS.containsKey(word);
    }

    private List<Token> run() {
        for (int i = 0; i < lines.length; i++) {
            line = lines[i];
            lineNumber = i + 1;

            int indent = leadingSpaces(line);
            if (indent == line.length() || line.charAt(indent) == '#') continue; // blank or comment-only

            handleIndentation(indent);
            pos = indent;
            scanLine();
        }

        // Close all open blocks, then end the stream. These synthetic tokens are
        // placed right after the last real token, which is where an error such as
        // "expected ':'" at the end of the file should point.
        int eofLine = 1;
        int eofColumn = 1;
        if (!tokens.isEmpty()) {
            Token last = tokens.get(tokens.size() - 1);
            eofLine = last.line();
            eofColumn = last.column() + last.lexeme().length();
        }
        while (indents.peek() > 0) {
            indents.pop();
            tokens.add(new Token(DEDENT, "", null, eofLine, eofColumn));
        }
        tokens.add(new Token(EOF, "", null, eofLine, eofColumn));
        return tokens;
    }

    // ---------------------------------------------------------------------
    // Indentation
    // ---------------------------------------------------------------------

    private void handleIndentation(int indent) {
        int current = indents.peek();

        if (indent > current) {
            indents.push(indent);
            tokens.add(new Token(INDENT, "", null, lineNumber, indent + 1));
            return;
        }

        if (indent < current) {
            String validLevels = describeLevels();
            while (indent < indents.peek()) {
                indents.pop();
                tokens.add(new Token(DEDENT, "", null, lineNumber, indent + 1));
            }
            if (indent != indents.peek()) {
                throw NoctError.syntax(new Span(lineNumber, indent + 1, 1),
                                "indentation does not match any enclosing block")
                        .help("indent this line by " + validLevels + " spaces");
            }
        }
    }

    /** The open indentation levels, outermost first, e.g. "0, 4 or 8". */
    private String describeLevels() {
        List<String> levels = new ArrayList<>(indents.stream().map(String::valueOf).toList());
        Collections.reverse(levels);
        if (levels.size() == 1) return levels.get(0);
        return levels.subList(0, levels.size() - 1).stream().collect(Collectors.joining(", "))
                + " or " + levels.get(levels.size() - 1);
    }

    private static int leadingSpaces(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') n++;
        return n;
    }

    // ---------------------------------------------------------------------
    // Tokens within a line
    // ---------------------------------------------------------------------

    private void scanLine() {
        while (pos < line.length()) {
            start = pos;
            char c = line.charAt(pos++);

            switch (c) {
                case ' ', '\t' -> { /* whitespace between tokens */ }
                case '#' -> pos = line.length(); // comment runs to the end of the line
                case '(' -> add(LPAREN);
                case ')' -> add(RPAREN);
                case ',' -> add(COMMA);
                case ':' -> add(COLON);
                case '+' -> add(PLUS);
                case '*' -> add(STAR);
                case '/' -> add(SLASH);
                case '-' -> add(match('>') ? ARROW : MINUS);
                case '=' -> add(match('=') ? EQ : ASSIGN);
                case '<' -> add(match('=') ? LE : LT);
                case '>' -> add(match('=') ? GE : GT);
                case '!' -> {
                    if (match('=')) {
                        add(NEQ);
                    } else {
                        throw NoctError.syntax(here(), "unexpected '!'")
                                .help("use '!=' for \"not equal\", or 'not' to negate a condition");
                    }
                }
                case '"' -> string();
                default -> {
                    if (isDigit(c)) number();
                    else if (isIdentifierStart(c)) identifier();
                    else throw NoctError.syntax(here(), "unexpected character '" + c + "'");
                }
            }
        }
    }

    private void identifier() {
        while (pos < line.length() && isIdentifierPart(line.charAt(pos))) pos++;
        String text = line.substring(start, pos);

        TokenType type = KEYWORDS.getOrDefault(text, IDENTIFIER);
        Object value = (type == BOOL) ? Boolean.valueOf(text) : null;
        tokens.add(new Token(type, text, value, lineNumber, start + 1));
    }

    private void number() {
        while (pos < line.length() && isDigit(line.charAt(pos))) pos++;
        String text = line.substring(start, pos);

        // Up to 10 digits can be checked with a long without overflowing.
        if (text.length() > 10 || Long.parseLong(text) > Integer.MAX_VALUE) {
            throw NoctError.syntax(here(), "number is too large")
                    .note("numbers are 32-bit integers; the largest is " + Integer.MAX_VALUE);
        }
        tokens.add(new Token(NUMBER, text, Integer.parseInt(text), lineNumber, start + 1));
    }

    /**
     * Scans a string literal, resolving escapes and splitting out
     * {@code {name}} interpolations. The opening quote is already consumed.
     */
    private void string() {
        List<StringTemplate.Part> parts = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        while (true) {
            if (pos >= line.length()) {
                throw NoctError.syntax(new Span(lineNumber, start + 1, line.length() - start),
                                "unterminated string")
                        .help("close the string with '\"' on the same line");
            }

            char c = line.charAt(pos);

            if (c == '"') {
                pos++;
                break;
            }

            if (c == '\\') {
                text.append(escape());
                continue;
            }

            if (c == '{') {
                if (!text.isEmpty()) {
                    parts.add(new StringTemplate.Text(text.toString()));
                    text.setLength(0);
                }
                parts.add(new StringTemplate.Ref(interpolatedName()));
                continue;
            }

            if (c == '}') {
                throw NoctError.syntax(new Span(lineNumber, pos + 1, 1), "unmatched '}' in string")
                        .help("write \\} for a literal brace");
            }

            text.append(c);
            pos++;
        }

        if (!text.isEmpty()) parts.add(new StringTemplate.Text(text.toString()));
        tokens.add(new Token(STRING, line.substring(start, pos), new StringTemplate(parts), lineNumber, start + 1));
    }

    private char escape() {
        if (pos + 1 >= line.length()) {
            throw NoctError.syntax(new Span(lineNumber, start + 1, line.length() - start), "unterminated string");
        }
        char e = line.charAt(pos + 1);
        char result = switch (e) {
            case '"', '\\', '{', '}' -> e;
            case 'n' -> '\n';
            case 't' -> '\t';
            default -> throw NoctError.syntax(new Span(lineNumber, pos + 1, 2), "unknown escape sequence '\\" + e + "'")
                    .help("supported escapes are \\\" \\\\ \\n \\t \\{ \\}");
        };
        pos += 2;
        return result;
    }

    /** Scans {@code {name}} inside a string, with {@code pos} on the opening brace. */
    private Token interpolatedName() {
        int braceColumn = pos + 1;
        int nameStart = pos + 1;
        int p = nameStart;
        while (p < line.length() && isIdentifierPart(line.charAt(p))) p++;

        boolean valid = p > nameStart
                && isIdentifierStart(line.charAt(nameStart))
                && p < line.length()
                && line.charAt(p) == '}';
        if (!valid) {
            throw NoctError.syntax(new Span(lineNumber, braceColumn, 1), "expected a var or flag name after '{'")
                    .help("write \\{ for a literal brace");
        }

        String name = line.substring(nameStart, p);
        if (isKeyword(name)) {
            throw NoctError.syntax(new Span(lineNumber, nameStart + 1, name.length()),
                    "'" + name + "' is a keyword, not a var or flag");
        }

        pos = p + 1;
        return new Token(IDENTIFIER, name, null, lineNumber, nameStart + 1);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private boolean match(char expected) {
        if (pos < line.length() && line.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void add(TokenType type) {
        tokens.add(new Token(type, line.substring(start, pos), null, lineNumber, start + 1));
    }

    private Span here() {
        return new Span(lineNumber, start + 1, pos - start);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }
}
