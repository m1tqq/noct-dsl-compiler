package noct.diagnostic;

import java.util.ArrayList;
import java.util.List;

/**
 * Every error Noct reports: lexical, syntactic, semantic or runtime.
 *
 * <p>An error carries its location and optional notes ("first declared
 * here", "did you mean ...?"). {@link Diagnostic} turns it into the
 * human-readable report with a source snippet.
 */
@SuppressWarnings("serial")
public final class NoctError extends RuntimeException {

    public enum Kind {
        SYNTAX("syntax error"),
        SEMANTIC("error"),
        RUNTIME("runtime error");

        private final String label;

        Kind(String label) { this.label = label; }

        public String label() { return label; }
    }

    private final Kind kind;
    private final Span span;
    private final List<String> notes = new ArrayList<>();

    private NoctError(Kind kind, Span span, String message) {
        super(message);
        this.kind = kind;
        this.span = span;
    }

    public static NoctError syntax(Span span, String message) {
        return new NoctError(Kind.SYNTAX, span, message);
    }

    public static NoctError semantic(Span span, String message) {
        return new NoctError(Kind.SEMANTIC, span, message);
    }

    public static NoctError runtime(Span span, String message) {
        return new NoctError(Kind.RUNTIME, span, message);
    }

    /** Adds a line rendered as {@code = note: ...}. */
    public NoctError note(String text) {
        notes.add("note: " + text);
        return this;
    }

    /** Adds a line rendered as {@code = help: ...}. */
    public NoctError help(String text) {
        notes.add("help: " + text);
        return this;
    }

    public Kind kind() { return kind; }

    public Span span() { return span; }

    public List<String> notes() { return List.copyOf(notes); }
}
