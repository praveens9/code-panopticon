package com.repo.analyzer.core;

import java.nio.file.Path;
import java.util.*;

/**
 * Registry for language analyzers.
 * Routes files to appropriate analyzers based on extension.
 */
public class AnalyzerRegistry {

    private final List<LanguageAnalyzer> analyzers;
    private final Map<String, LanguageAnalyzer> extensionMap;
    private final LanguageAnalyzer fallbackAnalyzer;

    public AnalyzerRegistry(List<LanguageAnalyzer> analyzers, LanguageAnalyzer fallbackAnalyzer) {
        this.analyzers = new ArrayList<>(analyzers);
        this.fallbackAnalyzer = fallbackAnalyzer;
        this.extensionMap = buildExtensionMap();
    }

    private Map<String, LanguageAnalyzer> buildExtensionMap() {
        Map<String, LanguageAnalyzer> map = new HashMap<>();

        // Sort by priority (lower = higher priority)
        List<LanguageAnalyzer> sorted = new ArrayList<>(analyzers);
        sorted.sort(Comparator.comparingInt(LanguageAnalyzer::getPriority));

        // Map extensions to analyzers (first available wins for each extension)
        for (LanguageAnalyzer analyzer : sorted) {
            if (!analyzer.isAvailable()) {
                System.out.println("  [SKIP] " + analyzer.getLanguageId() + " analyzer not available");
                continue;
            }

            for (String ext : analyzer.getSupportedExtensions()) {
                if (!map.containsKey(ext)) {
                    map.put(ext, analyzer);
                }
            }
        }

        return map;
    }

    /**
     * Get the appropriate analyzer for a file.
     */
    public LanguageAnalyzer getAnalyzer(Path file) {
        String ext = getExtension(file);
        return extensionMap.getOrDefault(ext, fallbackAnalyzer);
    }

    /**
     * Get all registered and available analyzers.
     */
    public List<LanguageAnalyzer> getAvailableAnalyzers() {
        return analyzers.stream()
                .filter(LanguageAnalyzer::isAvailable)
                .toList();
    }

    /**
     * Check if a specific language is supported.
     */
    public boolean supportsLanguage(String languageId) {
        return analyzers.stream()
                .filter(LanguageAnalyzer::isAvailable)
                .anyMatch(a -> a.getLanguageId().equals(languageId));
    }

    /**
     * Get all supported extensions.
     */
    public Set<String> getSupportedExtensions() {
        return extensionMap.keySet();
    }

    /**
     * Analyze a file using the appropriate analyzer.
     */
    public Optional<FileMetrics> analyze(Path file, AnalyzerConfig config) {
        LanguageAnalyzer analyzer = getAnalyzer(file);
        return analyzer.analyze(file, config);
    }

    /**
     * Print summary of available analyzers.
     */
    public void printSummary() {
        System.out.println("Available analyzers:");
        for (LanguageAnalyzer analyzer : getAvailableAnalyzers()) {
            System.out.printf("  [%s] %s%n", analyzer.getLanguageId(),
                    String.join(", ", analyzer.getSupportedExtensions()));
        }
        System.out.printf("  [fallback] %s (all other files)%n", fallbackAnalyzer.getLanguageId());
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }
}
