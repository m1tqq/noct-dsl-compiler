package noct;

import noct.diagnostic.NoctError;
import noct.semantic.Symbols;
import org.junit.jupiter.api.Test;

import static noct.TestSupport.assertError;
import static noct.TestSupport.lines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckerTest {

    /** A small valid world that test snippets are appended to. */
    private static final String WORLD = lines(
            "item key \"Key\"",
            "var health = 100",
            "flag power = false");

    /** Wraps actions in a room with a door and an object, and checks the program. */
    private static void checkActions(String... actions) {
        StringBuilder source = new StringBuilder(WORLD)
                .append("room lab \"Lab\":\n")
                .append("    door exit -> lab locked requires key\n")
                .append("    object box \"Box\"\n")
                .append("    on enter:\n");
        for (String action : actions) source.append("        ").append(action).append('\n');
        Noct.check(source.toString());
    }

    private static String errorFor(String... actions) {
        return assertError(() -> checkActions(actions)).getMessage();
    }

    @Test
    void validProgramPasses() {
        checkActions(
                "say \"Health: {health}, power: {power}\"",
                "set health, health - 10 * 2",
                "set power, not power and health > 0",
                "give key",
                "take key",
                "lock exit",
                "unlock exit",
                "if has(key) or health == 100:",
                "    end \"done\"");
    }

    @Test
    void summaryCounts() {
        Symbols symbols = Noct.check(WORLD + lines("room a \"A\":", "    door d -> a", "    object o \"O\"")).symbols();
        assertEquals(1, symbols.count(Symbols.Kind.ROOM));
        assertEquals(1, symbols.count(Symbols.Kind.ITEM));
        assertEquals(1, symbols.count(Symbols.Kind.VAR));
        assertEquals(1, symbols.count(Symbols.Kind.FLAG));
        assertEquals(1, symbols.count(Symbols.Kind.DOOR));
        assertEquals(1, symbols.count(Symbols.Kind.OBJECT));
    }

    // ---- Names --------------------------------------------------------------

    @Test
    void globalNamesShareOneNamespace() {
        NoctError error = assertError(() -> Noct.check(lines(
                "var power = 1",
                "flag power = true",
                "room a \"A\":",
                "    door d -> a")));
        assertEquals("the name 'power' is already used", error.getMessage());
        assertTrue(error.notes().get(0).contains("first declared as a var on line 1"));
    }

    @Test
    void doorsAndObjectsShareTheRoomNamespace() {
        NoctError error = assertError(() -> Noct.check(lines(
                "room a \"A\":",
                "    door thing -> a",
                "    object thing \"Thing\"")));
        assertEquals("the name 'thing' is already used in room 'a'", error.getMessage());
    }

    @Test
    void roomsCanBeReferencedBeforeTheyAreDeclared() {
        Noct.check(lines(
                "room a \"A\":",
                "    door forward -> b requires key",
                "room b \"B\":",
                "    door back -> a",
                "item key \"Key\""));
    }

    @Test
    void unknownDoorTargetSuggestsAName() {
        NoctError error = assertError(() -> Noct.check(lines(
                "room hall \"Hall\":",
                "    door d -> hal")));
        assertEquals("unknown room 'hal'", error.getMessage());
        assertEquals("help: did you mean 'hall'?", error.notes().get(0));
    }

    @Test
    void wrongKindOfName() {
        assertEquals("'lab' is a room, not an item", errorFor("give lab"));
        assertEquals("'key' is an item, not a var or flag", errorFor("if key:", "    say \"x\""));
        assertEquals("cannot assign to 'key': it is an item, not a var or flag", errorFor("set key, 1"));
        assertEquals("cannot show 'lab' in a string: it is a room, not a var or flag", errorFor("say \"{lab}\""));
        assertEquals("'box' is an object, not a var or flag", errorFor("set health, box"));
        assertEquals("cannot lock 'box': it is an object, not a door", errorFor("lock box"));
    }

    @Test
    void unknownNames() {
        assertEquals("unknown item 'lamp'", errorFor("give lamp"));
        assertEquals("unknown var or flag 'helth'", errorFor("set helth, 1"));
        assertEquals("room 'lab' has no door named 'gate'", errorFor("unlock gate"));
    }

    @Test
    void handlerTargetsMustBeInTheSameRoom() {
        NoctError error = assertError(() -> Noct.check(lines(
                "item key \"Key\"",
                "room a \"A\":",
                "    object chest \"Chest\"",
                "room b \"B\":",
                "    on inspect chest:",
                "        say \"x\"")));
        assertEquals("room 'b' has no door or object named 'chest'", error.getMessage());
    }

    @Test
    void lockOnlyAffectsDoorsInTheSameRoom() {
        NoctError error = assertError(() -> Noct.check(lines(
                "room a \"A\":",
                "    door gate -> b",
                "room b \"B\":",
                "    on enter:",
                "        lock gate")));
        assertEquals("room 'b' has no door named 'gate'", error.getMessage());
        assertTrue(error.notes().get(0).contains("there is a door 'gate' in room 'a'"));
    }

    @Test
    void duplicateHandlers() {
        String inspectTwice = lines(
                "room a \"A\":",
                "    object o \"O\"",
                "    on inspect o:",
                "        say \"1\"",
                "    on inspect o:",
                "        say \"2\"");
        assertEquals("room 'a' already has an 'on inspect o' handler",
                assertError(() -> Noct.check(inspectTwice)).getMessage());

        String enterTwice = lines(
                "room a \"A\":",
                "    on enter:",
                "        say \"1\"",
                "    on enter:",
                "        say \"2\"");
        Noct.check(enterTwice); // several 'on enter' handlers are allowed
    }

    @Test
    void programNeedsARoom() {
        assertEquals("the program has no rooms", assertError(() -> Noct.check("item key \"Key\"\n")).getMessage());
    }

    // ---- Var initializers ---------------------------------------------------

    @Test
    void varInitializerCannotUseLaterDeclarations() {
        // Regression: the original interpreter silently used 0 for 'b' here.
        NoctError error = assertError(() -> Noct.check(lines(
                "var a = b + 1",
                "var b = 5",
                "room r \"R\":",
                "    door d -> r")));
        assertEquals("'b' is used before it is declared", error.getMessage());
    }

    @Test
    void varInitializerCanUseEarlierDeclarations() {
        Noct.check(lines("var a = 5", "var b = a * 2", "room r \"R\":", "    door d -> r"));
    }

    @Test
    void varMustBeANumber() {
        NoctError error = assertError(() -> Noct.check(lines("var a = true", "room r \"R\":", "    door d -> r")));
        assertEquals("a var must start with a NUMBER value, found BOOL", error.getMessage());
    }

    // ---- Types --------------------------------------------------------------

    @Test
    void assignmentTypes() {
        assertEquals("cannot assign BOOL to var 'health'", errorFor("set health, power"));
        assertEquals("cannot assign NUMBER to flag 'power'", errorFor("set power, 1"));
    }

    @Test
    void operatorTypes() {
        assertEquals("'+' expects a NUMBER operand, found BOOL", errorFor("set health, health + power"));
        assertEquals("'-' expects a NUMBER operand, found BOOL", errorFor("set health, -power"));
        assertEquals("'not' expects a BOOL operand, found NUMBER", errorFor("set power, not health"));
        assertEquals("'and' expects a BOOL operand, found NUMBER", errorFor("set power, power and 1"));
        assertEquals("'<' expects a NUMBER operand, found BOOL", errorFor("set power, power < 1"));
        assertEquals("cannot compare NUMBER with BOOL using '=='", errorFor("set power, health == power"));
    }

    @Test
    void conditionMustBeBool() {
        assertEquals("an if condition must be BOOL, found NUMBER", errorFor("if health - 1:", "    say \"x\""));
    }

    @Test
    void equalityWorksOnBothTypes() {
        checkActions("set power, health == 1", "set power, power != true");
    }

    @Test
    void displayNamesCannotInterpolate() {
        NoctError error = assertError(() -> Noct.check(lines("var h = 1", "room r \"Room {h}\":", "    door d -> r")));
        assertEquals("display names cannot contain '{...}'", error.getMessage());
    }

    @Test
    void errorSpansCoverWholeExpressions() {
        NoctError error = assertError(() -> checkActions("if health + 1:", "    say \"x\""));
        assertEquals(12, error.span().column());
        assertEquals("health + 1".length(), error.span().length());
    }
}
