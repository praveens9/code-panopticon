package com.repo.analyzer.core;

import java.util.List;
import java.util.Map;

/**
 * Language-agnostic metrics for a single file.
 * This is the common output format for all language analyzers.
 */
public record FileMetrics(
        /** Full path to the analyzed file */
        String filePath,

        /** Language identifier (e.g., "java", "python", "generic") */
        String language,

        /** Lines of code (non-blank, non-comment) */
        int loc,

        /** Number of functions/methods in the file */
        int functionCount,

        /** Total cyclomatic complexity across all functions */
        double totalComplexity,

        /** Maximum complexity of any single function */
        double maxComplexity,

        /** Cohesion score (1.0 = fully cohesive, 0.0 = no cohesion) */
        double cohesion,

        /** Number of imports/dependencies (fan-out) */
        int fanOut,

        /** Names of functions exceeding complexity threshold */
        List<String> complexFunctions,

        /** Language-specific extra metrics */
        Map<String, Object> extra) {

    /**
     * Create minimal metrics with defaults for optional fields.
     */
    public static FileMetrics basic(String filePath, String language, int loc, int functionCount) {
        return new FileMetrics(
                filePath, language, loc, functionCount,
                0.0, 0.0, 1.0, 0,
                List.of(), Map.of());
    }

    /**
     * Get a typed extra metric value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key, T defaultValue) {
        Object value = extra.get(key);
        if (value == null)
            return defaultValue;
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
}
