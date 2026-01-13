package com.repo.analyzer.structural.graphs;

import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

import java.util.*;

public class LCOM4GraphGenerator {

    public String generateDot(SootClass sootClass) {
        StringBuilder dot = new StringBuilder();
        dot.append("graph \"").append(sootClass.getName()).append("\" {\n");
        dot.append("  node [shape=box];\n");
        dot.append("  compound=true;\n");

        List<? extends SootMethod> methods = new ArrayList<>(sootClass.getMethods());
        methods.removeIf(m -> !isRelevant(m));

        Map<String, Set<String>> edges = new HashMap<>();
        Map<String, List<String>> fieldUsers = new HashMap<>();

        for (SootMethod method : methods) {
            dot.append("  \"").append(method.getName()).append("\";\n");
        }

        for (SootMethod method : methods) {
            if (method.getBody() == null) continue;

            for (Stmt stmt : method.getBody().getStmts()) {
                String stmtStr = stmt.toString();
                if (stmtStr.contains("this.<")) {
                    String fieldName = extractFieldName(stmtStr);
                    if (fieldName != null) {
                        fieldUsers.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(method.getName());
                    }
                }
                if (stmt.containsInvokeExpr()) {
                    String invokedMethod = stmt.getInvokeExpr().getMethodSignature().getName();
                    if (isMethodInClass(methods, invokedMethod)) {
                        addEdge(edges, method.getName(), invokedMethod);
                    }
                }
            }
        }

        for (Map.Entry<String, List<String>> entry : fieldUsers.entrySet()) {
            String field = entry.getKey();
            dot.append("  \"").append(field).append("\" [shape=ellipse, style=filled, color=lightgrey];\n");
            for (String method : entry.getValue()) {
                dot.append("  \"").append(method).append("\" -- \"").append(field).append("\";\n");
            }
        }

        for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
            String source = entry.getKey();
            for (String target : entry.getValue()) {
                dot.append("  \"").append(source).append("\" -- \"").append(target).append("\" [style=dashed, label=\"calls\"];\n");
            }
        }

        // Link lambdas to their parents (Heuristic)
        for (SootMethod method : methods) {
            String methodName = method.getName();
            if (methodName.startsWith("lambda$")) {
                String[] parts = methodName.split("\\$");
                if (parts.length >= 3) {
                    String parentName = parts[1];
                    if (isMethodInClass(methods, parentName)) {
                        dot.append("  \"").append(parentName).append("\" -- \"").append(methodName).append("\" [style=dashed, color=blue, label=\"lambda\"];\n");
                    }
                }
            }
        }

        dot.append("}\n");
        return dot.toString();
    }

    private void addEdge(Map<String, Set<String>> edges, String a, String b) {
        if (!a.equals(b)) {
            edges.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        }
    }

    private boolean isMethodInClass(List<? extends SootMethod> methods, String name) {
        return methods.stream().anyMatch(m -> m.getName().equals(name));
    }

    private String extractFieldName(String stmtStr) {
        int start = stmtStr.indexOf(": ") + 2;
        int end = stmtStr.indexOf(">");
        if (start > 1 && end > start) {
            String fullField = stmtStr.substring(start, end);
            String[] parts = fullField.split(" ");
            return parts.length > 1 ? parts[1] : fullField;
        }
        return null;
    }

    private boolean isRelevant(SootMethod method) {
        return method.isConcrete() && 
               !method.isStatic() && 
               !method.getName().equals("<init>") && 
               !method.getName().equals("<clinit>");
    }
}
