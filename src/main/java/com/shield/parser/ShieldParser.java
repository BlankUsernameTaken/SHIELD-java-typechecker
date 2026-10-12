package com.shield.parser;

import com.shield.ast.ASTNodes;
import com.shield.lexer.Tokenizer;
import com.shield.lexer.Tokenizer.Token;
import com.shield.lexer.Tokenizer.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.shield.lexer.Tokenizer.TokenType.*;

/**
 * Recursive-descent parser for the subset represented by SHIELD's Tokenizer.
 * This file supplies its own AST nodes (implementing the existing ASTNodes marker)
 * and ParseException, so no existing source files need to change.
 *
 * <p>Usage: {@code var program = ShieldParser.parse("int x = 1 + 2 * 3;");}
 * Or: {@code new ShieldParser(new Tokenizer().tokenize(source)).parse()}.
 * Requires Java 17 or newer; no external libraries.
 *
 * <p>Programs may contain classes, declarations, and statements. Class members
 * are fields or methods with typed parameters and block bodies; extends is
 * optional. Types are int, boolean, named classes, or one-dimensional arrays;
 * void is allowed only as a method return type. Statements include blocks,
 * assignment/call/object-creation statements, if/else, while, return, print with
 * exactly one expression, and empty statements. Local declarations require a
 * block when used inside if/while bodies. Expressions include literals, names,
 * grouping, unary !/-/+, binary operators, assignment, member access, indexing,
 * calls, object creation, and array creation with an explicit size.
 *
 * <p>Precedence from lowest to highest: assignment (right associative), ||, &&,
 * ==/!=, &lt;/&gt;, +/-, * /, unary, then member access/indexing/calls.
 * Other binary operators associate left; else binds to the nearest if.
 *
 * <p>This is not a full Java parser: modifiers (public/static/etc.), constructors,
 * strings, packages/imports, for loops, casts, generics, multidimensional arrays,
 * and operators absent from Tokenizer are unsupported. Comment and identifier
 * support is inherited unchanged from Tokenizer, including its existing print
 * keyword behavior. The special PrintStmt uses contiguous System.out.println;
 * spaced spellings are parsed as ordinary member calls.
 *
 * <p>Parsing fails at the first syntax error with a token and line number. Lexical
 * errors retain Tokenizer's exception. Undefined names, duplicate declarations,
 * operand types, integer ranges, return context, and inheritance rules are left
 * to semantic analysis. Instances are reusable but not thread safe.
 */
public final class ShieldParser {
    private final List<Token> tokens;
    private int current;

    public ShieldParser(List<Token> tokens) {
        this.tokens = List.copyOf(Objects.requireNonNull(tokens, "tokens"));
        if (this.tokens.isEmpty() || this.tokens.get(this.tokens.size() - 1).type() != EOF) {
            throw new IllegalArgumentException("Token stream must end with EOF");
        }
        for (int i = 0; i < this.tokens.size(); i++) {
            Token token = this.tokens.get(i);
            if (token.type() == null || token.lexeme() == null || token.line() < 1) {
                throw new IllegalArgumentException("Tokens need a type, lexeme, and positive line number");
            }
            if (token.type() == EOF && i != this.tokens.size() - 1) {
                throw new IllegalArgumentException("EOF must only appear at the end of the token stream");
            }
        }
    }

    /** Convenience entry point using the existing tokenizer. */
    public static Program parse(String source) {
        return new ShieldParser(new Tokenizer().tokenize(source)).parse();
    }

    /** Parse the entire stream. Calling again starts from the beginning. */
    public Program parse() {
        current = 0;
        List<Node> items = new ArrayList<>();
        int line = peek().line();
        while (!check(EOF)) {
            items.add(check(CLASS) ? classDeclaration() : declaration());
        }
        return new Program(items, line);
    }

