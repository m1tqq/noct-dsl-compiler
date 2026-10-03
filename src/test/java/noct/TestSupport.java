package noct;

import noct.diagnostic.NoctError;
import noct.interpreter.Game;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Shared helpers for tests. */
public final class TestSupport {

    private TestSupport() {}

    /** Joins lines with newlines, so test programs can be written one line per argument. */
    public static String lines(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /** Checks and plays a program with the given commands, returning everything the game printed. */
    public static String play(String source, String... commands) {
        Played played = playGame(source, commands);
        return played.output();
    }

    public record Played(Game game, String output) {}

    public static Played playGame(String source, String... commands) {
        Noct.Checked checked = Noct.check(source);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        Game game = new Game(checked.program(), out);
        game.start();
        for (String command : commands) game.execute(command);
        return new Played(game, normalize(buffer.toString(StandardCharsets.UTF_8)));
    }

    /** Converts Windows line endings (from println) to \n, so tests pass on every OS. */
    public static String normalize(String output) {
        return output.replace("\r\n", "\n");
    }

    /** Asserts that running {@code code} fails with a {@link NoctError} and returns it. */
    public static NoctError assertError(Executable code) {
        return assertThrows(NoctError.class, code);
    }
}
