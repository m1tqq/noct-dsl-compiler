package noct.interpreter;

import noct.diagnostic.NoctError;
import noct.parser.ast.Expr;

/**
 * Evaluates expressions against the current {@link GameState}.
 *
 * <p>The checker has already verified every type, so this class can cast
 * freely. The only error left for runtime is division by zero.
 */
final class Evaluator {

    private final GameState state;

    Evaluator(GameState state) {
        this.state = state;
    }

    int number(Expr expr) {
        return (Integer) evaluate(expr);
    }

    boolean bool(Expr expr) {
        return (Boolean) evaluate(expr);
    }

    Object evaluate(Expr expr) {
        return switch (expr) {
            case Expr.Number e -> e.value();
            case Expr.Bool e -> e.value();
            case Expr.Name e -> {
                String name = e.token().lexeme();
                yield state.vars.containsKey(name) ? state.vars.get(name) : state.flags.get(name);
            }
            case Expr.Has e -> state.inventory.contains(e.item().lexeme());
            case Expr.Unary e -> switch (e.operator().type()) {
                case NOT -> !bool(e.operand());
                case MINUS -> -number(e.operand());
                default -> throw new IllegalStateException("unknown unary operator " + e.operator());
            };
            case Expr.Binary e -> binary(e);
        };
    }

    private Object binary(Expr.Binary e) {
        return switch (e.operator().type()) {
            // 'and' and 'or' short-circuit: the right side runs only if it can change the result.
            case AND -> bool(e.left()) && bool(e.right());
            case OR -> bool(e.left()) || bool(e.right());

            case PLUS -> number(e.left()) + number(e.right());
            case MINUS -> number(e.left()) - number(e.right());
            case STAR -> number(e.left()) * number(e.right());
            case SLASH -> {
                int left = number(e.left());
                int right = number(e.right());
                if (right == 0) throw NoctError.runtime(e.operator().span(), "division by zero");
                yield left / right; // truncates toward zero
            }

            case LT -> number(e.left()) < number(e.right());
            case LE -> number(e.left()) <= number(e.right());
            case GT -> number(e.left()) > number(e.right());
            case GE -> number(e.left()) >= number(e.right());

            case EQ -> evaluate(e.left()).equals(evaluate(e.right()));
            case NEQ -> !evaluate(e.left()).equals(evaluate(e.right()));

            default -> throw new IllegalStateException("unknown binary operator " + e.operator());
        };
    }
}
