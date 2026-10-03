package noct;

import noct.diagnostic.NoctError;
import noct.interpreter.Game;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static noct.TestSupport.assertError;
import static noct.TestSupport.lines;
import static noct.TestSupport.play;
import static noct.TestSupport.playGame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTest {

    private static final String KEY_AND_DOOR = lines(
            "item key \"Rusty Key\"",
            "room cell \"Cell\":",
            "    door exit -> hall locked requires key",
            "    door hatch -> hall locked",
            "    object bed \"Iron Bed\"",
            "    on enter:",
            "        say \"You wake up.\"",
            "    on inspect bed:",
            "        give key",
            "        say \"A key!\"",
            "room hall \"Hallway\":",
            "    door back -> cell",
            "    on enter:",
            "        say \"Cold air.\"");

    @Test
    void startsInTheFirstRoom() {
        assertEquals(lines("== Cell ==", "You wake up."), play(KEY_AND_DOOR));
    }

    @Test
    void lockedDoorWithoutKey() {
        TestSupport.Played played = playGame(KEY_AND_DOOR, "go exit");
        assertTrue(played.output().endsWith("exit is locked.\n"));
        assertEquals("cell", played.game().state().currentRoom());
    }

    @Test
    void doorUnlocksAutomaticallyWithRequiredItem() {
        TestSupport.Played played = playGame(KEY_AND_DOOR, "inspect bed", "go exit");
        assertTrue(played.output().contains("You unlock exit with the Rusty Key.\n\n== Hallway ==\nCold air.\n"));
        assertEquals("hall", played.game().state().currentRoom());
        assertFalse(played.game().state().doorLocked("cell", "exit"));
    }

    @Test
    void doorWithoutRequiresStaysLockedEvenWithItems() {
        TestSupport.Played played = playGame(KEY_AND_DOOR, "inspect bed", "go hatch");
        assertTrue(played.output().endsWith("hatch is locked.\n"));
    }

    @Test
    void enterRunsOnEveryVisit() {
        String output = play(KEY_AND_DOOR, "inspect bed", "go exit", "go back", "go exit");
        assertEquals(2, output.split("Cold air", -1).length - 1);
    }

    @Test
    void lookListsObjectsAndExits() {
        String output = play(KEY_AND_DOOR, "look");
        assertTrue(output.endsWith(lines(
                "== Cell ==",
                "You see: Iron Bed (bed)",
                "Exits: exit (locked), hatch (locked)")));
    }

    @Test
    void defaultResponses() {
        String output = play(KEY_AND_DOOR,
                "inspect hatch", "inspect mirror", "use key on bed", "go bed", "go window", "dance", "inventory");
        assertTrue(output.contains("hatch is locked.\n"));
        assertTrue(output.contains("There is no 'mirror' here.\n"));
        assertTrue(output.contains("You don't have 'key'.\n"));
        assertTrue(output.contains("You can't go through the Iron Bed.\n"));
        assertTrue(output.contains("There is no exit called 'window' here.\n"));
        assertTrue(output.contains("I don't understand 'dance'."));
        assertTrue(output.contains("You are carrying nothing.\n"));
    }

    @Test
    void inventoryAndUseWithoutHandler() {
        String output = play(KEY_AND_DOOR, "inspect bed", "inventory", "use key on bed");
        assertTrue(output.contains("You are carrying: Rusty Key (key)\n"));
        assertTrue(output.endsWith("Nothing happens.\n"));
    }

    @Test
    void useHandlerAndUnlock() {
        String source = lines(
                "item crowbar \"Crowbar\"",
                "room a \"A\":",
                "    door hatch -> b locked",
                "    on enter:",
                "        give crowbar",
                "    on use crowbar on hatch:",
                "        unlock hatch",
                "        take crowbar",
                "        say \"It pops open. The crowbar bends.\"",
                "room b \"B\":",
                "    on enter:",
                "        end \"Free.\"");
        TestSupport.Played played = playGame(source, "go hatch", "use crowbar on hatch", "inventory", "go hatch");
        assertTrue(played.output().contains("It pops open. The crowbar bends.\n"));
        assertTrue(played.output().contains("You are carrying nothing.\n"));
        assertTrue(played.output().endsWith("Free.\n\n" + Game.THE_END + "\n"));
        assertTrue(played.game().isOver());
    }

    @Test
    void endStopsTheHandlerImmediately() {
        String source = lines(
                "var hp = 10",
                "room a \"A\":",
                "    on enter:",
                "        end \"Dead.\"",
                "        set hp, 0",
                "    on enter:",
                "        say \"never printed\"");
        TestSupport.Played played = playGame(source, "look");
        assertEquals(lines("== A ==", "Dead.", "", Game.THE_END), played.output());
        assertEquals(10, played.game().state().vars().get("hp"));
    }

    @Test
    void setIfAndInterpolation() {
        String source = lines(
                "var hp = 100",
                "var damage = 15 * 2",
                "flag hurt = false",
                "room a \"A\":",
                "    on enter:",
                "        set hp, hp - damage",
                "        set hurt, hp < 100",
                "        if hurt and hp > 50:",
                "            say \"Health {hp}, hurt: {hurt}\"",
                "        else:",
                "            say \"wrong branch\"");
        assertTrue(play(source).endsWith("Health 70, hurt: true\n"));
    }

    @Test
    void integerArithmetic() {
        String source = lines(
                "var a = 7 / 2",
                "var b = -7 / 2",
                "var c = 2147483647 + 1",
                "room r \"R\":",
                "    on enter:",
                "        say \"{a} {b} {c}\"");
        assertTrue(play(source).endsWith("3 -3 -2147483648\n"));
    }

    @Test
    void logicalOperatorsShortCircuit() {
        String source = lines(
                "var zero = 0",
                "room r \"R\":",
                "    on enter:",
                "        if false and 1 / zero == 1:",
                "            say \"no\"",
                "        if true or 1 / zero == 1:",
                "            say \"short-circuit\"");
        assertTrue(play(source).endsWith("short-circuit\n"));
    }

    @Test
    void divisionByZeroIsARuntimeError() {
        String source = lines(
                "var zero = 0",
                "var n = 1",
                "room r \"R\":",
                "    object o \"O\"",
                "    on inspect o:",
                "        set n, n / zero");
        NoctError error = assertError(() -> play(source, "inspect o"));
        assertEquals(NoctError.Kind.RUNTIME, error.kind());
        assertEquals("division by zero", error.getMessage());
        assertEquals(6, error.span().line());
    }

    @Test
    void quitEndsTheGame() {
        TestSupport.Played played = playGame(KEY_AND_DOOR, "quit", "look");
        assertTrue(played.game().isOver());
        assertTrue(played.output().endsWith("Goodbye.\n"));
    }

    // ---- The example game, end to end ----------------------------------------

    private static String asylum() throws IOException {
        return Files.readString(Path.of("examples/asylum.noct"));
    }

    @Test
    void asylumGoodEnding() throws IOException {
        String output = play(asylum(),
                "inspect bed", "go cell_door", "go stairs", "inspect generator", "go up",
                "go to_office", "inspect desk", "go back", "use gate_key on gate", "go gate");
        assertTrue(output.endsWith("You run and don't look back.\n\n" + Game.THE_END + "\n"));
    }

    @Test
    void asylumBadEnding() throws IOException {
        String output = play(asylum(),
                "inspect bed", "go cell_door", "go to_office", "inspect desk", "go back",
                "go stairs", "inspect generator", "go up", "use gate_key on gate", "go gate");
        assertTrue(output.endsWith("something followed you out.\n\n" + Game.THE_END + "\n"));
    }

    @Test
    void asylumDeathInTheDark() throws IOException {
        TestSupport.Played played = playGame(asylum(),
                "inspect bed", "go cell_door", "go to_cell", "go cell_door", "go to_cell", "go cell_door");
        assertTrue(played.game().isOver());
        assertEquals(0, played.game().state().vars().get("sanity"));
    }

    @Test
    void asylumGateNeedsPower() throws IOException {
        String output = play(asylum(),
                "inspect bed", "go cell_door", "go to_office", "inspect desk", "go back", "use gate_key on gate");
        assertTrue(output.endsWith("No power.\n"));
    }
}