    private ClassDecl classDeclaration() {
        Token start = consume(CLASS, "Expected 'class'");
        String name = consume(IDENTIFIER, "Expected class name").lexeme();
        String parent = match(EXTENDS) ? consume(IDENTIFIER, "Expected superclass name").lexeme() : null;
        consume(LBRACE, "Expected '{' before class body");
        List<VarDecl> fields = new ArrayList<>();
        List<MethodDecl> methods = new ArrayList<>();
        while (!check(RBRACE) && !check(EOF)) {
            TypeRef type = type(true);
            Token member = consume(IDENTIFIER, "Expected field or method name");
            if (match(LPAREN)) {
                List<Parameter> parameters = new ArrayList<>();
                if (!check(RPAREN)) {
                    do {
                        TypeRef parameterType = type(false);
                        Token parameterName = consume(IDENTIFIER, "Expected parameter name");
                        parameters.add(new Parameter(parameterType, parameterName.lexeme(), parameterType.line()));
                    } while (match(COMMA));
                }
                consume(RPAREN, "Expected ')' after parameters");
                methods.add(new MethodDecl(type, member.lexeme(), parameters, block(), type.line()));
            } else {
                if (type.name().equals("void")) {
                    throw error(member, "Fields cannot have type void");
                }
                fields.add(variableTail(type, member));
            }
        }
        consume(RBRACE, "Expected '}' after class body");
        return new ClassDecl(name, parent, fields, methods, start.line());
    }

    private Statement declaration() {
        if (startsVariableDeclaration()) {
            TypeRef type = type(false);
            Token name = consume(IDENTIFIER, "Expected variable name");
            return variableTail(type, name);
        }
        return statement();
    }

    private boolean startsVariableDeclaration() {
        if (check(INT) || check(BOOLEAN)) return true;
        if (!check(IDENTIFIER)) return false;
        // A named type starts Foo name or Foo[] name. Foo[index] is an expression.
        return look(1).type() == IDENTIFIER
                || (look(1).type() == LBRACKET && look(2).type() == RBRACKET);
    }

    private TypeRef type(boolean allowVoid) {
        Token name = peek();
        if (!match(INT, BOOLEAN, IDENTIFIER) && !(allowVoid && match(VOID))) {
            throw error(peek(), "Expected " + (allowVoid ? "a type or void" : "a non-void type"));
        }
        boolean array = match(LBRACKET);
        if (array) {
            if (name.type() == VOID) throw error(name, "void cannot be an array element type");
            consume(RBRACKET, "Expected ']' in array type");
        }
        return new TypeRef(name.lexeme(), array, name.line());
    }

    private VarDecl variableTail(TypeRef type, Token name) {
        Expression initializer = match(ASSIGN) ? expression() : null;
        consume(SEMICOLON, "Expected ';' after variable declaration");
        return new VarDecl(type, name.lexeme(), initializer, type.line());
    }

    private Statement statement() {
        if (check(LBRACE)) return block();
        if (match(IF)) {
            Token start = previous();
            consume(LPAREN, "Expected '(' after if");
            Expression condition = expression();
            consume(RPAREN, "Expected ')' after if condition");
            Statement thenBranch = statement();
            Statement elseBranch = match(ELSE) ? statement() : null;
            return new IfStmt(condition, thenBranch, elseBranch, start.line());
        }
        if (match(WHILE)) {
            Token start = previous();
            consume(LPAREN, "Expected '(' after while");
            Expression condition = expression();
            consume(RPAREN, "Expected ')' after while condition");
            return new WhileStmt(condition, statement(), start.line());
        }
        if (match(PRINT)) {
            Token start = previous();
            consume(LPAREN, "Expected '(' after System.out.println");
            Expression value = expression();
            consume(RPAREN, "Expected ')' after print value");
            consume(SEMICOLON, "Expected ';' after print statement");
            return new PrintStmt(value, start.line());
        }
        if (match(RETURN)) {
            Token start = previous();
            Expression value = check(SEMICOLON) ? null : expression();
            consume(SEMICOLON, "Expected ';' after return statement");
            return new ReturnStmt(value, start.line());
        }
        if (match(SEMICOLON)) return new EmptyStmt(previous().line());
        Token start = peek();
        Expression value = expression();
        if (!(value instanceof Assignment || value instanceof Call || value instanceof NewObject)) {
            throw error(start, "Expected an assignment, method call, or object creation statement");
        }
        consume(SEMICOLON, "Expected ';' after expression statement");
        return new ExpressionStmt(value, start.line());
    }

