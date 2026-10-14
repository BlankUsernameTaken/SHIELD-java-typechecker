package com.shield.symbol;

import java.util.List;

public class Symbol {
    public enum Kind { VARIABLE, PARAMETER, FIELD, METHOD, CLASS }

    private final String name;
    private final String type;
    private final Kind kind;
    private final int line;
    private final List<String> paramTypes;

    public Symbol(String name, String type, Kind kind, int line) {
        this(name, type, kind, line, List.of());
    }

    private Symbol(String name, String type, Kind kind, int line, List<String> paramTypes) {
        this.name = name;
        this.type = type;
        this.kind = kind;
        this.line = line;
        this.paramTypes = List.copyOf(paramTypes);
    }

    public static Symbol method(String name, String returnType, List<String> paramTypes, int line) {
        return new Symbol(name, returnType, Kind.METHOD, line, paramTypes);
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public Kind getKind() { return kind; }
    public int getLine() { return line; }
    public List<String> getParamTypes() { return paramTypes; }
    public boolean isMethod() { return kind == Kind.METHOD; }

    @Override
    public String toString() {
        if (kind == Kind.METHOD) {
            return name + "(" + String.join(", ", paramTypes) + ") : " + type
                    + " [METHOD, line " + line + "]";
        }
        return name + " : " + type + " [" + kind + ", line " + line + "]";
    }
}