package noct;

import noct.diagnostic.NoctError;
import noct.parser.ast.Ast;
import noct.parser.ast.AstPrinter;
import org.junit.jupiter.api.Test;

import static noct.TestSupport.assertError;
import static noct.TestSupport.lines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {

    /** Parses {@code var x = <expr>} and returns the expression fully parenthesised. */
    private static String expr(String expression) {
        Ast.Program program = Noct.parse("var x = " + expression);
        Ast.VarDecl var = (Ast.VarDecl) program.declarations().get(0);
        return AstPrinter.expression(var.initializer());
    }

    @Test
    void arithmeticPrecedenceAndAssociativity() {
        assertEquals("(1 + (2 * 3))", expr("1 + 2 * 3"));
        assertEquals("((1 - 2) - 3)", expr("1 - 2 - 3"));
        assertEquals("((8 / 4) / 2)", expr("8 / 4 / 2"));
        assertEquals("((1 + 2) * 3)", expr("(1 + 2) * 3"));
        assertEquals("((-1) * (-(-2)))", expr("-1 * --2"));
    }

    @Test
    void logicalPrecedence() {
        assertEquals("(a or (b and c))", expr("a or b and c"));
        assertEquals("((not a) and b)", expr("not a and b"));
        assertEquals("(not (a > 1))", expr("not a > 1"));
        assertEquals("((a < 1) or (b >= 2))", expr("a < 1 or b >= 2"));
        assertEquals("(has(key) and (not has(lamp)))", expr("has(key) and not has(lamp)"));
    }

    @Test
    void parenthesisedConditionFollowedByComparison() {
        // Regression: the original parser treated "(" after "if" as part of the if syntax,
        // so "(a + 1) > 1" failed at '>'.
        assertEquals("((a + 1) > 1)", expr("(a + 1) > 1"));
        assertEquals("(health <= 0)", expr("(health <= 0)"));
    }

    @Test
    void chainedComparisonIsRejected() {
        NoctError error = assertError(() -> expr("0 < a < 10"));
        assertEquals("comparisons cannot be chained", error.getMessage());
    }

    @Test
    void doorOptionsInAnyOrder() {
        String source = lines(
                "room a \"A\":",
                "    door d1 -> a requires key locked",
                "    door d2 -> a locked requires key",
                "    door d3 -> a");
        Ast.RoomDecl room = (Ast.RoomDecl) Noct.parse(source).declarations().get(0);

        Ast.DoorDecl d1 = (Ast.DoorDecl) room.members().get(0);
        Ast.DoorDecl d2 = (Ast.DoorDecl) room.members().get(1);
        Ast.DoorDecl d3 = (Ast.DoorDecl) room.members().get(2);

        assertTrue(d1.locked());
        assertEquals("key", d1.requiredItem().lexeme());
        assertTrue(d2.locked());
        assertFalse(d3.locked());
        assertEquals(null, d3.requiredItem());
    }

    @Test
    void duplicateDoorOption() {
        NoctError error = assertError(() -> Noct.parse(lines("room a \"A\":", "    door d -> a locked locked")));
        assertEquals("'locked' is given twice for this door", error.getMessage());
    }

    @Test
    void ifElse() {
        String source = lines(
                "room a \"A\":",
                "    on enter:",
                "        if has(key):",
                "            say \"yes\"",
                "        else:",
                "            say \"no\"",
                "            end \"bye\"");
        Ast.RoomDecl room = (Ast.RoomDecl) Noct.parse(source).declarations().get(0);
        Ast.OnEnter handler = (Ast.OnEnter) room.members().get(0);
        Ast.If ifAction = (Ast.If) handler.body().get(0);

        assertEquals(1, ifAction.thenBranch().size());
        assertEquals(2, ifAction.elseBranch().size());
    }

    @Test
    void treePrinter() {
        String source = lines(
                "var hp = 10 * 2",
                "room a \"A\":",
                "    door out -> a locked",
                "    on enter:",
                "        if hp > 5:",
                "            say \"ok\"");
        String expected = lines(
                "program",
                "|-- var hp = (10 * 2)",
                "`-- room a \"A\"",
                "    |-- door out -> a (locked)",
                "    `-- on enter",
                "        `-- if (hp > 5)",
                "            `-- say \"ok\"");
        assertEquals(expected, AstPrinter.print(Noct.parse(source)));
    }

    @Test
    void keywordUsedAsName() {
        NoctError error = assertError(() -> Noct.parse(lines("room a \"A\":", "    on inspect door:", "        say \"x\"")));
        assertEquals("expected the name of a door or object to inspect, found keyword 'door'", error.getMessage());
    }

    @Test
    void helpfulMessagesForCommonMistakes() {
        assertEquals("expected ',' after the name in 'set'",
                assertError(() -> Noct.parse(lines("room a \"A\":", "    on enter:", "        set x = 1"))).getMessage());
        assertEquals("declarations are only allowed at the top level",
                assertError(() -> Noct.parse(lines("room a \"A\":", "    on enter:", "        flag f = true"))).getMessage());
        assertEquals("unexpected indentation",
                assertError(() -> Noct.parse(lines("room a \"A\":", "    on enter:", "        say \"a\"", "          say \"b\""))).getMessage());
        assertEquals("'else' without a matching 'if'",
                assertError(() -> Noct.parse(lines("room a \"A\":", "    on enter:", "        else:", "            say \"a\""))).getMessage());
    }

    @Test
    void missingBodyAtEndOfFile() {
        NoctError error = assertError(() -> Noct.parse("room a \"A\":"));
        assertEquals("expected an indented room body on the next line, found end of file", error.getMessage());
    }
}