    private Block block() {
        Token start = consume(LBRACE, "Expected '{' before block");
        List<Statement> statements = new ArrayList<>();
        while (!check(RBRACE) && !check(EOF)) statements.add(declaration());
        consume(RBRACE, "Expected '}' after block");
        return new Block(statements, start.line());
    }

    private Expression expression() { return assignment(); }

    private Expression assignment() {
        Expression left = logicalOr();
        if (match(ASSIGN)) {
            Token operator = previous();
            if (!(left instanceof Identifier || left instanceof MemberAccess || left instanceof ArrayAccess)) {
                throw error(operator, "Invalid assignment target");
            }
            return new Assignment(left, assignment(), left.line());
        }
        return left;
    }

    private Expression logicalOr() { return binary(this::logicalAnd, OR); }
    private Expression logicalAnd() { return binary(this::equality, AND); }
    private Expression equality() { return binary(this::comparison, EQUALS_EQUALS, NOT_EQUALS); }
    private Expression comparison() { return binary(this::addition, LESS_THAN, GREATER_THAN); }
    private Expression addition() { return binary(this::multiplication, PLUS, MINUS); }
    private Expression multiplication() { return binary(this::unary, MULTIPLY, DIVIDE); }

    private Expression binary(Supplier<Expression> operand, TokenType... operators) {
        Expression left = operand.get();
        while (match(operators)) {
            Token operator = previous();
            left = new BinaryExpr(left, operator.type(), operand.get(), left.line());
        }
        return left;
    }

    private Expression unary() {
        if (match(NOT, MINUS, PLUS)) {
            Token operator = previous();
            return new UnaryExpr(operator.type(), unary(), operator.line());
        }
        return postfix();
    }

    private Expression postfix() {
        Expression value = primary();
        while (true) {
            if (match(LBRACKET)) {
                Expression index = expression();
                consume(RBRACKET, "Expected ']' after array index");
                value = new ArrayAccess(value, index, value.line());
            } else if (match(DOT)) {
                Token member = consume(IDENTIFIER, "Expected member name after '.'");
                value = new MemberAccess(value, member.lexeme(), value.line());
            } else if (match(LPAREN)) {
                if (!(value instanceof Identifier || value instanceof MemberAccess)) {
                    throw error(previous(), "Expected a method name before '('");
                }
                value = new Call(value, arguments(), value.line());
            } else {
                return value;
            }
        }
    }

    /** Called after the opening parenthesis; consumes the closing one. */
    private List<Expression> arguments() {
        List<Expression> arguments = new ArrayList<>();
        if (!check(RPAREN)) {
            do { arguments.add(expression()); } while (match(COMMA));
        }
        consume(RPAREN, "Expected ')' after arguments");
        return arguments;
    }

