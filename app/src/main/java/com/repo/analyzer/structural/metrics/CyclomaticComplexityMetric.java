package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

public class CyclomaticComplexityMetric implements MetricCalculator {

    @Override
    public String getName() {
        return "Total Cyclomatic Complexity";
    }

    @Override
    public double calculate(SootClass sootClass) {
        int totalComplexity = 0;

        for (SootMethod method : sootClass.getMethods()) {
            if (method.isAbstract() || method.isNative()) continue;

            // Filter noise: Standard object methods
            String name = method.getName();
            if (name.equals("equals") || name.equals("hashCode") || name.equals("toString")) {
                continue;
            }

            totalComplexity += ComplexityCalculator.calculate(method);
        }

        return totalComplexity;
    }
}