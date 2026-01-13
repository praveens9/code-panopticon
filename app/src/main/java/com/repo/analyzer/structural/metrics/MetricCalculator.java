package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;

public interface MetricCalculator {
    String getName();
    double calculate(SootClass sootClass);
}
