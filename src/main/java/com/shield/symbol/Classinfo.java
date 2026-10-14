package com.shield.symbol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything the symbol table knows about one declared class. */
public class ClassInfo {
    private final String name;
    private final String parent;
    private final int line;
    private final Map<String, Symbol> fields = new LinkedHashMap<>();
    private final Map<String, Symbol> methods = new LinkedHashMap<>();

    public ClassInfo(String name, String parent, int line) {
        this.name = name;
        this.parent = parent;
        this.line = line;
    }

    public String getName() { return name; }
    public String getParent() { return parent; }
    public int getLine() { return line; }

    public boolean addField(Symbol field) {
        if (fields.containsKey(field.getName())) return false;
        fields.put(field.getName(), field);
        return true;
    }

    public boolean addMethod(Symbol method) {
        if (methods.containsKey(method.getName())) return false;
        methods.put(method.getName(), method);
        return true;
    }

    public Symbol getField(String fieldName) { return fields.get(fieldName); }
    public Symbol getMethod(String methodName) { return methods.get(methodName); }

    public Map<String, Symbol> getFields() { return Collections.unmodifiableMap(fields); }
    public Map<String, Symbol> getMethods() { return Collections.unmodifiableMap(methods); }
}