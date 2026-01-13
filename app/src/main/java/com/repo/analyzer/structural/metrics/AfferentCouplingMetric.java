package com.repo.analyzer.structural.metrics;

import sootup.core.model.SootClass;
import sootup.core.types.ClassType;
import sootup.core.views.View;

import java.util.HashSet;
import java.util.Set;

/**
 * Calculates Afferent Coupling (Ca) - the number of classes that depend on this
 * class.
 * This requires scanning all classes in the view to find dependencies.
 */
public class AfferentCouplingMetric implements MetricCalculator {

    private final View view;

    public AfferentCouplingMetric(View view) {
        this.view = view;
    }

    @Override
    public String getName() {
        return "Afferent Coupling (Ca)";
    }

    @Override
    public double calculate(SootClass targetClass) {
        Set<String> dependents = new HashSet<>();
        ClassType targetType = targetClass.getType();

        // Scan all classes to find those that depend on targetClass
        for (SootClass sootClass : view.getClasses()) {
            if (sootClass.equals(targetClass))
                continue;

            // Check if this class references the target class
            Set<ClassType> referencedTypes = new HashSet<>();
            sootClass.getMethods().forEach(method -> {
                // Skip abstract and native methods - they don't have bodies
                if (!method.isAbstract() && !method.isNative() && method.getBody() != null) {
                    method.getBody().getStmts().forEach(stmt -> {
                        // Extract types from statement
                        stmt.getUses().forEach(value -> {
                            if (value.getType() instanceof ClassType) {
                                referencedTypes.add((ClassType) value.getType());
                            }
                        });
                    });
                }
            });

            if (referencedTypes.contains(targetType)) {
                dependents.add(sootClass.getType().getClassName());
            }
        }

        return dependents.size();
    }
}
