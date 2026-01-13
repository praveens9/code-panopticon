package com.repo.analyzer.structural.metrics;

import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CohesionMetric implements MetricCalculator {

    @Override
    public String getName() {
        return "Avg Fields per Method";
    }

    @Override
    public double calculate(SootClass sootClass) {
        Map<String, Set<String>> methodToFields = new HashMap<>();

        for (SootMethod method : sootClass.getMethods()) {
            if (shouldSkip(method)) continue;

            Set<String> fields = new HashSet<>();
            if (method.getBody() != null) {
                for (Stmt stmt : method.getBody().getStmts()) {
                    String stmtStr = stmt.toString();
                    if (stmtStr.contains("this.<")) {
                        int start = stmtStr.indexOf(": ") + 2;
                        int end = stmtStr.indexOf(">");
                        if (start > 1 && end > start) {
                            fields.add(stmtStr.substring(start, end));
                        }
                    }
                }
            }
            methodToFields.put(method.getName(), fields);
        }

        return methodToFields.values().stream()
                .mapToInt(Set::size)
                .average()
                .orElse(0.0);
    }

    private boolean shouldSkip(SootMethod method) {
        return method.isAbstract() || method.isNative() || method.isStatic() || method.getName().equals("<init>");
    }
}
