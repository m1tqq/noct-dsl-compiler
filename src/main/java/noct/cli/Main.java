package noct.cli;

import noct.Noct;
import noct.diagnostic.Diagnostic;
import noct.diagnostic.NoctError;
import noct.lexer.TokenTable;
import noct.parser.ast.Ast;
import noct.parser.ast.AstJson;
import noct.parser.ast.AstPrinter;
import noct.preprocessor.Preprocessor;
import noct.semantic.Symbols;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** The {@code noct} command-line tool. */
public final class Main {

    // Exit codes follow the BSD sysexits.h convention.
    static final int OK = 0;
    static final int USAGE = 64;
    static final int PROGRAM_ERROR = 65;
    static final int NO_INPUT = 66;
    static final int RUNTIME_ERROR = 70;

    private static final String USAGE_TEXT = """
            Noct %s - a language for survival-horror text adventures

            Usage: noct <command> <file>

            Commands:
              play <file>           check the program, then play it
              check <file>          report errors without playing
              tokens <file>         print the token stream
              ast <file> [--json]   print the syntax tree

            Options:
              -h, --help            show this help
              -v, --version         show the version
            """.formatted(Noct.VERSION);

    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err, !isTerminal()));
    }

    static int run(String[] args, InputStream in, PrintStream out, PrintStream err, boolean echoInput) {
        if (args.length == 0) {
            err.print(USAGE_TEXT);
            return USAGE;
        }

        String command = args[0];
        switch (command) {
            case "-h", "--help", "help" -> {
                out.print(USAGE_TEXT);
                return OK;
            }
            case "-v", "--version" -> {
                out.println("noct " + Noct.VERSION);
                return OK;
            }
            case "play", "check", "tokens", "ast" -> { }
            default -> {
                err.println("noct: unknown command '" + command + "'");
                err.print(USAGE_TEXT);
                return USAGE;
            }
        }

        boolean json = command.equals("ast") && args.length == 3 && args[2].equals("--json");
        if (args.length != 2 && !json) {
            err.println("noct: usage: noct " + command + " <file>" + (command.equals("ast") ? " [--json]" : ""));
            return USAGE;
        }

        String file = args[1];
        String source;
        try {
            source = Files.readString(Path.of(file));
        } catch (NoSuchFileException e) {
            err.println("noct: cannot read '" + file + "': no such file");
            return NO_INPUT;
        } catch (CharacterCodingException e) {
            err.println("noct: cannot read '" + file + "': the file is not valid UTF-8 text");
            return NO_INPUT;
        } catch (IOException e) {
            err.println("noct: cannot read '" + file + "': " + e.getMessage());
            return NO_INPUT;
        }

        try {
            switch (command) {
                case "tokens" -> out.print(TokenTable.format(Noct.tokenize(source)));
                case "ast" -> {
                    Ast.Program program = Noct.parse(source);
                    out.print(json ? AstJson.print(program) : AstPrinter.print(program, supportsUnicode()));
                }
                case "check" -> out.println(file + ": OK (" + summary(Noct.check(source).symbols()) + ")");
                case "play" -> {
                    Noct.Checked checked = Noct.check(source);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    Repl.play(checked.program(), reader, out, echoInput);
                }
                default -> throw new IllegalStateException(command);
            }
            return OK;
        } catch (NoctError e) {
            out.flush();
            err.print(Diagnostic.render(e, file, Preprocessor.run(source)));
            return e.kind() == NoctError.Kind.RUNTIME ? RUNTIME_ERROR : PROGRAM_ERROR;
        } catch (IOException e) {
            err.println("noct: error reading input: " + e.getMessage());
            return NO_INPUT;
        }
    }

    private static String summary(Symbols symbols) {
        return plural(symbols.count(Symbols.Kind.ROOM), "room") + ", "
                + plural(symbols.count(Symbols.Kind.ITEM), "item") + ", "
                + plural(symbols.count(Symbols.Kind.VAR), "var") + ", "
                + plural(symbols.count(Symbols.Kind.FLAG), "flag");
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }

    /** Whether standard output can display Unicode box-drawing characters. */
    private static boolean supportsUnicode() {
        String encoding = System.getProperty("stdout.encoding", System.getProperty("native.encoding", ""));
        return encoding.equalsIgnoreCase("UTF-8");
    }

    /**
     * Whether we are talking to a person at a terminal. When input is piped
     * from a file, commands are echoed so the output reads like a session.
     */
    private static boolean isTerminal() {
        Console console = System.console();
        if (console == null) return false;
        try {
            // Java 22+ returns a Console even when redirected; isTerminal() tells the difference.
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (ReflectiveOperationException e) {
            return true; // Java 21: a non-null console means a terminal
        }
    }
}
