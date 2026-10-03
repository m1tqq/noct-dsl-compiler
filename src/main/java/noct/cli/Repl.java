package noct.cli;

import noct.interpreter.Game;
import noct.parser.ast.Ast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/** The interactive prompt for {@code noct play}. */
final class Repl {

    private Repl() {}

    /**
     * Plays the game, reading one command per line until the game ends or
     * input runs out.
     *
     * @param echo print each command after the prompt; used when input comes
     *             from a file or pipe, so the transcript reads like a real session
     */
    static void play(Ast.Program program, BufferedReader in, PrintStream out, boolean echo) throws IOException {
        Game game = new Game(program, out);
        game.start();

        while (!game.isOver()) {
            out.println();
            String line = prompt(in, out, echo);
            if (line == null) {
                out.println();
                return;
            }
            game.execute(line);
        }
    }

    /** Shows the prompt until a non-blank line is entered; returns null at end of input. */
    private static String prompt(BufferedReader in, PrintStream out, boolean echo) throws IOException {
        while (true) {
            out.print("> ");
            out.flush();
            String line = in.readLine();
            if (line == null) return null;
            if (echo) out.println(line);
            if (!line.isBlank()) return line;
        }
    }
}
