package com.shield.checker;

import com.shield.error.ErrorReporter;
import com.shield.lexer.Tokenizer.TokenType;
import com.shield.parser.ShieldParser.*;
import com.shield.symbol.ClassInfo;
import com.shield.symbol.Symbol;
import com.shield.symbol.SymbolTable;
import com.shield.type.Type;

import java.util.ArrayList;
import java.util.List;

public class TypeChecker {

    private final SymbolTable symbolTable;
    private final ErrorReporter errorReporter;

    private String currentClass = null;
    private Type currentMethodReturnType = null;

    public TypeChecker(SymbolTable symbolTable, ErrorReporter errorReporter) {
        this.symbolTable = symbolTable;
        this.errorReporter = errorReporter;
    }

    public boolean check(Program program) {
        // Pass 1: First declare all classes, fields, and method signatures in the Symbol Table
        collectSymbols(program);

        // Pass 2: Type check class method bodies and global statements
        for (Node item : program.items()) {
            if (item instanceof ClassDecl cd) {
                checkClass(cd);
            } else if (item instanceof Statement stmt) {
                checkStatement(stmt);
            }
        }

        return !errorReporter.hasErrors();
    }

    // =========================================================================
    // PASS 1: Symbol Collection Phase
    // =========================================================================

    private void collectSymbols(Program program) {
        // First register class names
        for (Node item : program.items()) {
            if (item instanceof ClassDecl cd) {
                if (!symbolTable.declareClass(cd.name(), cd.superClass(), cd.line())) {
                    errorReporter.reportError(cd.line(),
                        "Duplicate class declaration '" + cd.name() + "'.",
                        "Rename or remove duplicate class definition.");
                }
            }
        }

        // Verify inheritance cycles
        for (Node item : program.items()) {
            if (item instanceof ClassDecl cd && cd.superClass() != null) {
                if (!symbolTable.isClass(cd.superClass())) {
                    errorReporter.reportError(cd.line(),
                        "Class '" + cd.name() + "' extends unknown class '" + cd.superClass() + "'.",
                        "Ensure superclass is declared.");
                } else if (symbolTable.hasInheritanceCycle(cd.name())) {
                    errorReporter.reportError(cd.line(),
                        "Inheritance cycle detected for class '" + cd.name() + "'.",
                        "Remove cyclic inheritance references.");
                }
            }
        }

        // Register class fields and methods
        for (Node item : program.items()) {
            if (item instanceof ClassDecl cd) {
                for (VarDecl field : cd.fields()) {
                    validateTypeRef(field.type(), field.line());
                    Symbol fieldSym = new Symbol(field.name(), resolveTypeStr(field.type()), Symbol.Kind.FIELD, field.line());
                    if (!symbolTable.declareField(cd.name(), fieldSym)) {
                        errorReporter.reportError(field.line(),
                            "Duplicate field '" + field.name() + "' in class '" + cd.name() + "'.",
                            "Use unique field names within a class.");
                    }
                }

                for (MethodDecl method : cd.methods()) {
                    validateTypeRef(method.returnType(), method.line());
                    List<String> paramTypes = new ArrayList<>();
                    for (Parameter p : method.parameters()) {
                        validateTypeRef(p.type(), p.line());
                        paramTypes.add(resolveTypeStr(p.type()));
                    }

                    Symbol methodSym = Symbol.method(method.name(), resolveTypeStr(method.returnType()), paramTypes, method.line());
                    if (!symbolTable.declareMethod(cd.name(), methodSym)) {
                        errorReporter.reportError(method.line(),
                            "Duplicate method '" + method.name() + "' in class '" + cd.name() + "'.",
                            "Method overloading is not supported in MiniJava.");
                    }
                }
            }
        }
    }

    // =========================================================================
    // PASS 2: Declaration & Statement Validation Phase
    // =========================================================================

    private void checkClass(ClassDecl cd) {
        currentClass = cd.name();
        for (MethodDecl method : cd.methods()) {
            checkMethod(method);
        }
        currentClass = null;
    }

    private void checkMethod(MethodDecl method) {
        currentMethodReturnType = Type.fromString(resolveTypeStr(method.returnType()));
        symbolTable.enterScope();

        for (Parameter p : method.parameters()) {
            Symbol paramSym = new Symbol(p.name(), resolveTypeStr(p.type()), Symbol.Kind.PARAMETER, p.line());
            if (!symbolTable.declare(paramSym)) {
                errorReporter.reportError(p.line(),
                    "Duplicate parameter name '" + p.name() + "' in method '" + method.name() + "'.",
                    "Use distinct parameter names.");
            }
        }

        checkStatement(method.body());

        symbolTable.exitScope();
        currentMethodReturnType = null;
    }

