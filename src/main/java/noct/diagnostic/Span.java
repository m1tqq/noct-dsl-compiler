package noct.diagnostic;

/**
 * A location in the source: a 1-based line and column, plus the number of
 * characters the location covers (used to draw the {@code ^^^} underline).
 */
public record Span(int line, int column, int length) {

    /** A span that has no meaningful location, e.g. "the program has no rooms". */
    public static final Span NONE = new Span(0, 0, 0);

    public Span {
        length = Math.max(1, length);
    }

    public boolean isKnown() {
        return line > 0;
    }

    /**
     * Joins two spans into one covering both, if they are on the same line.
     * Otherwise returns {@code first}, since a caret can only underline one line.
     */
    public Span to(Span last) {
        if (!isKnown() || !last.isKnown() || last.line != line || last.column < column) return this;
        return new Span(line, column, last.column + last.length - column);
    }
}
