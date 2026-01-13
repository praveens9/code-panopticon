package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

public class MaxCyclomaticComplexityMetric implements MetricCalculator {

    @Override
    public String getName() {
        return "Max Cyclomatic Complexity";
    }

    @Override
    public double calculate(SootClass sootClass) {
        int maxComplexity = 0;

        for (SootMethod method : sootClass.getMethods()) {
            if (method.isAbstract() || method.isNative()) continue;
            
            // Filter noise: Standard object methods
            String name = method.getName();
            if (name.equals("equals") || name.equals("hashCode") || name.equals("toString")) {
                continue;
            }

            int cc = ComplexityCalculator.calculate(method);
            if (cc > maxComplexity) {
                maxComplexity = cc;
            }
        }

        return maxComplexity;
    }
}