    private void checkStatement(Statement stmt) {
        if (stmt == null) return;

        if (stmt instanceof VarDecl decl) {
            checkVarDecl(decl);
        } else if (stmt instanceof Block block) {
            symbolTable.enterScope();
            for (Statement s : block.statements()) {
                checkStatement(s);
            }
            symbolTable.exitScope();
        } else if (stmt instanceof IfStmt ifStmt) {
            Type condType = checkExpression(ifStmt.condition());
            if (!condType.isBoolean() && !condType.isError()) {
                errorReporter.reportError(ifStmt.line(),
                    "If condition must evaluate to boolean, found '" + condType + "'.",
                    "Ensure condition expression results in a boolean value.");
            }
            checkStatement(ifStmt.thenBranch());
            checkStatement(ifStmt.elseBranch());
        } else if (stmt instanceof WhileStmt whileStmt) {
            Type condType = checkExpression(whileStmt.condition());
            if (!condType.isBoolean() && !condType.isError()) {
                errorReporter.reportError(whileStmt.line(),
                    "While condition must evaluate to boolean, found '" + condType + "'.",
                    "Ensure loop condition is a boolean expression.");
            }
            checkStatement(whileStmt.body());
        } else if (stmt instanceof PrintStmt printStmt) {
            Type exprType = checkExpression(printStmt.value());
            if (!exprType.isPrimitive() && !exprType.isError()) {
                errorReporter.reportError(printStmt.line(),
                    "System.out.println expects int or boolean expression, found '" + exprType + "'.",
                    "Provide a primitive int or boolean operand.");
            }
        } else if (stmt instanceof ReturnStmt returnStmt) {
            if (currentMethodReturnType == null) {
                errorReporter.reportError(returnStmt.line(),
                    "Return statement outside of method body.",
                    "Remove return statement or enclose inside a method.");
                return;
            }

            if (returnStmt.value() == null) {
                if (currentMethodReturnType.getKind() != Type.Kind.VOID) {
                    errorReporter.reportError(returnStmt.line(),
                        "Missing return value for non-void method expecting '" + currentMethodReturnType + "'.",
                        "Return a value compatible with " + currentMethodReturnType + ".");
                }
            } else {
                if (currentMethodReturnType.getKind() == Type.Kind.VOID) {
                    errorReporter.reportError(returnStmt.line(),
                        "Void method cannot return a value.",
                        "Use 'return;' without an expression.");
                } else {
                    Type valType = checkExpression(returnStmt.value());
                    if (!valType.isAssignableTo(currentMethodReturnType, symbolTable)) {
                        errorReporter.reportError(returnStmt.line(),
                            "Incompatible return type: expected '" + currentMethodReturnType + "', found '" + valType + "'.",
                            "Change returned value or update method return type signature.");
                    }
                }
            }
        } else if (stmt instanceof ExpressionStmt exprStmt) {
            checkExpression(exprStmt.expression());
        } else if (stmt instanceof EmptyStmt) {
            // Nothing to validate
        }
    }

    private void checkVarDecl(VarDecl decl) {
        validateTypeRef(decl.type(), decl.line());
        Type targetType = Type.fromString(resolveTypeStr(decl.type()));

        if (targetType.getKind() == Type.Kind.VOID) {
            errorReporter.reportError(decl.line(),
                "Variable '" + decl.name() + "' cannot be of type void.",
                "Specify a non-void primitive or class type.");
        }

        Symbol varSym = new Symbol(decl.name(), resolveTypeStr(decl.type()), Symbol.Kind.VARIABLE, decl.line());
        if (!symbolTable.declare(varSym)) {
            errorReporter.reportError(decl.line(),
                "Variable '" + decl.name() + "' is already declared in current scope.",
                "Choose a unique name or remove re-declaration.");
        }

        if (decl.initializer() != null) {
            Type initType = checkExpression(decl.initializer());
            if (!initType.isAssignableTo(targetType, symbolTable)) {
                errorReporter.reportError(decl.line(),
                    "Incompatible initializer assignment: expected '" + targetType + "', found '" + initType + "'.",
                    "Ensure right-hand expression matches variable type.");
            }
        }
    }

    // =========================================================================
    // PASS 3: Expression Type Inference Phase
    // =========================================================================

