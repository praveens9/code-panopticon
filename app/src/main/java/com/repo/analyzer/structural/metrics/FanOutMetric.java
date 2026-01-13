package com.repo.analyzer.structural.metrics;

import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootField;
import sootup.core.model.SootMethod;
import sootup.core.types.Type;

import java.util.HashSet;
import java.util.Set;

public class FanOutMetric implements MetricCalculator {

    @Override
    public String getName() {
        return "Fan-Out (Coupling)";
    }

    @Override
    public double calculate(SootClass sootClass) {
        Set<String> dependencies = new HashSet<>();

        // 1. Fields Types
        for (SootField field : sootClass.getFields()) {
            addType(dependencies, field.getType());
        }

        // 2. Method Signatures (Params & Return types)
        for (SootMethod method : sootClass.getMethods()) {
            addType(dependencies, method.getReturnType());
            for (Type paramType : method.getParameterTypes()) {
                addType(dependencies, paramType);
            }
            
            // 3. Local Variables (inside methods)
            if (method.isConcrete() && method.getBody() != null) {
                method.getBody().getLocals().forEach(local -> addType(dependencies, local.getType()));
            }
        }
        
        // Remove self-references and java.lang.* noise
        dependencies.remove(sootClass.getName());
        dependencies.remove("void");
        dependencies.remove("int");
        dependencies.remove("boolean");
        dependencies.remove("long");
        dependencies.remove("double");
        
        return dependencies.size();
    }

    private void addType(Set<String> deps, Type type) {
        String typeName = type.toString();
        // Ignore standard library to focus on "Domain Coupling"
        if (!typeName.startsWith("java.lang") && !typeName.startsWith("java.util")) {
            deps.add(typeName);
        }
    }
}
