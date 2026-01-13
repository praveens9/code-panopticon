package com.repo.analyzer.structural;

import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;

import java.util.*;

public class LCOM4Analyzer {

    public record Component(Set<String> methods, int totalInstructions, int totalComplexity) {
    }

    public List<Component> analyze(SootClass sootClass) {
        List<? extends SootMethod> methods = new ArrayList<>(sootClass.getMethods());
        // Filter relevant
        methods.removeIf(m -> !isRelevant(m));

        if (methods.isEmpty())
            return Collections.emptyList();

        // Map for quick metric lookup
        Map<String, SootMethod> methodMap = new HashMap<>();
        methods.forEach(m -> methodMap.put(m.getName(), m));

        // 1. Build Adjacency List (Graph)
        Map<String, Set<String>> graph = new HashMap<>();
        methods.forEach(m -> graph.put(m.getName(), new HashSet<>()));

        Map<String, List<String>> fieldUsers = new HashMap<>();

        for (SootMethod method : methods) {
            if (method.getBody() == null)
                continue;

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
                    if (graph.containsKey(invokedMethod)) {
                        addEdge(graph, method.getName(), invokedMethod);
                    }
                }
            }
        }

        for (List<String> users : fieldUsers.values()) {
            for (int i = 0; i < users.size(); i++) {
                for (int j = i + 1; j < users.size(); j++) {
                    addEdge(graph, users.get(i), users.get(j));
                }
            }
        }

        // Link lambdas to their parents (Heuristic for javac synthetic methods)
        connectLambdas(graph, methodMap.keySet());

        // 2. Find Connected Components
        List<Set<String>> stringComponents = findComponents(graph);

        // 3. Enrich with Substance Metrics
        List<Component> enriched = new ArrayList<>();
        for (Set<String> compMethods : stringComponents) {
            int instrs = 0;
            int complexity = 0;
            for (String mName : compMethods) {
                SootMethod m = methodMap.get(mName);
                if (m != null && m.getBody() != null) {
                    instrs += m.getBody().getStmts().size();
                    complexity += com.repo.analyzer.structural.metrics.ComplexityCalculator.calculate(m);
                }
            }
            enriched.add(new Component(compMethods, instrs, complexity));
        }

        return enriched;
    }

    private void addEdge(Map<String, Set<String>> graph, String a, String b) {
        if (!a.equals(b)) {
            graph.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            graph.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        }
    }

    private List<Set<String>> findComponents(Map<String, Set<String>> graph) {
        List<Set<String>> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                Set<String> component = new HashSet<>();
                bfs(node, graph, visited, component);
                components.add(component);
            }
        }

        components.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return components;
    }

    private void bfs(String startNode, Map<String, Set<String>> graph, Set<String> visited, Set<String> component) {
        Queue<String> queue = new LinkedList<>();
        queue.add(startNode);
        visited.add(startNode);
        component.add(startNode);

        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    component.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    private void connectLambdas(Map<String, Set<String>> graph, Set<String> methodNames) {
        for (String methodName : methodNames) {
            if (methodName.startsWith("lambda$")) {
                // Format: lambda$parentMethod$sequence (e.g., lambda$getTrayInfo$1)
                String[] parts = methodName.split("\\$");
                if (parts.length >= 3) {
                    String parentName = parts[1];
                    // Verify parent exists to avoid linking to non-existent/filtered methods
                    if (graph.containsKey(parentName)) {
                        addEdge(graph, parentName, methodName);
                    }
                }
            }
        }
    }

    private String extractFieldName(String stmtStr) {
        int start = stmtStr.indexOf(": ") + 2;
        int end = stmtStr.indexOf(">");
        if (start > 1 && end > start) {
            return stmtStr.substring(start, end);
        }
        return null;
    }

    private boolean isRelevant(SootMethod method) {
        return method.isConcrete() &&
                !method.isStatic() &&
                !method.getName().equals("<init>") &&
                !method.getName().equals("<clinit>"); // Skip static initializers
    }
}
