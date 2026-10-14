package com.shield.symbol;

import com.shield.parser.ShieldParser.ClassDecl;
import com.shield.parser.ShieldParser.MethodDecl;
import com.shield.parser.ShieldParser.Node;
import com.shield.parser.ShieldParser.Parameter;
import com.shield.parser.ShieldParser.Program;
import com.shield.parser.ShieldParser.TypeRef;
import com.shield.parser.ShieldParser.VarDecl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class-level pass: reads the parser's Program and registers every class, field and method.
 * It does NOT walk method bodies; the type checker does that using enterScope()/declare()/exitScope().
 */
public final class SymbolTableBuilder {
    private final SymbolTable table;
    private final List<String> errors = new ArrayList<>();

    public SymbolTableBuilder() {
        this(new SymbolTable());
    }

    public SymbolTableBuilder(SymbolTable table) {
        this.table = table;
    }

    public static String typeName(TypeRef ref) {
        return ref.array() ? ref.name() + "[]" : ref.name();
    }

    public SymbolTable build(Program program) {
        List<ClassDecl> declared = new ArrayList<>();

        for (Node item : program.items()) {
            if (item instanceof ClassDecl c) {
                if (table.declareClass(c.name(), c.superClass(), c.line())) {
                    declared.add(c);
                } else {
                    error(c.line(), "Duplicate class '" + c.name() + "'");
                }
            }
        }

        // Pass 2: validate the extends clauses
        for (ClassDecl c : declared) {
            String parent = c.superClass();
            if (parent == null) continue;
            if (!table.isClass(parent)) {
                error(c.line(), "Class '" + c.name() + "' extends unknown class '" + parent + "'");
            } else if (table.hasInheritanceCycle(c.name())) {
                error(c.line(), "Cyclic inheritance involving class '" + c.name() + "'");
            }
        }

        // Pass 3: register fields and methods
        for (ClassDecl c : declared) {
            for (VarDecl f : c.fields()) addField(c, f);
            for (MethodDecl m : c.methods()) addMethod(c, m);
        }

        return table;
    }

    private void addField(ClassDecl owner, VarDecl field) {
        String type = typeName(field.type());
        if (!table.isKnownType(type)) {
            error(field.line(), "Unknown type '" + type + "' for field '" + field.name() + "'");
        }
        Symbol symbol = new Symbol(field.name(), type, Symbol.Kind.FIELD, field.line());
        if (!table.declareField(owner.name(), symbol)) {
            error(field.line(), "Duplicate field '" + field.name() + "' in class '" + owner.name() + "'");
        }
    }

    private void addMethod(ClassDecl owner, MethodDecl method) {
        String returnType = typeName(method.returnType());
        if (!returnType.equals("void") && !table.isKnownType(returnType)) {
            error(method.line(), "Unknown return type '" + returnType + "' for method '" + method.name() + "'");
        }

        List<String> paramTypes = new ArrayList<>();
        Set<String> paramNames = new HashSet<>();
        for (Parameter p : method.parameters()) {
            String type = typeName(p.type());
            if (!table.isKnownType(type)) {
                error(p.line(), "Unknown type '" + type + "' for parameter '" + p.name() + "'");
            }
            if (!paramNames.add(p.name())) {
                error(p.line(), "Duplicate parameter '" + p.name() + "' in method '" + method.name() + "'");
            }
            paramTypes.add(type);
        }

        Symbol symbol = Symbol.method(method.name(), returnType, paramTypes, method.line());
        if (!table.declareMethod(owner.name(), symbol)) {
            error(method.line(), "Duplicate method '" + method.name() + "' in class '" + owner.name()
                    + "' (overloading is not supported)");
        }
    }

    public SymbolTable getTable() { return table; }
    public boolean hasErrors() { return !errors.isEmpty(); }
    public List<String> getErrors() { return Collections.unmodifiableList(errors); }

    private void error(int line, String message) {
        errors.add("Line " + line + ": " + message);
    }
}