package noct.parser.ast;

import noct.lexer.StringTemplate;
import noct.lexer.Token;

import java.util.List;

/**
 * The abstract syntax tree for declarations, room members and actions.
 * Expressions are in {@link Expr}.
 *
 * <p>Nodes are immutable records grouped by sealed interfaces, so every
 * {@code switch} over them is checked for exhaustiveness by the compiler.
 * Nodes keep their tokens so later phases can report errors at the exact
 * source location.
 */
public final class Ast {

    private Ast() {}

    public record Program(List<Decl> declarations) {
        public Program {
            declarations = List.copyOf(declarations);
        }
    }

    // ---------------------------------------------------------------------
    // Top-level declarations
    // ---------------------------------------------------------------------

    public sealed interface Decl permits ItemDecl, FlagDecl, VarDecl, RoomDecl {
        Token name();
    }

    /** {@code item key "Rusty Key"} */
    public record ItemDecl(Token name, Token displayName) implements Decl {}

    /** {@code flag power = false} */
    public record FlagDecl(Token name, Token value) implements Decl {
        public boolean initialValue() {
            return (Boolean) value.value();
        }
    }

    /** {@code var health = 100} */
    public record VarDecl(Token name, Expr initializer) implements Decl {}

    /** {@code room lab "Laboratory": ...} */
    public record RoomDecl(Token name, Token displayName, List<Member> members) implements Decl {
        public RoomDecl {
            members = List.copyOf(members);
        }
    }

    // ---------------------------------------------------------------------
    // Room members
    // ---------------------------------------------------------------------

    public sealed interface Member permits DoorDecl, ObjectDecl, Handler {}

    /** {@code door exit -> hall locked requires key}; {@code requiredItem} may be null. */
    public record DoorDecl(Token name, Token target, boolean locked, Token requiredItem) implements Member {}

    /** {@code object generator "Power Generator"} */
    public record ObjectDecl(Token name, Token displayName) implements Member {}

    /** An event handler: {@code on <trigger>: <body>}. */
    public sealed interface Handler extends Member permits OnEnter, OnInspect, OnUse {
        List<Action> body();
    }

    /** {@code on enter:} */
    public record OnEnter(Token keyword, List<Action> body) implements Handler {
        public OnEnter {
            body = List.copyOf(body);
        }
    }

    /** {@code on inspect generator:} */
    public record OnInspect(Token target, List<Action> body) implements Handler {
        public OnInspect {
            body = List.copyOf(body);
        }
    }

    /** {@code on use key on gate:} */
    public record OnUse(Token item, Token target, List<Action> body) implements Handler {
        public OnUse {
            body = List.copyOf(body);
        }
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    public sealed interface Action permits Say, Set, Give, Take, Lock, Unlock, End, If {}

    /** {@code say "text"} */
    public record Say(Token keyword, Token text) implements Action {
        public StringTemplate template() {
            return (StringTemplate) text.value();
        }
    }

    /** {@code set health, health - 10} */
    public record Set(Token target, Expr value) implements Action {}

    /** {@code give key} */
    public record Give(Token item) implements Action {}

    /** {@code take key} */
    public record Take(Token item) implements Action {}

    /** {@code lock exit} */
    public record Lock(Token door) implements Action {}

    /** {@code unlock exit} */
    public record Unlock(Token door) implements Action {}

    /** {@code end "You escaped."} */
    public record End(Token keyword, Token text) implements Action {
        public StringTemplate template() {
            return (StringTemplate) text.value();
        }
    }

    /** {@code if cond: ... else: ...}; {@code elseBranch} is empty when there is no {@code else}. */
    public record If(Token keyword, Expr condition, List<Action> thenBranch, List<Action> elseBranch)
            implements Action {
        public If {
            thenBranch = List.copyOf(thenBranch);
            elseBranch = List.copyOf(elseBranch);
        }

        public boolean hasElse() {
            return !elseBranch.isEmpty();
        }
    }
}
