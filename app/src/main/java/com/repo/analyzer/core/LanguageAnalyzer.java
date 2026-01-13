package com.repo.analyzer.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plugin interface for language-specific code analyzers.
 * Each implementation handles one or more programming languages.
 */
public interface LanguageAnalyzer {

    /**
     * Unique identifier for this language (e.g., "java", "python", "javascript").
     */
    String getLanguageId();

    /**
     * File extensions this analyzer handles (e.g., ".py", ".js", ".ts").
     * Extensions should include the leading dot.
     */
    Set<String> getSupportedExtensions();

    /**
     * Check if this analyzer is available at runtime.
     * For example, Python analyzer checks if python3 is installed.
     * 
     * @return true if all required dependencies are available
     */
    boolean isAvailable();

    /**
     * Analyze a single source file and return metrics.
     * 
     * @param sourceFile path to the source file
     * @param config     analyzer configuration
     * @return metrics for the file, or empty if analysis failed
     */
    Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config);

    /**
     * Batch analyze multiple files for efficiency.
     * Default implementation calls analyze() for each file.
     * 
     * @param files  list of files to analyze
     * @param config analyzer configuration
     * @return list of metrics for successfully analyzed files
     */
    default List<FileMetrics> analyzeBatch(List<Path> files, AnalyzerConfig config) {
        return files.stream()
                .map(f -> analyze(f, config))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    /**
     * Priority for this analyzer when multiple analyzers support the same
     * extension.
     * Lower values = higher priority. Default is 100.
     */
    default int getPriority() {
        return 100;
    }
}
