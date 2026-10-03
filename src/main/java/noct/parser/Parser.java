package noct.parser;

import noct.diagnostic.NoctError;
import noct.lexer.Token;
import noct.lexer.TokenType;
import noct.parser.ast.Ast;
import noct.parser.ast.Expr;

import java.util.ArrayList;
import java.util.List;

import static noct.lexer.TokenType.*;

/**
 * A hand-written recursive-descent parser for the grammar in
 * {@code docs/grammar.md}. Each grammar rule is one method, named after
 * the rule. The grammar is LL(1), so the parser decides what to do by
 * looking at the next token only and never backtracks.
 *
 * <p>The parser stops at the first syntax error and reports it with the
 * offending token's location.
 */
public final class Parser {

    private final List<Token> tokens;
    private int current = 0;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Ast.Program parse(List<Token> tokens) {
        return new Parser(tokens).program();
    }

    // program = { declaration } , EOF ;
    private Ast.Program program() {
        List<Ast.Decl> declarations = new ArrayList<>();
        while (!check(EOF)) {
            declarations.add(declaration());
        }
        return new Ast.Program(declarations);
    }

    // ---------------------------------------------------------------------
    // Declarations
    // ---------------------------------------------------------------------

    // declaration = item_decl | flag_decl | var_decl | room_decl ;
    private Ast.Decl declaration() {
        if (match(ITEM)) return itemDecl();
        if (match(FLAG)) return flagDecl();
        if (match(VAR)) return varDecl();
        if (match(ROOM)) return roomDecl();

        if (check(INDENT)) throw unexpectedIndent();
        throw error("expected a declaration (item, flag, var or room), found " + peek().describe());
    }

    // item_decl = "item" , IDENTIFIER , STRING ;
    private Ast.ItemDecl itemDecl() {
        Token name = name("an item name after 'item'");
        Token display = expect(STRING, "the item's display name, e.g. \"Rusty Key\"");
        return new Ast.ItemDecl(name, display);
    }

    // flag_decl = "flag" , IDENTIFIER , "=" , BOOL ;
    private Ast.FlagDecl flagDecl() {
        Token name = name("a flag name after 'flag'");
        expect(ASSIGN, "'=' after the flag name");
        Token value = expect(BOOL, "'true' or 'false' as the flag's initial value");
        return new Ast.FlagDecl(name, value);
    }

    // var_decl = "var" , IDENTIFIER , "=" , expression ;
    private Ast.VarDecl varDecl() {
        Token name = name("a var name after 'var'");
        expect(ASSIGN, "'=' after the var name");
        return new Ast.VarDecl(name, expression());
    }

    // room_decl = "room" , IDENTIFIER , STRING , ":" , INDENT , room_stmt , { room_stmt } , DEDENT ;
    private Ast.RoomDecl roomDecl() {
        Token name = name("a room name after 'room'");
        Token display = expect(STRING, "the room's display name, e.g. \"Laboratory\"");
        expect(COLON, "':' after the room's display name");
        expect(INDENT, "an indented room body on the next line");

        List<Ast.Member> members = new ArrayList<>();
        do {
            members.add(member());
        } while (!check(DEDENT) && !check(EOF));

        expect(DEDENT, "end of the room body");
        return new Ast.RoomDecl(name, display, members);
    }

    // ---------------------------------------------------------------------
    // Room members
    // ---------------------------------------------------------------------

    // room_stmt = door_decl | object_decl | handler ;
    private Ast.Member member() {
        if (match(DOOR)) return doorDecl();
        if (match(OBJECT)) return objectDecl();
        if (match(ON)) return handler();

        if (check(INDENT)) throw unexpectedIndent();
        throw error("expected 'door', 'object' or 'on' inside a room, found " + peek().describe());
    }

    // door_decl = "door" , IDENTIFIER , "->" , IDENTIFIER , { door_option } ;
    // door_option = "locked" | "requires" , IDENTIFIER ;
    private Ast.DoorDecl doorDecl() {
        Token name = name("a door name after 'door'");
        expect(ARROW, "'->' after the door name");
        Token target = name("the name of the room this door leads to");

        boolean locked = false;
        Token requiredItem = null;

        while (true) {
            if (match(LOCKED)) {
                if (locked) throw errorAt(previous(), "'locked' is given twice for this door");
                locked = true;
            } else if (match(REQUIRES)) {
                if (requiredItem != null) throw errorAt(previous(), "'requires' is given twice for this door");
                requiredItem = name("an item name after 'requires'");
            } else {
                break;
            }
        }

        return new Ast.DoorDecl(name, target, locked, requiredItem);
    }

