package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

public class LinesOfCodeMetric implements MetricCalculator {

    @Override
    public String getName() {
        return "Lines of Code";
    }

    @Override
    public double calculate(SootClass sootClass) {
        int totalLines = 0;

        for (SootMethod method : sootClass.getMethods()) {
            // Skip abstract and native methods - they don't have bodies
            if (!method.isAbstract() && !method.isNative() && method.getBody() != null) {
                // Count statements as proxy for LOC
                totalLines += method.getBody().getStmts().size();
            }
        }

        return totalLines;
    }
}
