package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

public class InstructionCountMetric implements MetricCalculator {

    @Override
    public String getName() {
        return "Total Instructions";
    }

    @Override
    public double calculate(SootClass sootClass) {
        long count = 0;
        for (SootMethod method : sootClass.getMethods()) {
            if (method.isConcrete() && method.getBody() != null) {
                count += method.getBody().getStmts().size();
            }
        }
        return count;
    }
}
