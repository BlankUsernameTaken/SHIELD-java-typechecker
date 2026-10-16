package com.shield;

import com.shield.checker.TypeChecker;
import com.shield.error.ErrorReporter;
import com.shield.parser.ShieldParser;
import com.shield.symbol.SymbolTable;

public class Main {
    public static void main(String[] args) {
        String testProgram = """
            class Person {
                int age;
                boolean active;
                
                void setAge(int newAge) {
                    age = newAge;
                }
            }
            
            class Student extends Person {
                int studentId;
            }
            
            int x = 10;
            boolean flag = true;
            Person p = new Student();
            
            // Valid call
            p.setAge(20);
            
            // Intentional semantic errors
            int badAssign = flag;
            int badAdd = x + flag;
            p.setAge(flag);
        """;

        System.out.println("--- Parsing MiniJava Source Code ---");
        ShieldParser.Program program = ShieldParser.parse(testProgram);

        SymbolTable symbolTable = new SymbolTable();
        ErrorReporter errorReporter = new ErrorReporter();
        TypeChecker typeChecker = new TypeChecker(symbolTable, errorReporter);

        System.out.println("--- Running Type Checker ---");
        boolean result = typeChecker.check(program);

        System.out.println("\n--- Diagnostic Results ---");
        errorReporter.printSummary();
    }
}