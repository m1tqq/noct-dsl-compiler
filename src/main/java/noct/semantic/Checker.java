package noct.semantic;

import noct.diagnostic.NoctError;
import noct.diagnostic.Span;
import noct.diagnostic.Suggestions;
import noct.lexer.StringTemplate;
import noct.lexer.Token;
import noct.parser.ast.Ast;
import noct.parser.ast.Expr;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static analysis: checks that every name refers to the right kind of thing
 * and that every expression is well typed, before the program runs.
 *
 * <p>It works in two passes:
 * <ol>
 *   <li><b>Declaration pass</b>: collects every global name and every room's
 *       doors and objects. Because of this pass, rooms and items can be
 *       referenced before they are declared.</li>
 *   <li><b>Checking pass</b>: walks every declaration in order, resolving
 *       names and inferring the type of every expression.</li>
 * </ol>
 *
 * <p>The checker stops at the first error. A program that passes it cannot
 * hit a name or type error at runtime; see {@code docs/language.md#static-checks}.
 */
public final class Checker {

    private final Symbols symbols = new Symbols();

    /** Vars and flags declared so far, used to reject forward references in var initializers. */
    private final Set<String> declaredSoFar = new HashSet<>();

    /** True while checking a var initializer, where only names declared above are visible. */
    private boolean inInitializer = false;

    /** The room whose members are being checked, or null at the top level. */
    private Symbols.Room room = null;

    private Checker() {}

    /** Checks the program and returns a summary, or throws the first {@link NoctError}. */
    public static Symbols check(Ast.Program program) {
        Checker checker = new Checker();
        checker.declarationPass(program);
        checker.checkingPass(program);
        return checker.symbols;
    }

    // ---------------------------------------------------------------------
    // Pass 1: declarations
    // ---------------------------------------------------------------------

    private void declarationPass(Ast.Program program) {
        for (Ast.Decl decl : program.declarations()) {
            Symbols.Kind kind = switch (decl) {
                case Ast.ItemDecl d -> Symbols.Kind.ITEM;
                case Ast.FlagDecl d -> Symbols.Kind.FLAG;
                case Ast.VarDecl d -> Symbols.Kind.VAR;
                case Ast.RoomDecl d -> Symbols.Kind.ROOM;
            };
            declare(symbols.globals, decl.name(), kind, "");

            if (decl instanceof Ast.RoomDecl roomDecl) declareRoomMembers(roomDecl);
        }

        if (symbols.rooms().isEmpty()) {
            throw NoctError.semantic(Span.NONE, "the program has no rooms")
                    .help("declare at least one room; the first room is where the game starts");
        }
    }

    private void declareRoomMembers(Ast.RoomDecl roomDecl) {
        Symbols.Room r = new Symbols.Room(roomDecl.name().lexeme());
        for (Ast.Member member : roomDecl.members()) {
            switch (member) {
                case Ast.DoorDecl d -> declare(r.members, d.name(), Symbols.Kind.DOOR, " in room '" + r.name + "'");
                case Ast.ObjectDecl o -> declare(r.members, o.name(), Symbols.Kind.OBJECT, " in room '" + r.name + "'");
                case Ast.Handler h -> { /* handlers are checked in pass 2 */ }
            }
        }
        symbols.rooms.put(r.name, r);
    }

    private static void declare(Map<String, Symbols.Symbol> scope, Token name, Symbols.Kind kind, String where) {
        Symbols.Symbol previous = scope.get(name.lexeme());
        if (previous != null) {
            throw NoctError.semantic(name.span(), "the name '" + name.lexeme() + "' is already used" + where)
                    .note("'" + name.lexeme() + "' was first declared as " + previous.kind().withArticle()
                            + " on line " + previous.token().line());
        }
        scope.put(name.lexeme(), new Symbols.Symbol(kind, name));
    }

    // ---------------------------------------------------------------------
    // Pass 2: checking
    // ---------------------------------------------------------------------

    private void checkingPass(Ast.Program program) {
        for (Ast.Decl decl : program.declarations()) {
            switch (decl) {
                case Ast.ItemDecl d -> requirePlainText(d.displayName());
                case Ast.FlagDecl d -> declaredSoFar.add(d.name().lexeme());
                case Ast.VarDecl d -> checkVar(d);
                case Ast.RoomDecl d -> checkRoom(d);
            }
        }
    }

    private void checkVar(Ast.VarDecl var) {
        inInitializer = true;
        Type type = typeOf(var.initializer());
        inInitializer = false;

        if (type != Type.NUMBER) {
            throw NoctError.semantic(var.initializer().span(),
                            "a var must start with a NUMBER value, found " + type)
                    .help("use 'flag " + var.name().lexeme() + " = true' for a true/false value");
        }
        declaredSoFar.add(var.name().lexeme());
    }

    private void checkRoom(Ast.RoomDecl roomDecl) {
        requirePlainText(roomDecl.displayName());
        room = symbols.rooms.get(roomDecl.name().lexeme());

        Map<String, Token> inspectHandlers = new HashMap<>();
        Map<String, Token> useHandlers = new HashMap<>();

        for (Ast.Member member : roomDecl.members()) {
            switch (member) {
                case Ast.DoorDecl d -> {
                    requireGlobal(d.target(), Symbols.Kind.ROOM);
                    if (d.requiredItem() != null) requireGlobal(d.requiredItem(), Symbols.Kind.ITEM);
                }
                case Ast.ObjectDecl o -> requirePlainText(o.displayName());
                case Ast.OnEnter h -> checkActions(h.body());
                case Ast.OnInspect h -> {
                    requireDoorOrObject(h.target(), "inspect");
                    requireUniqueHandler(inspectHandlers, h.target().lexeme(), h.target(),
                            "on inspect " + h.target().lexeme());
                    checkActions(h.body());
                }
                case Ast.OnUse h -> {
                    requireGlobal(h.item(), Symbols.Kind.ITEM);
                    requireDoorOrObject(h.target(), "use an item on");
                    requireUniqueHandler(useHandlers, h.item().lexeme() + " " + h.target().lexeme(), h.item(),
                            "on use " + h.item().lexeme() + " on " + h.target().lexeme());
                    checkActions(h.body());
                }
            }
        }
        room = null;
    }

    private void requireUniqueHandler(Map<String, Token> seen, String key, Token at, String description) {
        Token first = seen.putIfAbsent(key, at);
        if (first != null) {
            throw NoctError.semantic(at.span(), "room '" + room.name + "' already has an '" + description + "' handler")
                    .note("the first one is on line " + first.line());
        }
    }

    private void checkActions(List<Ast.Action> actions) {
        for (Ast.Action action : actions) checkAction(action);
    }

    private void checkAction(Ast.Action action) {
        switch (action) {
            case Ast.Say a -> checkInterpolation(a.template());
            case Ast.End a -> checkInterpolation(a.template());
            case Ast.Give a -> requireGlobal(a.item(), Symbols.Kind.ITEM);
            case Ast.Take a -> requireGlobal(a.item(), Symbols.Kind.ITEM);
            case Ast.Lock a -> requireDoor(a.door(), "lock");
            case Ast.Unlock a -> requireDoor(a.door(), "unlock");
            case Ast.Set a -> checkSet(a);
            case Ast.If a -> {
                Type condition = typeOf(a.condition());
                if (condition != Type.BOOL) {
                    throw NoctError.semantic(a.condition().span(), "an if condition must be BOOL, found " + condition);
                }
                checkActions(a.thenBranch());
                checkActions(a.elseBranch());
            }
        }
    }

    private void checkSet(Ast.Set set) {
        Symbols.Symbol target = requireVarOrFlag(set.target(), "cannot assign to '%s'");
        Type expected = target.kind() == Symbols.Kind.VAR ? Type.NUMBER : Type.BOOL;
        Type actual = typeOf(set.value());

        if (actual != expected) {
            throw NoctError.semantic(set.value().span(),
                            "cannot assign " + actual + " to " + target.kind() + " '" + set.target().lexeme() + "'")
                    .note("'" + set.target().lexeme() + "' is " + target.kind().withArticle()
                            + ", so it holds " + (expected == Type.NUMBER ? "a NUMBER" : "a BOOL"));
        }
    }

    private void checkInterpolation(StringTemplate template) {
        for (StringTemplate.Ref ref : template.refs()) {
            requireVarOrFlag(ref.name(), "cannot show '%s' in a string");
        }
    }

    /** Display names are shown verbatim, so they cannot contain {name} interpolation. */
    private static void requirePlainText(Token string) {
        StringTemplate template = (StringTemplate) string.value();
        if (!template.isPlain()) {
            Token first = template.refs().get(0).name();
            throw NoctError.semantic(first.span(), "display names cannot contain '{...}'")
                    .help("only 'say' and 'end' strings can show values; write \\{ for a literal brace");
        }
    }

    // ---------------------------------------------------------------------
    // Expressions: type inference
    // ---------------------------------------------------------------------

    private Type typeOf(Expr expr) {
        return switch (expr) {
            case Expr.Number e -> Type.NUMBER;
            case Expr.Bool e -> Type.BOOL;
            case Expr.Name e -> requireVarOrFlag(e.token(), null).kind() == Symbols.Kind.VAR
                    ? Type.NUMBER
                    : Type.BOOL;
            case Expr.Has e -> {
                requireGlobal(e.item(), Symbols.Kind.ITEM);
                yield Type.BOOL;
            }
            case Expr.Unary e -> switch (e.operator().type()) {
                case NOT -> operand(e.operand(), Type.BOOL, "not", Type.BOOL);
                case MINUS -> operand(e.operand(), Type.NUMBER, "-", Type.NUMBER);
                default -> throw new IllegalStateException("unknown unary operator " + e.operator());
            };
            case Expr.Binary e -> typeOfBinary(e);
        };
    }

    private Type typeOfBinary(Expr.Binary e) {
        String op = e.operator().lexeme();
        return switch (e.operator().type()) {
            case PLUS, MINUS, STAR, SLASH -> {
                operand(e.left(), Type.NUMBER, op, Type.NUMBER);
                yield operand(e.right(), Type.NUMBER, op, Type.NUMBER);
            }
            case LT, LE, GT, GE -> {
                operand(e.left(), Type.NUMBER, op, Type.BOOL);
                yield operand(e.right(), Type.NUMBER, op, Type.BOOL);
            }
            case AND, OR -> {
                operand(e.left(), Type.BOOL, op, Type.BOOL);
                yield operand(e.right(), Type.BOOL, op, Type.BOOL);
            }
            case EQ, NEQ -> {
                Type left = typeOf(e.left());
                Type right = typeOf(e.right());
                if (left != right) {
                    throw NoctError.semantic(e.span(), "cannot compare " + left + " with " + right + " using '" + op + "'")
                            .note("both sides of '" + op + "' must have the same type");
                }
                yield Type.BOOL;
            }
            default -> throw new IllegalStateException("unknown binary operator " + e.operator());
        };
    }

    /** Checks that {@code operand} has type {@code expected} and returns {@code result}. */
    private Type operand(Expr operand, Type expected, String operator, Type result) {
        Type actual = typeOf(operand);
        if (actual != expected) {
            throw NoctError.semantic(operand.span(),
                    "'" + operator + "' expects a " + expected + " operand, found " + actual);
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // Name resolution
    // ---------------------------------------------------------------------

    /**
     * Resolves a name that must be a var or flag (in expressions, set targets and string interpolation).
     *
     * @param purpose what the name is used for, as a format string such as "cannot assign to '%s'",
     *                or null for a plain expression
     */
    private Symbols.Symbol requireVarOrFlag(Token name, String purpose) {
        Symbols.Symbol symbol = symbols.globals.get(name.lexeme());

        if (symbol == null) {
            Symbols.Symbol member = room == null ? null : room.members.get(name.lexeme());
            if (member != null) {
                throw NoctError.semantic(name.span(),
                        "'" + name.lexeme() + "' is " + member.kind().withArticle() + ", not a var or flag");
            }
            throw withSuggestion(
                    NoctError.semantic(name.span(), "unknown var or flag '" + name.lexeme() + "'"),
                    name.lexeme(), symbols.namesOf(Symbols.Kind.VAR, Symbols.Kind.FLAG));
        }

        if (symbol.kind() != Symbols.Kind.VAR && symbol.kind() != Symbols.Kind.FLAG) {
            String subject = purpose == null
                    ? "'" + name.lexeme() + "' is "
                    : purpose.formatted(name.lexeme()) + ": it is ";
            NoctError error = NoctError.semantic(name.span(),
                    subject + symbol.kind().withArticle() + ", not a var or flag");
            if (symbol.kind() == Symbols.Kind.ITEM) {
                error.help("to check whether the player carries it, use has(" + name.lexeme() + ")");
            }
            throw error;
        }

        if (inInitializer && !declaredSoFar.contains(name.lexeme())) {
            throw NoctError.semantic(name.span(), "'" + name.lexeme() + "' is used before it is declared")
                    .note("'" + name.lexeme() + "' is declared on line " + symbol.token().line()
                            + "; a var can only use vars and flags declared above it");
        }
        return symbol;
    }

    /** Resolves a global name that must be of the given kind (an item or a room). */
    private void requireGlobal(Token name, Symbols.Kind kind) {
        Symbols.Symbol symbol = symbols.globals.get(name.lexeme());
        if (symbol == null) {
            throw withSuggestion(
                    NoctError.semantic(name.span(), "unknown " + kind + " '" + name.lexeme() + "'"),
                    name.lexeme(), symbols.namesOf(kind));
        }
        if (symbol.kind() != kind) {
            throw NoctError.semantic(name.span(),
                    "'" + name.lexeme() + "' is " + symbol.kind().withArticle() + ", not " + kind.withArticle());
        }
    }

    /** Resolves a name that must be a door or object in the current room. */
    private void requireDoorOrObject(Token name, String action) {
        if (room.members.containsKey(name.lexeme())) return;

        NoctError error = NoctError.semantic(name.span(),
                "room '" + room.name + "' has no door or object named '" + name.lexeme() + "'");
        Symbols.Symbol global = symbols.globals.get(name.lexeme());
        if (global != null) {
            error.note("'" + name.lexeme() + "' is " + global.kind().withArticle()
                    + "; you can only " + action + " doors and objects in the same room");
        }
        throw withSuggestion(error, name.lexeme(), room.members.keySet());
    }

    /** Resolves a name that must be a door in the current room (for lock and unlock). */
    private void requireDoor(Token name, String action) {
        Symbols.Symbol member = room.members.get(name.lexeme());
        if (member != null && member.kind() == Symbols.Kind.DOOR) return;

        if (member != null) {
            throw NoctError.semantic(name.span(), "cannot " + action + " '" + name.lexeme() + "': it is an object, not a door");
        }

        NoctError error = NoctError.semantic(name.span(),
                "room '" + room.name + "' has no door named '" + name.lexeme() + "'");
        for (Symbols.Room other : symbols.rooms.values()) {
            Symbols.Symbol door = other.members.get(name.lexeme());
            if (door != null && door.kind() == Symbols.Kind.DOOR) {
                error.note("there is a door '" + name.lexeme() + "' in room '" + other.name
                        + "', but handlers can only " + action + " doors in their own room");
                break;
            }
        }
        throw withSuggestion(error, name.lexeme(), room.doorNames());
    }

    private static NoctError withSuggestion(NoctError error, String name, Collection<String> candidates) {
        Optional<String> suggestion = Suggestions.closest(name, candidates);
        suggestion.ifPresent(s -> error.help("did you mean '" + s + "'?"));
        return error;
    }
}
