package noct.preprocessor;

/**
 * Normalises raw source text before lexing, so the lexer only has to deal
 * with one representation of whitespace:
 *
 * <ul>
 *   <li>removes a UTF-8 byte order mark, if present,</li>
 *   <li>converts Windows ({@code \r\n}) and old Mac ({@code \r}) line endings to {@code \n},</li>
 *   <li>expands leading tabs to 4 spaces each, so indentation is measured in spaces,</li>
 *   <li>strips trailing whitespace from every line,</li>
 *   <li>makes sure the text ends with a newline.</li>
 * </ul>
 *
 * <p>Line numbers are never changed, so error locations still match the file.
 */
public final class Preprocessor {

    public static final int TAB_WIDTH = 4;

    private Preprocessor() {}

    public static String run(String raw) {
        if (raw == null || raw.isEmpty()) return "\n";

        String text = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 16);

        for (int i = 0; i < lines.length; i++) {
            out.append(stripTrailing(expandLeadingTabs(lines[i])));
            if (i < lines.length - 1) out.append('\n');
        }

        if (out.isEmpty() || out.charAt(out.length() - 1) != '\n') out.append('\n');
        return out.toString();
    }

    private static String expandLeadingTabs(String line) {
        int i = 0;
        int width = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            width += line.charAt(i) == '\t' ? TAB_WIDTH : 1;
            i++;
        }
        if (i == 0) return line;
        return " ".repeat(width) + line.substring(i);
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) end--;
        return line.substring(0, end);
    }
}
