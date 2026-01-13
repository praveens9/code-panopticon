package com.repo.analyzer.analyzers;

import com.repo.analyzer.core.AnalyzerConfig;
import com.repo.analyzer.core.FileMetrics;
import com.repo.analyzer.core.LanguageAnalyzer;
import com.repo.analyzer.structural.BytecodeAnalyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Java bytecode analyzer that wraps the existing BytecodeAnalyzer.
 * Requires compiled .class files to be available.
 */
public class JavaBytecodeAnalyzer implements LanguageAnalyzer {

    private static final Set<String> EXTENSIONS = Set.of(".java");

    private BytecodeAnalyzer bytecodeAnalyzer;
    private Path compiledClassesPath;

    public JavaBytecodeAnalyzer() {
        // Will be initialized when config is provided
    }

    /**
     * Initialize with path to compiled classes.
     */
    public void initialize(Path compiledClassesPath) {
        this.compiledClassesPath = compiledClassesPath;
        if (Files.exists(compiledClassesPath)) {
            this.bytecodeAnalyzer = new BytecodeAnalyzer(compiledClassesPath);
        }
    }

    @Override
    public String getLanguageId() {
        return "java";
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean isAvailable() {
        return bytecodeAnalyzer != null;
    }

    @Override
    public int getPriority() {
        return 5; // Highest priority for Java files when bytecode is available
    }

    @Override
    public Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        // Convert source path to class name
        String className = convertPathToClassName(sourceFile, config);
        if (className == null) {
            return Optional.empty();
        }

        // Use existing bytecode analyzer
        Optional<BytecodeAnalyzer.Result> result = bytecodeAnalyzer.analyze(className);

        return result.map(r -> toFileMetrics(r, sourceFile));
    }

    private String convertPathToClassName(Path sourceFile, AnalyzerConfig config) {
        String pathStr = sourceFile.toString();

        // Try to extract class name from common source layouts
        String[] patterns = { "src/main/java/", "src/", "java/" };

        for (String pattern : patterns) {
            int idx = pathStr.indexOf(pattern);
            if (idx >= 0) {
                String remainder = pathStr.substring(idx + pattern.length());
                return remainder
                        .replace(".java", "")
                        .replace("/", ".")
                        .replace("\\", ".");
            }
        }

        // Fallback: just use the filename
        String filename = sourceFile.getFileName().toString();
        return filename.replace(".java", "");
    }

    private FileMetrics toFileMetrics(BytecodeAnalyzer.Result result, Path sourceFile) {
        Map<String, Double> metrics = result.metrics();

        return new FileMetrics(
                sourceFile.toString(),
                "java",
                (int) metrics.getOrDefault("Lines of Code", 0.0).doubleValue(),
                (int) metrics.getOrDefault("Method Count", 0.0).doubleValue(),
                metrics.getOrDefault("Total Cyclomatic Complexity", 0.0),
                metrics.getOrDefault("Max Cyclomatic Complexity", 0.0),
                calculateCohesion(metrics),
                (int) metrics.getOrDefault("Fan-Out (Coupling)", 0.0).doubleValue(),
                result.brainMethodNames(),
                buildExtraMetrics(metrics, result));
    }

    private double calculateCohesion(Map<String, Double> metrics) {
        double lcom4 = metrics.getOrDefault("LCOM4 (Components)", 1.0);
        // Invert LCOM4: 1 component = 1.0 cohesion, higher components = lower cohesion
        return lcom4 > 0 ? 1.0 / lcom4 : 1.0;
    }

    private Map<String, Object> buildExtraMetrics(Map<String, Double> metrics, BytecodeAnalyzer.Result result) {
        Map<String, Object> extra = new HashMap<>();

        extra.put("lcom4", metrics.getOrDefault("LCOM4 (Components)", 1.0));
        extra.put("afferentCoupling", metrics.getOrDefault("Afferent Coupling (Ca)", 0.0));
        extra.put("instability", metrics.getOrDefault("Instability", 0.0));
        extra.put("verdict", result.verdict());
        extra.put("isDataClass", result.isDataClass());
        extra.put("lcom4Blocks", result.lcom4Blocks());

        return extra;
    }

    /**
     * Create an analyzer with initialized bytecode path.
     */
    public static JavaBytecodeAnalyzer create(Path compiledClassesPath) {
        JavaBytecodeAnalyzer analyzer = new JavaBytecodeAnalyzer();
        analyzer.initialize(compiledClassesPath);
        return analyzer;
    }
}
