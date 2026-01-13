package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;

public class LCOM4Metric implements MetricCalculator {

    @Override
    public String getName() {
        return "LCOM4 (Components)";
    }

    @Override
    public double calculate(SootClass sootClass) {
        return new com.repo.analyzer.structural.LCOM4Analyzer().analyze(sootClass).size();
    }
}
