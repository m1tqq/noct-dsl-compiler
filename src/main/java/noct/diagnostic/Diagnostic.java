package noct.diagnostic;

/**
 * Renders a {@link NoctError} in the style of the Rust compiler:
 *
 * <pre>
 * error: unknown room 'hal'
 *  --> broken.noct:5:18
 *   |
 * 5 |     door exit -> hal locked requires key
 *   |                  ^^^
 *   = help: did you mean 'hall'?
 * </pre>
 */
public final class Diagnostic {

    private Diagnostic() {}

    public static String render(NoctError error, String fileName, String source) {
        StringBuilder out = new StringBuilder();
        out.append(error.kind().label()).append(": ").append(error.getMessage()).append('\n');

        Span span = error.span();
        String sourceLine = span.isKnown() ? lineAt(source, span.line()) : null;

        String gutter = " ".repeat(span.isKnown() ? Integer.toString(span.line()).length() : 1);
        out.append(gutter).append("--> ").append(fileName);
        if (span.isKnown()) out.append(':').append(span.line()).append(':').append(span.column());
        out.append('\n');

        if (sourceLine != null) {
            // Tabs are shown as single spaces so the caret lines up with the column number.
            String shown = sourceLine.replace('\t', ' ');
            int caretStart = Math.min(span.column() - 1, shown.length());
            int caretLength = Math.max(1, Math.min(span.length(), shown.length() - caretStart));

            out.append(gutter).append(" |\n");
            out.append(span.line()).append(" | ").append(shown).append('\n');
            out.append(gutter).append(" | ")
               .append(" ".repeat(caretStart))
               .append("^".repeat(caretLength))
               .append('\n');
        }

        for (String note : error.notes()) {
            out.append(gutter).append(" = ").append(note).append('\n');
        }
        return out.toString();
    }

    private static String lineAt(String source, int line) {
        String[] lines = source.split("\n", -1);
        return (line >= 1 && line <= lines.length) ? lines[line - 1] : null;
    }
}
