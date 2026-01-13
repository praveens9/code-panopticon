package com.repo.analyzer.structural.metrics;

import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootMethod;

public class ComplexityCalculator {

    public static int calculate(SootMethod method) {
        if (method.getBody() == null) return 1;

        int branches = 0;
        
        // 1. Branches and Switches
        for (Stmt stmt : method.getBody().getStmts()) {
            if (stmt instanceof JIfStmt) {
                // if (condition) -> 2 paths (Target + Fallthrough)
                // We count the branch (jump).
                branches++;
            }
            else if (stmt instanceof JSwitchStmt) {
                // switch (x) case A: ... case B: ...
                // Count targets (cases)
                branches += ((JSwitchStmt) stmt).getValues().size();
            }
        }
        
        // 2. Exception Handlers (Traps)
        // Each catch block is an implicit branch (GOTO handler)
        branches += method.getBody().getTraps().size();
        
        return branches + 1; // Base complexity is 1 (the method itself)
    }
}
