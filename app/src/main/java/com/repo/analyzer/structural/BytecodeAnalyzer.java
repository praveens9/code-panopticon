package com.repo.analyzer.structural;

import com.repo.analyzer.structural.metrics.CohesionMetric;
import com.repo.analyzer.structural.metrics.ComplexityCalculator;
import com.repo.analyzer.structural.metrics.CyclomaticComplexityMetric;
import com.repo.analyzer.structural.metrics.FanOutMetric;
import com.repo.analyzer.structural.metrics.InstructionCountMetric;
import com.repo.analyzer.structural.metrics.LCOM4Metric;
import com.repo.analyzer.structural.metrics.LinesOfCodeMetric;
import com.repo.analyzer.structural.metrics.AfferentCouplingMetric;
import com.repo.analyzer.structural.metrics.MetricCalculator;
import com.repo.analyzer.structural.graphs.LCOM4GraphGenerator;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.views.View;
import sootup.core.types.ClassType;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaIdentifierFactory;
import sootup.java.core.views.JavaView;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BytecodeAnalyzer {

    public record Result(String className, Map<String, Double> metrics, String verdict, List<String> brainMethodNames,
            List<Set<String>> lcom4Blocks, boolean isDataClass) {
        public double getMetric(String name) {
            return metrics.getOrDefault(name, 0.0);
        }
    }

    private final View view;
    private final List<MetricCalculator> calculators;

    public BytecodeAnalyzer(Path classesPath) {
        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(classesPath.toString());
        this.view = new JavaView(Collections.singletonList(inputLocation));

        this.calculators = List.of(
                new CohesionMetric(),
                new CyclomaticComplexityMetric(),
                // MaxCyclomaticComplexityMetric removed; we calculate it inline to capture
                // method name
                new FanOutMetric(),
                new InstructionCountMetric(),
                new LCOM4Metric(),
                new LinesOfCodeMetric(),
                new AfferentCouplingMetric(view));
    }

    public Optional<Result> analyze(String className) {
        ClassType classType = JavaIdentifierFactory.getInstance().getClassType(className);
        Optional<? extends SootClass> sootClassOpt = view.getClass(classType);

        if (sootClassOpt.isEmpty()) {
            return Optional.empty();
        }

        SootClass sootClass = sootClassOpt.get();
        Map<String, Double> metrics = new HashMap<>();

        for (MetricCalculator calculator : calculators) {
            metrics.put(calculator.getName(), calculator.calculate(sootClass));
        }

        // Inline calculation of Max CC to capture the Method Name and its Instruction
        // Count
        int maxCC = 0;
        long methodCount = 0;

        for (SootMethod method : sootClass.getMethods()) {
            if (method.getName().startsWith("<"))
                continue; // Skip clinit/init for counting
            methodCount++;

            if (method.isAbstract() || method.isNative())
                continue;

            // Filter noise for Max CC check
            String name = method.getName();
            if (name.equals("equals") || name.equals("hashCode") || name.equals("toString")) {
                continue;
            }

            int cc = ComplexityCalculator.calculate(method);
            if (cc > maxCC) {
                maxCC = cc;
            }
        }

        metrics.put("Method Count", (double) methodCount);
        metrics.put("Max Cyclomatic Complexity", (double) maxCC);

        // Calculate Instability: Ce / (Ca + Ce)
        // Ce = Efferent Coupling (Fan-Out), Ca = Afferent Coupling
        double ce = metrics.getOrDefault("Fan-Out (Coupling)", 0.0);
        double ca = metrics.getOrDefault("Afferent Coupling (Ca)", 0.0);
        double instability = (ca + ce > 0) ? ce / (ca + ce) : 0.0;
        metrics.put("Instability", instability);

        String verdict = "OK";
        double totalCC = metrics.getOrDefault("Total Cyclomatic Complexity", 0.0);
        double cohesion = metrics.getOrDefault("Avg Fields per Method", 0.0);
        double lcom4 = metrics.getOrDefault("LCOM4 (Components)", 0.0);
        double fanOut = metrics.getOrDefault("Fan-Out (Coupling)", 0.0);
        double instructions = metrics.getOrDefault("Total Instructions", 0.0);

        if (lcom4 > 1)
            verdict = "SPLIT_CANDIDATE";
        if (fanOut > 30)
            verdict = "HIGH_COUPLING";
        if (instructions > 1000)
            verdict = "BLOATED";

        // Revised Complexity Verdicts (Holistic Approach)
        // High CC is only a "Brain Method" if it also has significant mass
        // (Instructions)
        record MethodScore(String name, int cc) {
        }
        List<MethodScore> scores = new ArrayList<>();

        for (SootMethod method : sootClass.getMethods()) {
            if (method.isAbstract() || method.isNative() || method.getName().startsWith("<"))
                continue;

            // Filter noise
            String name = method.getName();
            if (name.equals("equals") || name.equals("hashCode") || name.equals("toString")
                    || name.equals("canEqual")) {
                continue;
            }

            // Check for Brain Method candidates
            int methodCC = ComplexityCalculator.calculate(method);
            int methodInstr = (method.getBody() != null) ? method.getBody().getStmts().size() : 0;

            if (methodCC > 15 && methodInstr > 50) {
                scores.add(new MethodScore(method.getName(), methodCC));
            }
        }

        // Sort by Complexity Descending (worst offenders first)
        scores.sort((a, b) -> Integer.compare(b.cc, a.cc));

        List<String> brainMethods = scores.stream().map(MethodScore::name).collect(Collectors.toList());

        if (!brainMethods.isEmpty()) {
            verdict = "BRAIN_METHOD";
        } else if (maxCC > 15) {
            verdict = "COMPLEX (Low Risk)";
        }

        if (lcom4 > 4)
            verdict = "TOTAL_MESS";

        // Critical conditions override everything
        if (totalCC > 200 || (methodCount > 50 && cohesion < 0.5))
            verdict = "GOD_CLASS";
        if (fanOut > 50)
            verdict = "GOD_CLASS (Coupling)";

        // Data Class Heuristic: Is this just a DTO/Entity/Record?
        int getterCount = 0;
        int setterCount = 0;
        int boilerCount = 0;

        Set<String> fieldNames = sootClass.getFields().stream()
                .map(f -> f.getName())
                .collect(Collectors.toSet());

        for (SootMethod method : sootClass.getMethods()) {
            String name = method.getName();
            if (name.startsWith("<"))
                continue;
            if (name.equals("equals") || name.equals("hashCode") || name.equals("toString")
                    || name.equals("canEqual")) {
                boilerCount++;
            } else if (name.startsWith("get") || name.startsWith("is")) {
                getterCount++;
            } else if (name.startsWith("set")) {
                setterCount++;
            } else if (fieldNames.contains(name)) {
                // Modern Java Record style accessor (method name == field name)
                getterCount++;
            }
        }

        boolean isRecord = sootClass.getSuperclass().isPresent()
                && sootClass.getSuperclass().get().toString().equals("java.lang.Record");

        // Heuristic: Name-based check since annotation API is unavailable in this environment
        boolean isConfiguration = className.endsWith("Configuration") || className.endsWith("Config");

        boolean isDataClass = isRecord || ((methodCount > 0)
                && (((double) (getterCount + setterCount + boilerCount) / methodCount) > 0.8));

        // LCOM4 Component Analysis
        List<com.repo.analyzer.structural.LCOM4Analyzer.Component> rawComponents = new com.repo.analyzer.structural.LCOM4Analyzer()
                .analyze(sootClass);

        // Prescriptive Intelligence: Only count "Substantial" components
        // A substantial component has > 10 instructions OR > 1 CC
        long substantialCount = rawComponents.stream()
                .filter(c -> c.totalInstructions() > 10 || c.totalComplexity() > 1)
                .count();

        // Orchestrator Detection: High coupling but simple logic
        boolean isOrchestrator = (fanOut > 20) && (maxCC < 10) && (totalCC / methodCount < 5);

        if (substantialCount > 1 && verdict.equals("OK")) {
            verdict = "SPLIT_CANDIDATE";
        }

        if (substantialCount > 4) {
            verdict = "TOTAL_MESS";
        }

        if (isOrchestrator) {
            verdict = (fanOut > 50) ? "GOD_CLASS (Coupling)" : "ORCHESTRATOR";
        }

        if (isConfiguration) {
            verdict = "CONFIGURATION";
        } else if (isDataClass && (verdict.equals("SPLIT_CANDIDATE") || verdict.equals("TOTAL_MESS"))) {
            verdict = "DATA_CLASS";
        }

        // Return only the method names for the Result record (UI expectations)
        List<Set<String>> lcom4Blocks = rawComponents.stream()
                .map(com.repo.analyzer.structural.LCOM4Analyzer.Component::methods)
                .collect(Collectors.toList());

        metrics.put("Substantial Components", (double) substantialCount);

        return Optional.of(new Result(className, metrics, verdict, brainMethods, lcom4Blocks, isDataClass));
    }

    public Optional<String> generateGraph(String className) {

        ClassType classType = JavaIdentifierFactory.getInstance().getClassType(className);

        Optional<? extends SootClass> sootClassOpt = view.getClass(classType);

        if (sootClassOpt.isEmpty()) {

            return Optional.empty();

        }

        return Optional.of(new LCOM4GraphGenerator().generateDot(sootClassOpt.get()));

    }

}
