package noct.parser.ast;

import noct.lexer.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Prints the AST as an indented tree, for {@code noct ast}.
 *
 * <p>Expressions are printed fully parenthesised, which makes operator
 * precedence visible: {@code 10 + 5 * 2} is shown as {@code (10 + (5 * 2))}.
 */
public final class AstPrinter {

    /** A tree node: a label and its children. */
    private record Node(String label, List<Node> children) {
        Node(String label) {
            this(label, new ArrayList<>());
        }

        Node add(Node child) {
            children.add(child);
            return this;
        }
    }

    private AstPrinter() {}

    /** Line-drawing characters for the tree. */
    private record Style(String branch, String lastBranch, String pipe) {}

    private static final Style UNICODE = new Style("├── ", "└── ", "│   ");
    private static final Style ASCII = new Style("|-- ", "`-- ", "|   ");

    /** Prints the tree with ASCII line drawing, which displays correctly everywhere. */
    public static String print(Ast.Program program) {
        return print(program, false);
    }

    /** Prints the tree, with Unicode box-drawing lines if {@code unicode} is true. */
    public static String print(Ast.Program program, boolean unicode) {
        Node root = new Node("program");
        for (Ast.Decl decl : program.declarations()) root.add(declaration(decl));

        StringBuilder sb = new StringBuilder(root.label()).append('\n');
        render(root.children(), "", unicode ? UNICODE : ASCII, sb);
        return sb.toString();
    }

    private static void render(List<Node> nodes, String prefix, Style style, StringBuilder sb) {
        for (int i = 0; i < nodes.size(); i++) {
            boolean last = i == nodes.size() - 1;
            Node node = nodes.get(i);
            sb.append(prefix).append(last ? style.lastBranch() : style.branch()).append(node.label()).append('\n');
            render(node.children(), prefix + (last ? "    " : style.pipe()), style, sb);
        }
    }

    private static Node declaration(Ast.Decl decl) {
        return switch (decl) {
            case Ast.ItemDecl d -> new Node("item " + d.name().lexeme() + " " + d.displayName().lexeme());
            case Ast.FlagDecl d -> new Node("flag " + d.name().lexeme() + " = " + d.value().lexeme());
            case Ast.VarDecl d -> new Node("var " + d.name().lexeme() + " = " + expression(d.initializer()));
            case Ast.RoomDecl d -> {
                Node room = new Node("room " + d.name().lexeme() + " " + d.displayName().lexeme());
                for (Ast.Member member : d.members()) room.add(member(member));
                yield room;
            }
        };
    }

    private static Node member(Ast.Member member) {
        return switch (member) {
            case Ast.DoorDecl d -> new Node("door " + d.name().lexeme() + " -> " + d.target().lexeme() + doorOptions(d));
            case Ast.ObjectDecl o -> new Node("object " + o.name().lexeme() + " " + o.displayName().lexeme());
            case Ast.OnEnter h -> actions(new Node("on enter"), h.body());
            case Ast.OnInspect h -> actions(new Node("on inspect " + h.target().lexeme()), h.body());
            case Ast.OnUse h -> actions(new Node("on use " + h.item().lexeme() + " on " + h.target().lexeme()), h.body());
        };
    }

    private static String doorOptions(Ast.DoorDecl door) {
        List<String> options = new ArrayList<>();
        if (door.locked()) options.add("locked");
        if (door.requiredItem() != null) options.add("requires " + door.requiredItem().lexeme());
        return options.isEmpty() ? "" : " (" + String.join(", ", options) + ")";
    }

    private static Node actions(Node parent, List<Ast.Action> actions) {
        for (Ast.Action action : actions) parent.add(action(action));
        return parent;
    }

    private static Node action(Ast.Action action) {
        return switch (action) {
            case Ast.Say a -> new Node("say " + a.text().lexeme());
            case Ast.Set a -> new Node("set " + a.target().lexeme() + " = " + expression(a.value()));
            case Ast.Give a -> new Node("give " + a.item().lexeme());
            case Ast.Take a -> new Node("take " + a.item().lexeme());
            case Ast.Lock a -> new Node("lock " + a.door().lexeme());
            case Ast.Unlock a -> new Node("unlock " + a.door().lexeme());
            case Ast.End a -> new Node("end " + a.text().lexeme());
            case Ast.If a -> {
                Node node = new Node("if " + expression(a.condition()));
                if (a.hasElse()) {
                    node.add(actions(new Node("then"), a.thenBranch()));
                    node.add(actions(new Node("else"), a.elseBranch()));
                } else {
                    actions(node, a.thenBranch());
                }
                yield node;
            }
        };
    }

    /** Formats an expression on one line, fully parenthesised. */
    public static String expression(Expr expr) {
        return switch (expr) {
            case Expr.Number e -> Integer.toString(e.value());
            case Expr.Bool e -> Boolean.toString(e.value());
            case Expr.Name e -> e.token().lexeme();
            case Expr.Has e -> "has(" + e.item().lexeme() + ")";
            case Expr.Unary e -> "(" + operator(e.operator()) + expression(e.operand()) + ")";
            case Expr.Binary e -> "(" + expression(e.left()) + " " + e.operator().lexeme() + " " + expression(e.right()) + ")";
        };
    }

    private static String operator(Token op) {
        return op.lexeme().equals("not") ? "not " : op.lexeme();
    }
}