    public Type checkExpression(Expression expr) {
        if (expr == null) return Type.ERROR;

        if (expr instanceof IntegerLiteral) return Type.INT;
        if (expr instanceof BooleanLiteral) return Type.BOOLEAN;

        if (expr instanceof Identifier id) {
            Symbol sym = symbolTable.lookup(id.name());
            if (sym != null) {
                return Type.fromString(sym.getType());
            }

            // Check field in current class
            if (currentClass != null) {
                Symbol field = symbolTable.lookupField(currentClass, id.name());
                if (field != null) return Type.fromString(field.getType());
            }

            errorReporter.reportError(id.line(),
                "Undeclared variable '" + id.name() + "'.",
                "Declare variable before using it.");
            return Type.ERROR;
        }

        if (expr instanceof UnaryExpr unary) {
            Type operand = checkExpression(unary.operand());
            if (operand.isError()) return Type.ERROR;

            if (unary.operator() == TokenType.NOT) {
                if (!operand.isBoolean()) {
                    errorReporter.reportError(unary.line(),
                        "Logical NOT operator '!' requires boolean operand, found '" + operand + "'.",
                        "Apply '!' to a boolean expression.");
                    return Type.ERROR;
                }
                return Type.BOOLEAN;
            } else if (unary.operator() == TokenType.MINUS || unary.operator() == TokenType.PLUS) {
                if (!operand.isNumeric()) {
                    errorReporter.reportError(unary.line(),
                        "Unary arithmetic operator requires int operand, found '" + operand + "'.",
                        "Apply unary sign to an integer.");
                    return Type.ERROR;
                }
                return Type.INT;
            }
        }

        if (expr instanceof BinaryExpr binary) {
            Type left = checkExpression(binary.left());
            Type right = checkExpression(binary.right());
            if (left.isError() || right.isError()) return Type.ERROR;

            TokenType op = binary.operator();
            if (op == TokenType.PLUS || op == TokenType.MINUS || op == TokenType.MULTIPLY || op == TokenType.DIVIDE) {
                if (!left.isNumeric() || !right.isNumeric()) {
                    errorReporter.reportError(binary.line(),
                        "Arithmetic operator '" + op + "' expects (int, int), found (" + left + ", " + right + ").",
                        "Ensure operands are of type int.");
                    return Type.ERROR;
                }
                return Type.INT;
            } else if (op == TokenType.LESS_THAN || op == TokenType.GREATER_THAN) {
                if (!left.isNumeric() || !right.isNumeric()) {
                    errorReporter.reportError(binary.line(),
                        "Comparison operator '" + op + "' expects (int, int), found (" + left + ", " + right + ").",
                        "Ensure comparison operands are integers.");
                    return Type.ERROR;
                }
                return Type.BOOLEAN;
            } else if (op == TokenType.EQUALS_EQUALS || op == TokenType.NOT_EQUALS) {
                if (!left.isAssignableTo(right, symbolTable) && !right.isAssignableTo(left, symbolTable)) {
                    errorReporter.reportError(binary.line(),
                        "Equality check requires comparable types, found (" + left + ", " + right + ").",
                        "Compare values of compatible types.");
                    return Type.ERROR;
                }
                return Type.BOOLEAN;
            } else if (op == TokenType.AND || op == TokenType.OR) {
                if (!left.isBoolean() || !right.isBoolean()) {
                    errorReporter.reportError(binary.line(),
                        "Logical operator '" + op + "' expects (boolean, boolean), found (" + left + ", " + right + ").",
                        "Ensure logical operands evaluate to boolean.");
                    return Type.ERROR;
                }
                return Type.BOOLEAN;
            }
        }

        if (expr instanceof Assignment assign) {
            Type left = checkExpression(assign.target());
            Type right = checkExpression(assign.value());

            if (!left.isError() && !right.isError()) {
                if (!right.isAssignableTo(left, symbolTable)) {
                    errorReporter.reportError(assign.line(),
                        "Incompatible assignment: cannot assign '" + right + "' to '" + left + "'.",
                        "Convert right-hand side expression to target type.");
                }
            }
            return left;
        }

        if (expr instanceof ArrayAccess access) {
            Type arrType = checkExpression(access.array());
            Type idxType = checkExpression(access.index());

            if (!arrType.isArray() && !arrType.isError()) {
                errorReporter.reportError(access.line(),
                    "Array index operator [] used on non-array type '" + arrType + "'.",
                    "Ensure target variable is an array (e.g. int[]).");
            }
            if (!idxType.isNumeric() && !idxType.isError()) {
                errorReporter.reportError(access.line(),
                    "Array index must be of type int, found '" + idxType + "'.",
                    "Pass an integer value inside array brackets.");
            }
            return Type.INT;
        }

        if (expr instanceof MemberAccess mem) {
            Type receiverType = checkExpression(mem.receiver());
            if (receiverType.isError()) return Type.ERROR;

            if (receiverType.isArray() && mem.member().equals("length")) {
                return Type.INT;
            }

            if (receiverType.isClass()) {
                Symbol field = symbolTable.lookupField(receiverType.getClassName(), mem.member());
                if (field != null) {
                    return Type.fromString(field.getType());
                }
                errorReporter.reportError(mem.line(),
                    "Class '" + receiverType.getClassName() + "' has no field '" + mem.member() + "'.",
                    "Check field declaration in class.");
                return Type.ERROR;
            }

            errorReporter.reportError(mem.line(),
                "Member access '.' on unsupported type '" + receiverType + "'.",
                "Ensure receiver object is a class or array.");
            return Type.ERROR;
        }

        if (expr instanceof Call call) {
            if (call.callee() instanceof MemberAccess mem) {
                Type receiverType = checkExpression(mem.receiver());
                if (receiverType.isError()) return Type.ERROR;

                if (!receiverType.isClass()) {
                    errorReporter.reportError(call.line(),
                        "Method calls require an object reference, found '" + receiverType + "'.",
                        "Invoke method on a class instance.");
                    return Type.ERROR;
                }

                Symbol methodSym = symbolTable.lookupMethod(receiverType.getClassName(), mem.member());
                if (methodSym == null) {
                    errorReporter.reportError(call.line(),
                        "Method '" + mem.member() + "' not found in class '" + receiverType.getClassName() + "'.",
                        "Ensure method exists in class or superclass.");
                    return Type.ERROR;
                }

                validateArguments(methodSym, call.arguments(), call.line());
                return Type.fromString(methodSym.getType());
            } else if (call.callee() instanceof Identifier id) {
                if (currentClass == null) {
                    errorReporter.reportError(call.line(),
                        "Method call '" + id.name() + "' without object receiver outside class context.",
                        "Specify target object receiver.");
                    return Type.ERROR;
                }

                Symbol methodSym = symbolTable.lookupMethod(currentClass, id.name());
                if (methodSym == null) {
                    errorReporter.reportError(call.line(),
                        "Method '" + id.name() + "' not found in class '" + currentClass + "'.",
                        "Declare method or check method name.");
                    return Type.ERROR;
                }

                validateArguments(methodSym, call.arguments(), call.line());
                return Type.fromString(methodSym.getType());
            }
        }

        if (expr instanceof NewArray newArray) {
            Type sizeType = checkExpression(newArray.size());
            if (!sizeType.isNumeric() && !sizeType.isError()) {
                errorReporter.reportError(newArray.line(),
                    "Array dimension size must be an int, found '" + sizeType + "'.",
                    "Provide integer array dimension size.");
            }
            return Type.INT_ARRAY;
        }

        if (expr instanceof NewObject newObj) {
            if (!symbolTable.isClass(newObj.className())) {
                errorReporter.reportError(newObj.line(),
                    "Cannot instantiate unknown class '" + newObj.className() + "'.",
                    "Declare class before instantiation.");
                return Type.ERROR;
            }
            return Type.clazz(newObj.className());
        }

        return Type.ERROR;
    }

