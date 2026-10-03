package noct.lexer;

import java.util.List;

/**
 * The value of a string literal, split into plain text and {@code {name}}
 * references. Escape sequences are already resolved.
 *
 * <p>{@code "Health: {health}!"} becomes {@code [Text("Health: "), Ref(health), Text("!")]}.
 */
public record StringTemplate(List<Part> parts) {

    public sealed interface Part permits Text, Ref {}

    public record Text(String text) implements Part {}

    /** A reference to a var or flag; the token points at the name inside the braces. */
    public record Ref(Token name) implements Part {}

    public StringTemplate {
        parts = List.copyOf(parts);
    }

    public List<Ref> refs() {
        return parts.stream().filter(p -> p instanceof Ref).map(p -> (Ref) p).toList();
    }

    public boolean isPlain() {
        return refs().isEmpty();
    }

    /** The text with every reference written back as {@code {name}}. */
    public String source() {
        StringBuilder sb = new StringBuilder();
        for (Part part : parts) {
            switch (part) {
                case Text t -> sb.append(t.text());
                case Ref r -> sb.append('{').append(r.name().lexeme()).append('}');
            }
        }
        return sb.toString();
    }
}
