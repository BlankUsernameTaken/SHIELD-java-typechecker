package com.shield.symbol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SymbolTable {

    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();


    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();

    public SymbolTable() {
        enterScope(); // global scope
    }


    public void enterScope() {
        scopes.push(new LinkedHashMap<>());
    }

    public void exitScope() {
        if (scopes.size() > 1) scopes.pop();
    }

    public int getScopeDepth() {
        return scopes.size();
    }

    public boolean isGlobalScope() {
        return scopes.size() == 1;
    }


    public boolean declare(Symbol symbol) {
        Map<String, Symbol> current = scopes.peek();
        if (current.containsKey(symbol.getName())) return false;
        current.put(symbol.getName(), symbol);
        return true;
    }

    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : scopes) {
            Symbol found = scope.get(name);
            if (found != null) return found;
        }
        return null;
    }

    public Symbol lookupCurrentScope(String name) {
        return scopes.peek().get(name);
    }


    public boolean declareClass(String name, String parent, int line) {
        if (classes.containsKey(name)) return false;
        classes.put(name, new ClassInfo(name, parent, line));
        return true;
    }

    public ClassInfo lookupClass(String name) {
        return classes.get(name);
    }

    public boolean isClass(String name) {
        return classes.containsKey(name);
    }

    public boolean declareField(String className, Symbol field) {
        return requireClass(className).addField(field);
    }

    public boolean declareMethod(String className, Symbol method) {
        return requireClass(className).addMethod(method);
    }

    public Symbol lookupField(String className, String fieldName) {
        for (ClassInfo c : hierarchy(className)) {
            Symbol found = c.getField(fieldName);
            if (found != null) return found;
        }
        return null;
    }

    public Symbol lookupMethod(String className, String methodName) {
        for (ClassInfo c : hierarchy(className)) {
            Symbol found = c.getMethod(methodName);
            if (found != null) return found;
        }
        return null;
    }

    public boolean isSubtype(String child, String parent) {
        if (child == null || parent == null) return false;
        if (child.equals(parent)) return true;
        for (ClassInfo c : hierarchy(child)) {
            if (c.getName().equals(parent)) return true;
        }
        return false;
    }

    public boolean hasInheritanceCycle(String className) {
        ClassInfo start = classes.get(className);
        if (start == null) return false;
        Set<String> seen = new HashSet<>();
        String current = start.getParent();
        while (current != null) {
            if (current.equals(className)) return true;
            if (!seen.add(current)) return false; // loops somewhere else, not through this class
            ClassInfo info = classes.get(current);
            current = (info == null) ? null : info.getParent();
        }
        return false;
    }

    public boolean isKnownType(String type) {
        if (type == null) return false;
        return type.equals("int") || type.equals("boolean") || type.equals("int[]")
                || classes.containsKey(type);
    }


    public String dump() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Classes ===\n");
        if (classes.isEmpty()) sb.append("(none)\n");
        for (ClassInfo c : classes.values()) {
            sb.append("class ").append(c.getName());
            if (c.getParent() != null) sb.append(" extends ").append(c.getParent());
            sb.append(" (line ").append(c.getLine()).append(")\n");
            for (Symbol f : c.getFields().values()) sb.append("    ").append(f).append('\n');
            for (Symbol m : c.getMethods().values()) sb.append("    ").append(m).append('\n');
        }

        sb.append("=== Open scopes (outermost first) ===\n");
        int depth = 0;
        Iterator<Map<String, Symbol>> it = scopes.descendingIterator();
        while (it.hasNext()) {
            Map<String, Symbol> scope = it.next();
            sb.append("scope ").append(depth).append(depth == 0 ? " (global)" : "").append(":\n");
            if (scope.isEmpty()) sb.append("    (empty)\n");
            for (Symbol s : scope.values()) sb.append("    ").append(s).append('\n');
            depth++;
        }
        return sb.toString();
    }

    private ClassInfo requireClass(String className) {
        ClassInfo info = classes.get(className);
        if (info == null) throw new IllegalArgumentException("Unknown class: " + className);
        return info;
    }

    private List<ClassInfo> hierarchy(String className) {
        List<ClassInfo> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String current = className;
        while (current != null && seen.add(current)) {
            ClassInfo info = classes.get(current);
            if (info == null) break;
            chain.add(info);
            current = info.getParent();
        }
        return chain;
    }
}