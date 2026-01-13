package com.repo.analyzer.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for analyzers.
 * Loaded from panopticon.yaml or uses sensible defaults.
 */
public record AnalyzerConfig(
        /** Complexity threshold for "complex function" detection */
        int maxComplexity,

        /** Maximum lines of code before flagging as bloated */
        int maxLoc,

        /** Maximum fan-out (imports) before flagging high coupling */
        int maxFanOut,

        /** Patterns to exclude from analysis (glob format) */
        Set<String> excludePatterns,

        /** Path to compiled classes (for Java bytecode analysis) */
        Path compiledClassesPath,

        /** Extra language-specific settings */
        Map<String, Object> extra) {

    /** Default configuration with sensible values */
    public static final AnalyzerConfig DEFAULT = new AnalyzerConfig(
            15, // maxComplexity
            500, // maxLoc
            30, // maxFanOut
            Set.of("**/test/**", "**/*Test.*", "**/node_modules/**", "**/vendor/**", "**/__pycache__/**"),
            null, // no compiled classes by default
            Map.of());

    /**
     * Create config with custom complexity threshold.
     */
    public AnalyzerConfig withMaxComplexity(int maxComplexity) {
        return new AnalyzerConfig(maxComplexity, this.maxLoc, this.maxFanOut,
                this.excludePatterns, this.compiledClassesPath, this.extra);
    }

    /**
     * Create config with compiled classes path for Java analysis.
     */
    public AnalyzerConfig withCompiledClassesPath(Path path) {
        return new AnalyzerConfig(this.maxComplexity, this.maxLoc, this.maxFanOut,
                this.excludePatterns, path, this.extra);
    }

    /**
     * Check if a path should be excluded based on patterns.
     */
    public boolean shouldExclude(Path path) {
        String pathStr = path.toString();
        for (String pattern : excludePatterns) {
            if (matchesGlob(pathStr, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGlob(String path, String glob) {
        // Simple glob matching for common patterns
        String regex = glob
                .replace("**", "<<<DOUBLESTAR>>>")
                .replace("*", "[^/]*")
                .replace("<<<DOUBLESTAR>>>", ".*");
        return path.matches(".*" + regex + ".*");
    }
}
