package noct.parser.ast;

import noct.lexer.StringTemplate;
import noct.lexer.Token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialises the AST to JSON, for {@code noct ast --json}.
 *
 * <p>Uses a tiny built-in JSON writer so the project has no runtime
 * dependencies. Every node is an object with a {@code "kind"} field;
 * declarations and actions also carry their source {@code "line"}.
 */
public final class AstJson {

    private AstJson() {}

    public static String print(Ast.Program program) {
        List<Object> declarations = new ArrayList<>();
        for (Ast.Decl decl : program.declarations()) declarations.add(declaration(decl));

        Map<String, Object> root = node("program");
        root.put("declarations", declarations);

        StringBuilder sb = new StringBuilder();
        write(root, 0, sb);
        return sb.append('\n').toString();
    }

    // ---------------------------------------------------------------------
    // AST to maps and lists
    // ---------------------------------------------------------------------

    private static Map<String, Object> declaration(Ast.Decl decl) {
        Map<String, Object> n = switch (decl) {
            case Ast.ItemDecl d -> {
                Map<String, Object> m = node("item");
                m.put("name", d.name().lexeme());
                m.put("displayName", text(d.displayName()));
                yield m;
            }
            case Ast.FlagDecl d -> {
                Map<String, Object> m = node("flag");
                m.put("name", d.name().lexeme());
                m.put("value", d.initialValue());
                yield m;
            }
            case Ast.VarDecl d -> {
                Map<String, Object> m = node("var");
                m.put("name", d.name().lexeme());
                m.put("initializer", expression(d.initializer()));
                yield m;
            }
            case Ast.RoomDecl d -> {
                Map<String, Object> m = node("room");
                m.put("name", d.name().lexeme());
                m.put("displayName", text(d.displayName()));
                List<Object> members = new ArrayList<>();
                for (Ast.Member member : d.members()) members.add(member(member));
                m.put("members", members);
                yield m;
            }
        };
        n.put("line", decl.name().line());
        return n;
    }

    private static Map<String, Object> member(Ast.Member member) {
        return switch (member) {
            case Ast.DoorDecl d -> {
                Map<String, Object> m = node("door");
                m.put("name", d.name().lexeme());
                m.put("target", d.target().lexeme());
                m.put("locked", d.locked());
                m.put("requires", d.requiredItem() == null ? null : d.requiredItem().lexeme());
                m.put("line", d.name().line());
                yield m;
            }
            case Ast.ObjectDecl o -> {
                Map<String, Object> m = node("object");
                m.put("name", o.name().lexeme());
                m.put("displayName", text(o.displayName()));
                m.put("line", o.name().line());
                yield m;
            }
            case Ast.OnEnter h -> {
                Map<String, Object> m = node("onEnter");
                m.put("line", h.keyword().line());
                m.put("body", actions(h.body()));
                yield m;
            }
            case Ast.OnInspect h -> {
                Map<String, Object> m = node("onInspect");
                m.put("target", h.target().lexeme());
                m.put("line", h.target().line());
                m.put("body", actions(h.body()));
                yield m;
            }
            case Ast.OnUse h -> {
                Map<String, Object> m = node("onUse");
                m.put("item", h.item().lexeme());
                m.put("target", h.target().lexeme());
                m.put("line", h.item().line());
                m.put("body", actions(h.body()));
                yield m;
            }
        };
    }

    private static List<Object> actions(List<Ast.Action> actions) {
        List<Object> list = new ArrayList<>();
        for (Ast.Action action : actions) list.add(action(action));
        return list;
    }

    private static Map<String, Object> action(Ast.Action action) {
        return switch (action) {
            case Ast.Say a -> withLine(node("say"), a.keyword().line(), "text", a.template().source());
            case Ast.End a -> withLine(node("end"), a.keyword().line(), "text", a.template().source());
            case Ast.Give a -> withLine(node("give"), a.item().line(), "item", a.item().lexeme());
            case Ast.Take a -> withLine(node("take"), a.item().line(), "item", a.item().lexeme());
            case Ast.Lock a -> withLine(node("lock"), a.door().line(), "door", a.door().lexeme());
            case Ast.Unlock a -> withLine(node("unlock"), a.door().line(), "door", a.door().lexeme());
            case Ast.Set a -> {
                Map<String, Object> m = withLine(node("set"), a.target().line(), "target", a.target().lexeme());
                m.put("value", expression(a.value()));
                yield m;
            }
            case Ast.If a -> {
                Map<String, Object> m = node("if");
                m.put("line", a.keyword().line());
                m.put("condition", expression(a.condition()));
                m.put("then", actions(a.thenBranch()));
                m.put("else", actions(a.elseBranch()));
                yield m;
            }
        };
    }

    private static Map<String, Object> expression(Expr expr) {
        return switch (expr) {
            case Expr.Number e -> with(node("number"), "value", e.value());
            case Expr.Bool e -> with(node("bool"), "value", e.value());
            case Expr.Name e -> with(node("name"), "name", e.token().lexeme());
            case Expr.Has e -> with(node("has"), "item", e.item().lexeme());
            case Expr.Unary e -> {
                Map<String, Object> m = with(node("unary"), "operator", e.operator().lexeme());
                m.put("operand", expression(e.operand()));
                yield m;
            }
            case Expr.Binary e -> {
                Map<String, Object> m = with(node("binary"), "operator", e.operator().lexeme());
                m.put("left", expression(e.left()));
                m.put("right", expression(e.right()));
                yield m;
            }
        };
    }

    private static Map<String, Object> node(String kind) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        return m;
    }

    private static Map<String, Object> with(Map<String, Object> m, String key, Object value) {
        m.put(key, value);
        return m;
    }

    private static Map<String, Object> withLine(Map<String, Object> m, int line, String key, Object value) {
        m.put(key, value);
        m.put("line", line);
        return m;
    }

    private static String text(Token stringToken) {
        return ((StringTemplate) stringToken.value()).source();
    }

    // ---------------------------------------------------------------------
    // Minimal pretty-printing JSON writer
    // ---------------------------------------------------------------------

    private static void write(Object value, int depth, StringBuilder sb) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> writeString(s, sb);
            case Number n -> sb.append(n);
            case Boolean b -> sb.append(b);
            case Map<?, ?> map -> {
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append("{\n");
                int i = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    indent(depth + 1, sb);
                    writeString(entry.getKey().toString(), sb);
                    sb.append(": ");
                    write(entry.getValue(), depth + 1, sb);
                    sb.append(++i < map.size() ? ",\n" : "\n");
                }
                indent(depth, sb);
                sb.append('}');
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    indent(depth + 1, sb);
                    write(list.get(i), depth + 1, sb);
                    sb.append(i < list.size() - 1 ? ",\n" : "\n");
                }
                indent(depth, sb);
                sb.append(']');
            }
            default -> throw new IllegalArgumentException("cannot serialise " + value.getClass());
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static void indent(int depth, StringBuilder sb) {
        sb.append("  ".repeat(depth));
    }
}
