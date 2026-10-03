package noct;

import noct.lexer.Lexer;
import noct.lexer.Token;
import noct.parser.Parser;
import noct.parser.ast.Ast;
import noct.preprocessor.Preprocessor;
import noct.semantic.Checker;
import noct.semantic.Symbols;

import java.util.List;

/**
 * Entry points to the front end of the language, one per pipeline stage.
 * Each stage runs all the stages before it.
 *
 * <pre>
 * source → preprocess → tokenize → parse → check → (interpreter)
 * </pre>
 *
 * All methods throw {@link noct.diagnostic.NoctError} on the first error.
 */
public final class Noct {

    public static final String VERSION = "1.0.0";

    private Noct() {}

    public static List<Token> tokenize(String source) {
        return Lexer.tokenize(Preprocessor.run(source));
    }

    public static Ast.Program parse(String source) {
        return Parser.parse(tokenize(source));
    }

    /** A parsed program that passed every static check. */
    public record Checked(Ast.Program program, Symbols symbols) {}

    public static Checked check(String source) {
        Ast.Program program = parse(source);
        return new Checked(program, Checker.check(program));
    }
}
