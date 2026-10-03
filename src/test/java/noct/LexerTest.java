package noct;

import noct.diagnostic.NoctError;
import noct.lexer.StringTemplate;
import noct.lexer.Token;
import noct.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static noct.TestSupport.assertError;
import static noct.TestSupport.lines;
import static noct.lexer.TokenType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerTest {

    private static List<TokenType> types(String source) {
        return Noct.tokenize(source).stream().map(Token::type).toList();
    }

    @Test
    void keywordsIdentifiersAndLiterals() {
        assertEquals(List.of(VAR, IDENTIFIER, ASSIGN, NUMBER, PLUS, NUMBER, EOF), types("var health = 10 + 5"));
        assertEquals(List.of(FLAG, IDENTIFIER, ASSIGN, BOOL, EOF), types("flag power = true"));
    }

    @Test
    void keywordsAreCaseSensitive() {
        assertEquals(List.of(IDENTIFIER, IDENTIFIER, EOF), types("Room ROOM"));
    }

    @Test
    void multiCharacterOperators() {
        assertEquals(List.of(ARROW, EQ, NEQ, LE, GE, LT, GT, MINUS, ASSIGN, EOF), types("-> == != <= >= < > - ="));
    }

    @Test
    void indentationProducesIndentAndDedent() {
        String source = lines(
                "room a \"A\":",
                "    on enter:",
                "        say \"hi\"",
                "    object o \"O\"");

        assertEquals(List.of(
                ROOM, IDENTIFIER, STRING, COLON,
                INDENT, ON, ENTER, COLON,
                INDENT, SAY, STRING,
                DEDENT, OBJECT, IDENTIFIER, STRING,
                DEDENT, EOF), types(source));
    }

    @Test
    void blankAndCommentLinesDoNotAffectIndentation() {
        String source = lines(
                "room a \"A\":",
                "",
                "        # a comment at any indentation",
                "    on enter:  # trailing comment",
                "        say \"hi\"");

        assertEquals(List.of(
                ROOM, IDENTIFIER, STRING, COLON,
                INDENT, ON, ENTER, COLON,
                INDENT, SAY, STRING,
                DEDENT, DEDENT, EOF), types(source));
    }

    @Test
    void tabsCountAsFourSpaces() {
        String source = "room a \"A\":\n\ton enter:\n        say \"hi\"\n";
        assertEquals(List.of(
                ROOM, IDENTIFIER, STRING, COLON,
                INDENT, ON, ENTER, COLON,
                INDENT, SAY, STRING,
                DEDENT, DEDENT, EOF), types(source));
    }

    @Test
    void tokenPositionsAreOneBased() {
        Token health = Noct.tokenize("\nvar  health = 1").get(1);
        assertEquals("health", health.lexeme());
        assertEquals(2, health.line());
        assertEquals(6, health.column());
    }

    @Test
    void stringEscapes() {
        Token token = Noct.tokenize("\"a \\\"b\\\" \\\\ \\{c\\} \\n\\t\"").get(0);
        StringTemplate template = (StringTemplate) token.value();
        assertTrue(template.isPlain());
        assertEquals("a \"b\" \\ {c} \n\t", template.source());
    }

    @Test
    void stringInterpolation() {
        Token token = Noct.tokenize("\"Health: {health}, power: {power}!\"").get(0);
        StringTemplate template = (StringTemplate) token.value();

        assertEquals(List.of("health", "power"),
                template.refs().stream().map(r -> r.name().lexeme()).toList());
        assertEquals(11, template.refs().get(0).name().column());
        assertEquals("Health: {health}, power: {power}!", template.source());
    }

    @Test
    void unterminatedString() {
        NoctError error = assertError(() -> Noct.tokenize("say \"oops\n"));
        assertEquals("unterminated string", error.getMessage());
        assertEquals(5, error.span().column());
    }

    @Test
    void unknownEscape() {
        NoctError error = assertError(() -> Noct.tokenize("\"\\q\""));
        assertEquals("unknown escape sequence '\\q'", error.getMessage());
    }

    @Test
    void badInterpolation() {
        assertEquals("expected a var or flag name after '{'",
                assertError(() -> Noct.tokenize("\"{ health }\"")).getMessage());
        assertEquals("unmatched '}' in string",
                assertError(() -> Noct.tokenize("\"oops}\"")).getMessage());
        assertEquals("'end' is a keyword, not a var or flag",
                assertError(() -> Noct.tokenize("\"{end}\"")).getMessage());
    }

    @Test
    void numberTooLarge() {
        Noct.tokenize("2147483647");
        assertEquals("number is too large", assertError(() -> Noct.tokenize("2147483648")).getMessage());
    }

    @Test
    void unexpectedCharacters() {
        assertEquals("unexpected character '@'", assertError(() -> Noct.tokenize("var @")).getMessage());
        assertEquals("unexpected '!'", assertError(() -> Noct.tokenize("if !x")).getMessage());
    }

    @Test
    void inconsistentDedent() {
        String source = lines(
                "room a \"A\":",
                "    on enter:",
                "        say \"a\"",
                "      say \"b\"");
        NoctError error = assertError(() -> Noct.tokenize(source));
        assertEquals("indentation does not match any enclosing block", error.getMessage());
        assertEquals(4, error.span().line());
    }
}