    private void validateArguments(Symbol methodSym, List<Expression> arguments, int line) {
        List<String> expectedTypes = methodSym.getParamTypes();
        if (expectedTypes.size() != arguments.size()) {
            errorReporter.reportError(line,
                "Method '" + methodSym.getName() + "' expects " + expectedTypes.size() + " arguments, received " + arguments.size() + ".",
                "Adjust argument count in invocation.");
            return;
        }

        for (int i = 0; i < arguments.size(); i++) {
            Type argType = checkExpression(arguments.get(i));
            Type paramType = Type.fromString(expectedTypes.get(i));
            if (!argType.isAssignableTo(paramType, symbolTable)) {
                errorReporter.reportError(line,
                    "Argument " + (i + 1) + " of method '" + methodSym.getName() + "' expects '" + paramType + "', found '" + argType + "'.",
                    "Provide matching argument type.");
            }
        }
    }

   private void validateTypeRef(TypeRef ref, int line) {
    if (ref.name().equals("void")) return; // void is allowed for method return signatures
    if (!symbolTable.isKnownType(ref.name())) {
        errorReporter.reportError(line,
            "Unknown type '" + ref.name() + "'.",
            "Declare class or use a valid primitive type.");
    }
}

    private String resolveTypeStr(TypeRef ref) {
        return ref.array() ? ref.name() + "[]" : ref.name();
    }
}