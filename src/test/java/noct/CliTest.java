package noct.cli;

import noct.TestSupport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {

    private record Result(int exitCode, String out, String err) {}

    private static Result run(String input, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Main.run(args,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8),
                true);
        return new Result(code,
                TestSupport.normalize(out.toString(StandardCharsets.UTF_8)),
                TestSupport.normalize(err.toString(StandardCharsets.UTF_8)));
    }

    @Test
    void checkReportsSummary() {
        Result r = run("", "check", "examples/asylum.noct");
        assertEquals(Main.OK, r.exitCode());
        assertEquals("examples/asylum.noct: OK (5 rooms, 2 items, 2 vars, 4 flags)\n", r.out());
    }

    @Test
    void checkReportsErrors() {
        Result r = run("", "check", "examples/errors/unknown-room.noct");
        assertEquals(Main.PROGRAM_ERROR, r.exitCode());
        assertTrue(r.err().startsWith("error: unknown room 'hal'\n --> examples/errors/unknown-room.noct:5:18\n"));
    }

    @Test
    void playEchoesPipedCommands() {
        Result r = run("inspect bed\ngo cell_door\n", "play", "examples/asylum.noct");
        assertEquals(Main.OK, r.exitCode());
        assertTrue(r.out().contains("\n> inspect bed\nWedged between the springs"));
        assertTrue(r.out().contains("\n> go cell_door\nYou unlock cell_door with the Bent Hairpin.\n"));
    }

    @Test
    void tokensAndAst() {
        assertTrue(run("", "tokens", "examples/asylum.noct").out().startsWith("LOCATION  TYPE        LEXEME\n"));
        assertTrue(run("", "ast", "examples/asylum.noct").out().startsWith("program\n"));
        assertTrue(run("", "ast", "examples/asylum.noct", "--json").out().startsWith("{\n  \"kind\": \"program\""));
    }

    @Test
    void usageErrors() {
        assertEquals(Main.USAGE, run("").exitCode());
        assertEquals(Main.USAGE, run("", "fly", "x.noct").exitCode());
        assertEquals(Main.USAGE, run("", "check").exitCode());
        assertEquals(Main.NO_INPUT, run("", "check", "missing.noct").exitCode());
        assertEquals(Main.OK, run("", "--version").exitCode());
    }
}
