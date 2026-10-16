package com.shield.error;

import com.shield.parser.ShieldParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ErrorReporter {

    private final List<Diagnostic> errors = new ArrayList<>();
    private final String[] sourceLines;

    public ErrorReporter(String source) {
        this.sourceLines = source.split("\n");
    }

    // The entry point, the type checker reports its errors here
    public void add(String kind, int line, String message, String suggestion) {
        if (suggestion == null) suggestion = suggestionFor(message);
        errors.add(new Diagnostic(kind, line, message, suggestion));
    }

    // One catch block, sort exception by their kind
    public void addException(RuntimeException e) {
        if (e instanceof ShieldParser.ParseException p) addSyntax(p);
        else if (e.getMessage() != null && e.getMessage().startsWith("Unexpected character")) addLexical(e);
        else throw e;
    }

    public void addLexical(RuntimeException e) {
        Matcher m = Pattern.compile("(.*) at line (\\d+)").matcher(e.getMessage());
        if (m.matches()) add("Lexical Error", Integer.parseInt(m.group(2)), m.group(1), null);
        else add("Lexical Error", 0, e.getMessage(), null);
    }

    public void addSyntax(ShieldParser.ParseException e) {
        String message = e.getMessage().replaceFirst("^Line \\d+: ", "");
        int line = e.line();
        // The parser blames the line after a missing ';', then steps back to the line missing it.
        if (message.startsWith("Expected ';'")) line = previousCodeLine(line);
        add("Syntax Error", line, message, null);
    }

    public void addSemantic(List<String> symbolErrors) {
        for (String text : symbolErrors) {
            Matcher m = Pattern.compile("Line (\\d+): (.*)").matcher(text);
            if (m.matches()) add("Semantic Error", Integer.parseInt(m.group(1)), m.group(2), null);
            else add("Semantic Error", 0, text, null);
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void printReport() {
        errors.sort(Comparator.comparingInt(Diagnostic::line));
        for (Diagnostic d : errors) {
            System.out.println(d.kind() + " (Line " + d.line() + "): " + d.message());
            String code = lineText(d.line());
            if (!code.isBlank()) System.out.println("    " + code.strip());
            if (d.suggestion() != null) System.out.println("  Suggestion: " + d.suggestion());
            System.out.println();
        }
        System.out.println(errors.isEmpty()
                ? "Validation passed: no errors found."
                : "Validation failed: " + errors.size() + " error(s) found.");
    }

    private String suggestionFor(String message) {
        Matcher m = Pattern.compile("Expected '(.)'").matcher(message);
        if (m.find()) {
            return m.group(1).equals(";") ? "Add ';' at the end of the line." : "Insert '" + m.group(1) + "' here.";
        }
        if (message.contains("Duplicate")) return "Rename or remove one of the declarations.";
        if (message.contains("Unknown type") || message.contains("unknown class"))
            return "Check the spelling, or declare that class first.";
        if (message.startsWith("Unexpected character")) return "Remove the character; SHIELD does not support it.";
        return null;
    }

    private String lineText(int line) {
        return (line >= 1 && line <= sourceLines.length) ? sourceLines[line - 1] : "";
    }

    private int previousCodeLine(int line) {
        int prev = line - 1;
        while (prev > 1 && lineText(prev).isBlank()) prev--;
        return Math.max(prev, 1);
    }
}
