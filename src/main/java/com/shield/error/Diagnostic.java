package com.shield.error;

// One reported problem. The parameters/fields: kind = error category, line = source line (0 if unknown), message = what went wrong, suggestion = how to fix it (may be null). 
// This is for the Error Reporter
public record Diagnostic(String kind, int line, String message, String suggestion) {}
