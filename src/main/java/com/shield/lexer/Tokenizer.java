package com.shield.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Tokenizer {

    public record Token(TokenType type, String lexeme, int line) {}

    public enum TokenType {
        // Keywords
        CLASS, EXTENDS, INT, BOOLEAN, VOID, IF, ELSE, WHILE, RETURN, TRUE, FALSE, NEW, PRINT,
        // Literals and Identifiers
        IDENTIFIER, NUMBER,
        // Operators and Punctuation
        ASSIGN, PLUS, MINUS, MULTIPLY, DIVIDE, 
        LESS_THAN, GREATER_THAN, EQUALS_EQUALS, NOT_EQUALS, AND, OR, NOT,
        SEMICOLON, COMMA, DOT, 
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        EOF
    }

    private static final Set<String> KEYWORDS = Set.of(
        "class", "extends", "int", "boolean", "void", "if", "else", 
        "while", "return", "true", "false", "new", "System.out.println"
    );

    public List<Token> tokenize(String sourceCode) {
        List<Token> tokens = new ArrayList<>();
        int length = sourceCode.length();
        int index = 0;
        int line = 1;

        while (index < length) {
            char current = sourceCode.charAt(index);

            // Track Newlines and Skip Whitespace
            if (current == '\n') {
                line++;
                index++;
                continue;
            }
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }

            //Skips Comments
            if (current == '/' && index + 1 < length && sourceCode.charAt(index + 1) == '/') {
                while (index < length && sourceCode.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }

            // Identifiers & Keywords
            if (Character.isLetter(current) || current == '_') {
                int start = index;
                while (index < length && (Character.isLetterOrDigit(sourceCode.charAt(index)) || sourceCode.charAt(index) == '_')) {
                    index++;
                }
                String lexeme = sourceCode.substring(start, index);
                
                // Handle special multi-word keyword sequence for print/normal keyword check
                if (lexeme.equals("System") && sourceCode.startsWith(".out.println", index)) {
                    // Handling for System.out.println statement keyword
                    index += 12; // length of ".out.println"
                    tokens.add(new Token(TokenType.PRINT, "System.out.println", line));
                } else if (KEYWORDS.contains(lexeme)) {
                    TokenType type = switch (lexeme) {
                        case "class" -> TokenType.CLASS;
                        case "extends" -> TokenType.EXTENDS;
                        case "int" -> TokenType.INT;
                        case "boolean" -> TokenType.BOOLEAN;
                        case "void" -> TokenType.VOID;
                        case "if" -> TokenType.IF;
                        case "else" -> TokenType.ELSE;
                        case "while" -> TokenType.WHILE;
                        case "return" -> TokenType.RETURN;
                        case "true" -> TokenType.TRUE;
                        case "false" -> TokenType.FALSE;
                        case "new" -> TokenType.NEW;
                        default -> TokenType.IDENTIFIER;
                    };
                    tokens.add(new Token(type, lexeme, line));
                } else {
                    tokens.add(new Token(TokenType.IDENTIFIER, lexeme, line));
                }
                continue;
            }

            // Numeric Literals
            if (Character.isDigit(current)) {
                int start = index;
                while (index < length && Character.isDigit(sourceCode.charAt(index))) {
                    index++;
                }
                String lexeme = sourceCode.substring(start, index);
                tokens.add(new Token(TokenType.NUMBER, lexeme, line));
                continue;
            }

            //Multi-character and Single-character Operators & Punctuation
            char next = (index + 1 < length) ? sourceCode.charAt(index + 1) : '\0';
            
            if (current == '=' && next == '=') { index += 2; tokens.add(new Token(TokenType.EQUALS_EQUALS, "==", line)); continue; }
            if (current == '!' && next == '=') { index += 2; tokens.add(new Token(TokenType.NOT_EQUALS, "!=", line)); continue; }
            if (current == '&' && next == '&') { index += 2; tokens.add(new Token(TokenType.AND, "&&", line)); continue; }
            if (current == '|' && next == '|') { index += 2; tokens.add(new Token(TokenType.OR, "||", line)); continue; }

            // Single characters
            switch (current) {
                case '=' -> { tokens.add(new Token(TokenType.ASSIGN, "=", line)); index++; }
                case '+' -> { tokens.add(new Token(TokenType.PLUS, "+", line)); index++; }
                case '-' -> { tokens.add(new Token(TokenType.MINUS, "-", line)); index++; }
                case '*' -> { tokens.add(new Token(TokenType.MULTIPLY, "*", line)); index++; }
                case '/' -> { tokens.add(new Token(TokenType.DIVIDE, "/", line)); index++; }
                case '<' -> { tokens.add(new Token(TokenType.LESS_THAN, "<", line)); index++; }
                case '>' -> { tokens.add(new Token(TokenType.GREATER_THAN, ">", line)); index++; }
                case '!' -> { tokens.add(new Token(TokenType.NOT, "!", line)); index++; }
                case ';' -> { tokens.add(new Token(TokenType.SEMICOLON, ";", line)); index++; }
                case ',' -> { tokens.add(new Token(TokenType.COMMA, ",", line)); index++; }
                case '.' -> { tokens.add(new Token(TokenType.DOT, ".", line)); index++; }
                case '(' -> { tokens.add(new Token(TokenType.LPAREN, "(", line)); index++; }
                case ')' -> { tokens.add(new Token(TokenType.RPAREN, ")", line)); index++; }
                case '{' -> { tokens.add(new Token(TokenType.LBRACE, "{", line)); index++; }
                case '}' -> { tokens.add(new Token(TokenType.RBRACE, "}", line)); index++; }
                case '[' -> { tokens.add(new Token(TokenType.LBRACKET, "[", line)); index++; }
                case ']' -> { tokens.add(new Token(TokenType.RBRACKET, "]", line)); index++; }
                default -> {
                    throw new RuntimeException("Unexpected character '" + current + "' at line " + line);
                }
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line));
        return tokens;
    }
}