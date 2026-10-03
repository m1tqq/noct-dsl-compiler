package noct.semantic;

import noct.lexer.Token;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The symbol table built by the {@link Checker}.
 *
 * <p>There are two kinds of scope: a single global scope (items, flags, vars
 * and rooms share one namespace), and one scope per room for its doors and
 * objects.
 */
public final class Symbols {

    public enum Kind {
        ITEM, FLAG, VAR, ROOM, DOOR, OBJECT;

        /** "an item", "a flag", ... */
        public String withArticle() {
            return (this == ITEM || this == OBJECT ? "an " : "a ") + this;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** A declared name: what kind of thing it is and where it was declared. */
    public record Symbol(Kind kind, Token token) {}

    /** The doors and objects of one room. */
    public static final class Room {
        final String name;
        final Map<String, Symbol> members = new LinkedHashMap<>();

        Room(String name) {
            this.name = name;
        }

        List<String> doorNames() {
            return members.values().stream()
                    .filter(s -> s.kind() == Kind.DOOR)
                    .map(s -> s.token().lexeme())
                    .toList();
        }
    }

    final Map<String, Symbol> globals = new LinkedHashMap<>();
    final Map<String, Room> rooms = new LinkedHashMap<>();

    Symbols() {}

    /** Room names in declaration order. */
    public List<String> rooms() {
        return List.copyOf(rooms.keySet());
    }

    public int count(Kind kind) {
        if (kind == Kind.DOOR || kind == Kind.OBJECT) {
            return (int) rooms.values().stream()
                    .flatMap(r -> r.members.values().stream())
                    .filter(s -> s.kind() == kind)
                    .count();
        }
        return (int) globals.values().stream().filter(s -> s.kind() == kind).count();
    }

    Collection<String> namesOf(Kind... kinds) {
        List<Kind> wanted = Arrays.asList(kinds);
        return globals.values().stream()
                .filter(s -> wanted.contains(s.kind()))
                .map(s -> s.token().lexeme())
                .toList();
    }
}
