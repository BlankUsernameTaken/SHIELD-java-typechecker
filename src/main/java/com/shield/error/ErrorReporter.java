package com.shield.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ErrorReporter {

    public record Diagnostic(
        String classification,
        int line,
        String message,
        String suggestion
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(classification).append(" Error] Line ").append(line).append(": ").append(message);
            if (suggestion != null && !suggestion.isBlank()) {
                sb.append("\n  ↳ Suggestion: ").append(suggestion);
            }
            return sb.toString();
        }
    }

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void report(String classification, int line, String message, String suggestion) {
        diagnostics.add(new Diagnostic(classification, line, message, suggestion));
    }

    public void reportError(int line, String message, String suggestion) {
        report("Type", line, message, suggestion);
    }

    public boolean hasErrors() {
        return !diagnostics.isEmpty();
    }

    public int getErrorCount() {
        return diagnostics.size();
    }

    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }

    public void printSummary() {
        if (!hasErrors()) {
            System.out.println("TYPE CHECK PASSED: 0 errors detected.");
            return;
        }

        System.out.println("TYPE CHECK FAILED (" + getErrorCount() + " error(s) found):");
        System.out.println("==================================================");
        for (int i = 0; i < diagnostics.size(); i++) {
            System.out.println((i + 1) + ". " + diagnostics.get(i));
        }
        System.out.println("==================================================");
    }

    public void clear() {
        diagnostics.clear();
    }
}
