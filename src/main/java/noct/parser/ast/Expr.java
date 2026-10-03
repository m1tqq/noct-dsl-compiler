package noct.parser.ast;

import noct.diagnostic.Span;
import noct.lexer.Token;

/**
 * Expression nodes. Every expression can report the source span it covers,
 * so type errors can underline the whole offending expression.
 */
public sealed interface Expr permits Expr.Number, Expr.Bool, Expr.Name, Expr.Has, Expr.Unary, Expr.Binary {

    /** The source range covered by this expression (same line only; see {@link Span#to}). */
    Span span();

    /** A number literal, e.g. {@code 42}. */
    record Number(Token token, int value) implements Expr {
        public Span span() { return token.span(); }
    }

    /** A boolean literal: {@code true} or {@code false}. */
    record Bool(Token token, boolean value) implements Expr {
        public Span span() { return token.span(); }
    }

    /** A reference to a var or flag, e.g. {@code health}. */
    record Name(Token token) implements Expr {
        public Span span() { return token.span(); }
    }

    /** {@code has(item)}; {@code close} is the closing parenthesis. */
    record Has(Token keyword, Token item, Token close) implements Expr {
        public Span span() { return keyword.span().to(close.span()); }
    }

    /** {@code not x} or {@code -x}. */
    record Unary(Token operator, Expr operand) implements Expr {
        public Span span() { return operator.span().to(operand.span()); }
    }

    /** {@code left op right}, for arithmetic, comparison and logical operators. */
    record Binary(Expr left, Token operator, Expr right) implements Expr {
        public Span span() { return left.span().to(right.span()); }
    }
}
