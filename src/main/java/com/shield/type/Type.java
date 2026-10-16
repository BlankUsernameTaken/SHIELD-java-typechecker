package com.shield.type;

import com.shield.symbol.SymbolTable;
import java.util.Objects;

public class Type {
    public enum Kind {
        INT,
        BOOLEAN,
        INT_ARRAY,
        CLASS,
        VOID,
        ERROR // Sentinel type for error recovery
    }

    public static final Type INT = new Type(Kind.INT, "int");
    public static final Type BOOLEAN = new Type(Kind.BOOLEAN, "boolean");
    public static final Type INT_ARRAY = new Type(Kind.INT_ARRAY, "int[]");
    public static final Type VOID = new Type(Kind.VOID, "void");
    public static final Type ERROR = new Type(Kind.ERROR, "error");

    private final Kind kind;
    private final String className; // Non-null only when kind == CLASS

    private Type(Kind kind, String className) {
        this.kind = kind;
        this.className = className;
    }

    public static Type clazz(String className) {
        return new Type(Kind.CLASS, className);
    }

    public static Type fromString(String typeStr) {
        if (typeStr == null) return ERROR;
        return switch (typeStr) {
            case "int" -> INT;
            case "boolean" -> BOOLEAN;
            case "int[]" -> INT_ARRAY;
            case "void" -> VOID;
            default -> clazz(typeStr);
        };
    }

    public Kind getKind() {
        return kind;
    }

    public String getClassName() {
        return className;
    }

    public boolean isPrimitive() {
        return kind == Kind.INT || kind == Kind.BOOLEAN;
    }

    public boolean isNumeric() {
        return kind == Kind.INT;
    }

    public boolean isBoolean() {
        return kind == Kind.BOOLEAN;
    }

    public boolean isArray() {
        return kind == Kind.INT_ARRAY;
    }

    public boolean isClass() {
        return kind == Kind.CLASS;
    }

    public boolean isError() {
        return kind == Kind.ERROR;
    }

    /**
     * Checks if this source type can be assigned to the target type.
     * Enforces subtyping rules for classes using SymbolTable.
     */
    public boolean isAssignableTo(Type target, SymbolTable symbolTable) {
        if (this.isError() || target.isError()) return true; // Suppress cascading errors
        if (this.equals(target)) return true;

        // Class inheritance subtyping: target = child class instance assigned to parent type variable
        if (this.isClass() && target.isClass()) {
            return symbolTable.isSubtype(this.className, target.className);
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Type type)) return false;
        return kind == type.kind && Objects.equals(className, type.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, className);
    }

    @Override
    public String toString() {
        if (kind == Kind.CLASS) return className;
        return kind.name().toLowerCase();
    }
}