    private Expression primary() {
        if (match(NUMBER)) return new IntegerLiteral(previous().lexeme(), previous().line());
        if (match(TRUE, FALSE)) return new BooleanLiteral(previous().type() == TRUE, previous().line());
        if (match(IDENTIFIER)) return new Identifier(previous().lexeme(), previous().line());
        if (match(LPAREN)) {
            Expression value = expression();
            consume(RPAREN, "Expected ')' after expression");
            return value;
        }
        if (match(NEW)) {
            Token start = previous();
            Token name = peek();
            if (!match(INT, BOOLEAN, IDENTIFIER)) throw error(name, "Expected type after new");
            if (match(LBRACKET)) {
                Expression size = expression();
                consume(RBRACKET, "Expected ']' after array size");
                if (check(LBRACKET)) {
                    throw error(peek(), "Only one-dimensional array creation is supported");
                }
                return new NewArray(new TypeRef(name.lexeme(), false, name.line()), size, start.line());
            }
            if (name.type() != IDENTIFIER) throw error(name, "Primitive allocation requires an array size");
            consume(LPAREN, "Expected '(' after class name");
            return new NewObject(name.lexeme(), arguments(), start.line());
        }
        throw error(peek(), "Expected expression");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                if (type != EOF) current++;
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            Token token = peek();
            if (type != EOF) current++;
            return token;
        }
        throw error(peek(), message);
    }

    private boolean check(TokenType type) { return peek().type() == type; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
    private Token look(int offset) { return tokens.get(Math.min(current + offset, tokens.size() - 1)); }
    private ParseException error(Token token, String message) { return new ParseException(token, message); }

    public interface Node extends ASTNodes { int line(); }

    public interface Statement extends Node {}
    public interface Expression extends Node {}

    public record Program(List<Node> items, int line) implements Node {
        public Program { items = List.copyOf(items); }
    }

    /** A syntactic type name, not a resolved semantic Type. Arrays are one dimensional. */
    public record TypeRef(String name, boolean array, int line) implements Node {}

    /** superClass is null when no extends clause is present. */
    public record ClassDecl(String name, String superClass, List<VarDecl> fields,
                     List<MethodDecl> methods, int line) implements Node {
        public ClassDecl {
            fields = List.copyOf(fields);
            methods = List.copyOf(methods);
        }
    }

    public record Parameter(TypeRef type, String name, int line) implements Node {}

    public record MethodDecl(TypeRef returnType, String name, List<Parameter> parameters,
                      Block body, int line) implements Node {
        public MethodDecl { parameters = List.copyOf(parameters); }
    }

    /** initializer is null for a declaration without an initializer. */
    public record VarDecl(TypeRef type, String name, Expression initializer,
                   int line) implements Statement {}

    public record Block(List<Statement> statements, int line) implements Statement {
        public Block { statements = List.copyOf(statements); }
    }

    /** elseBranch is null when the optional else is absent. */
    public record IfStmt(Expression condition, Statement thenBranch, Statement elseBranch,
                  int line) implements Statement {}

    public record WhileStmt(Expression condition, Statement body, int line) implements Statement {}
    public record PrintStmt(Expression value, int line) implements Statement {}
    /** value is null for return;. */
    public record ReturnStmt(Expression value, int line) implements Statement {}
    public record ExpressionStmt(Expression expression, int line) implements Statement {}
    public record EmptyStmt(int line) implements Statement {}

    /** Keep the digit spelling so parsing does not impose integer range/type rules. */
    public record IntegerLiteral(String digits, int line) implements Expression {}
    public record BooleanLiteral(boolean value, int line) implements Expression {}
    public record Identifier(String name, int line) implements Expression {}
    public record UnaryExpr(TokenType operator, Expression operand, int line) implements Expression {}
    public record BinaryExpr(Expression left, TokenType operator, Expression right,
                      int line) implements Expression {}
    public record Assignment(Expression target, Expression value, int line) implements Expression {}
    public record MemberAccess(Expression receiver, String member, int line) implements Expression {}
    public record ArrayAccess(Expression array, Expression index, int line) implements Expression {}

    /** callee is an Identifier or MemberAccess; resolution happens later. */
    public record Call(Expression callee, List<Expression> arguments, int line) implements Expression {
        public Call { arguments = List.copyOf(arguments); }
    }

    public record NewObject(String className, List<Expression> arguments,
                     int line) implements Expression {
        public NewObject { arguments = List.copyOf(arguments); }
    }

    public record NewArray(TypeRef elementType, Expression size, int line) implements Expression {}

    /** Syntax error at the offending token, including EOF for incomplete input. */
    public static final class ParseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        // Tokenizer.Token is not serializable; diagnostics are intended for local use.
        @SuppressWarnings("serial")
        private final Token token;
    
        public ParseException(Token token, String message) {
            super("Line " + token.line() + ": " + message
                    + (token.lexeme().isEmpty() ? " (at end of input)" : " (found '" + token.lexeme() + "')"));
            this.token = token;
        }
    
        public Token token() { return token; }
        public int line() { return token.line(); }
    }
}