    // object_decl = "object" , IDENTIFIER , STRING ;
    private Ast.ObjectDecl objectDecl() {
        Token name = name("an object name after 'object'");
        Token display = expect(STRING, "the object's display name, e.g. \"Old Mirror\"");
        return new Ast.ObjectDecl(name, display);
    }

    // handler = "on" , trigger , ":" , block ;
    // trigger = "enter" | "inspect" , IDENTIFIER | "use" , IDENTIFIER , "on" , IDENTIFIER ;
    private Ast.Handler handler() {
        if (match(ENTER)) {
            Token keyword = previous();
            expect(COLON, "':' after 'on enter'");
            return new Ast.OnEnter(keyword, block());
        }

        if (match(INSPECT)) {
            Token target = name("the name of a door or object to inspect");
            expect(COLON, "':' after the inspect target");
            return new Ast.OnInspect(target, block());
        }

        if (match(USE)) {
            Token item = name("an item name after 'use'");
            expect(ON, "'on' after the item, as in 'use key on door'");
            Token target = name("the name of a door or object to use the item on");
            expect(COLON, "':' after the use target");
            return new Ast.OnUse(item, target, block());
        }

        throw error("expected 'enter', 'inspect' or 'use' after 'on', found " + peek().describe());
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    // block = INDENT , action , { action } , DEDENT ;
    private List<Ast.Action> block() {
        expect(INDENT, "an indented block of actions on the next line");

        List<Ast.Action> actions = new ArrayList<>();
        do {
            actions.add(action());
        } while (!check(DEDENT) && !check(EOF));

        expect(DEDENT, "end of the block");
        return actions;
    }

    // action = say_stmt | set_stmt | give_stmt | take_stmt | lock_stmt | unlock_stmt | end_stmt | if_stmt ;
    private Ast.Action action() {
        if (match(SAY)) return new Ast.Say(previous(), expect(STRING, "a string after 'say'"));
        if (match(SET)) return set();
        if (match(GIVE)) return new Ast.Give(name("an item name after 'give'"));
        if (match(TAKE)) return new Ast.Take(name("an item name after 'take'"));
        if (match(LOCK)) return new Ast.Lock(name("a door name after 'lock'"));
        if (match(UNLOCK)) return new Ast.Unlock(name("a door name after 'unlock'"));
        if (match(END)) return new Ast.End(previous(), expect(STRING, "a string after 'end'"));
        if (match(IF)) return ifAction();

        if (check(INDENT)) throw unexpectedIndent();
        if (check(ELSE)) throw error("'else' without a matching 'if'");
        if (check(FLAG) || check(VAR)) {
            throw error("declarations are only allowed at the top level")
                    .help("use 'set name, value' to change a var or flag inside a handler");
        }
        throw error("expected an action (say, set, give, take, lock, unlock, end or if), found "
                + peek().describe());
    }

    // set_stmt = "set" , IDENTIFIER , "," , expression ;
    private Ast.Set set() {
        Token target = name("a var or flag name after 'set'");
        if (check(ASSIGN)) {
            throw error("expected ',' after the name in 'set'")
                    .help("Noct writes assignment as 'set " + target.lexeme() + ", value'");
        }
        expect(COMMA, "',' after the name in 'set'");
        return new Ast.Set(target, expression());
    }

    // if_stmt = "if" , expression , ":" , block , [ "else" , ":" , block ] ;
    private Ast.If ifAction() {
        Token keyword = previous();
        Expr condition = expression();
        expect(COLON, "':' after the if condition");
        List<Ast.Action> thenBranch = block();

        List<Ast.Action> elseBranch = List.of();
        if (match(ELSE)) {
            expect(COLON, "':' after 'else'");
            elseBranch = block();
        }
        return new Ast.If(keyword, condition, thenBranch, elseBranch);
    }

    // ---------------------------------------------------------------------
    // Expressions, from lowest to highest precedence
    // ---------------------------------------------------------------------

    // expression = or_expr ;
    private Expr expression() {
        return orExpr();
    }

    // or_expr = and_expr , { "or" , and_expr } ;
    private Expr orExpr() {
        Expr expr = andExpr();
        while (match(OR)) {
            Token op = previous();
            expr = new Expr.Binary(expr, op, andExpr());
        }
        return expr;
    }

    // and_expr = not_expr , { "and" , not_expr } ;
    private Expr andExpr() {
        Expr expr = notExpr();
        while (match(AND)) {
            Token op = previous();
            expr = new Expr.Binary(expr, op, notExpr());
        }
        return expr;
    }

    // not_expr = "not" , not_expr | comparison ;
    private Expr notExpr() {
        if (match(NOT)) {
            Token op = previous();
            return new Expr.Unary(op, notExpr());
        }
        return comparison();
    }

    // comparison = additive , [ comp_op , additive ] ;
    private Expr comparison() {
        Expr expr = additive();
        if (match(EQ, NEQ, LT, LE, GT, GE)) {
            Token op = previous();
            expr = new Expr.Binary(expr, op, additive());

            if (check(EQ, NEQ, LT, LE, GT, GE)) {
                throw error("comparisons cannot be chained")
                        .help("combine them with 'and', e.g. 'a < b and b < c'");
            }
        }
        return expr;
    }

    // additive = multiplicative , { ( "+" | "-" ) , multiplicative } ;
    private Expr additive() {
        Expr expr = multiplicative();
        while (match(PLUS, MINUS)) {
            Token op = previous();
            expr = new Expr.Binary(expr, op, multiplicative());
        }
        return expr;
    }

    // multiplicative = unary , { ( "*" | "/" ) , unary } ;
    private Expr multiplicative() {
        Expr expr = unary();
        while (match(STAR, SLASH)) {
            Token op = previous();
            expr = new Expr.Binary(expr, op, unary());
        }
        return expr;
    }

    // unary = "-" , unary | primary ;
    private Expr unary() {
        if (match(MINUS)) {
            Token op = previous();
            return new Expr.Unary(op, unary());
        }
        return primary();
    }

    // primary = NUMBER | BOOL | IDENTIFIER | "has" , "(" , IDENTIFIER , ")" | "(" , expression , ")" ;
    private Expr primary() {
        if (match(NUMBER)) return new Expr.Number(previous(), (Integer) previous().value());
        if (match(BOOL)) return new Expr.Bool(previous(), (Boolean) previous().value());
        if (match(IDENTIFIER)) return new Expr.Name(previous());

        if (match(HAS)) {
            Token keyword = previous();
            expect(LPAREN, "'(' after 'has'");
            Token item = name("an item name inside has(...)");
            Token close = expect(RPAREN, "')' after the item name");
            return new Expr.Has(keyword, item, close);
        }

        if (match(LPAREN)) {
            Expr inner = expression();
            expect(RPAREN, "')' to close the parenthesis");
            return inner;
        }

        if (check(NOT)) {
            // e.g. "a == not b": 'not' binds looser than comparison, so it needs parentheses here.
            throw error("'not' must be wrapped in parentheses here")
                    .help("write '(not " + peekAhead(1).lexeme() + ")'");
        }
        throw error("expected an expression, found " + peek().describe());
    }

    // ---------------------------------------------------------------------
    // Token helpers
    // ---------------------------------------------------------------------

    /** Consumes an identifier; keywords get a dedicated message since they are a common mistake. */
    private Token name(String what) {
        if (check(IDENTIFIER)) return advance();
        if (peek().type().isKeyword()) {
            throw error("expected " + what + ", found " + peek().describe())
                    .note("'" + peek().lexeme() + "' is a reserved keyword and cannot be used as a name");
        }
        throw error("expected " + what + ", found " + peek().describe());
    }

    private Token expect(TokenType type, String what) {
        if (check(type)) return advance();
        throw error("expected " + what + ", found " + peek().describe());
    }

    private boolean match(TokenType... types) {
        if (check(types)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean check(TokenType... types) {
        TokenType actual = peek().type();
        for (TokenType t : types) {
            if (actual == t) return true;
        }
        return false;
    }

    private Token advance() {
        Token t = peek();
        if (t.type() != EOF) current++;
        return t;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekAhead(int distance) {
        return tokens.get(Math.min(current + distance, tokens.size() - 1));
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private NoctError unexpectedIndent() {
        return error("unexpected indentation")
                .help("only a line ending in ':' can be followed by a more indented block");
    }

    private NoctError error(String message) {
        return errorAt(peek(), message);
    }

    private NoctError errorAt(Token token, String message) {
        return NoctError.syntax(token.span(), message);
    }
}
