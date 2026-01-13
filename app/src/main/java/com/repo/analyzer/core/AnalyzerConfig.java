package com.repo.analyzer.core;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Configuration for Code Panopticon analysis.
 * Loaded from panopticon.yaml in project root or uses sensible defaults.
 */
public class AnalyzerConfig {

    // Threshold defaults
    private int totalMessChurn = 20;
    private int totalMessComplexity = 50;
    private int brainMethodMaxCC = 15;
    private int splitCandidateLcom4 = 3;
    private int bloatedLoc = 500;
    private int godClassComplexity = 100;
    private int godClassLcom4 = 2;

    // Weight defaults
    private double weightChurn = 1.0;
    private double weightComplexity = 1.0;
    private double weightCoupling = 0.1;

    // Exclusion patterns
    private Set<String> exclusions = Set.of(
            "**/test/**", "**/tests/**", "**/*Test.java", "**/*_test.py",
            "**/node_modules/**", "**/vendor/**", "**/__pycache__/**");

    // Treemap settings
    private int treemapMaxFiles = 100;
    private boolean treemapGroupByFolder = false;
    private int treemapMinLabelWidth = 60;
    private int treemapMinLabelHeight = 25;

    // Legacy fields for backward compatibility
    private int maxComplexity = 15;
    private int maxLoc = 500;
    private int maxFanOut = 30;
    private Path compiledClassesPath = null;

    /**
     * Load configuration from YAML file or return defaults.
     */
    public static AnalyzerConfig load(Path projectRoot) {
        AnalyzerConfig config = new AnalyzerConfig();
        Path configFile = projectRoot.resolve("panopticon.yaml");

        if (Files.exists(configFile)) {
            try (InputStream is = Files.newInputStream(configFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(is);
                if (data != null) {
                    config.parseYaml(data);
                }
                System.out.println("Loaded configuration from: " + configFile);
            } catch (IOException e) {
                System.err.println("Warning: Could not read config file, using defaults: " + e.getMessage());
            }
        }
        return config;
    }

    /**
     * Default configuration.
     */
    public static AnalyzerConfig defaults() {
        return new AnalyzerConfig();
    }

    @SuppressWarnings("unchecked")
    private void parseYaml(Map<String, Object> data) {
        // Parse thresholds
        if (data.containsKey("thresholds")) {
            Map<String, Object> thresholds = (Map<String, Object>) data.get("thresholds");

            if (thresholds.containsKey("total_mess")) {
                Map<String, Object> tm = (Map<String, Object>) thresholds.get("total_mess");
                totalMessChurn = getInt(tm, "churn", totalMessChurn);
                totalMessComplexity = getInt(tm, "complexity", totalMessComplexity);
            }
            if (thresholds.containsKey("brain_method")) {
                Map<String, Object> bm = (Map<String, Object>) thresholds.get("brain_method");
                brainMethodMaxCC = getInt(bm, "max_cc", brainMethodMaxCC);
            }
            if (thresholds.containsKey("split_candidate")) {
                Map<String, Object> sc = (Map<String, Object>) thresholds.get("split_candidate");
                splitCandidateLcom4 = getInt(sc, "lcom4", splitCandidateLcom4);
            }
            if (thresholds.containsKey("bloated")) {
                Map<String, Object> bl = (Map<String, Object>) thresholds.get("bloated");
                bloatedLoc = getInt(bl, "loc", bloatedLoc);
            }
            if (thresholds.containsKey("god_class")) {
                Map<String, Object> gc = (Map<String, Object>) thresholds.get("god_class");
                godClassComplexity = getInt(gc, "complexity", godClassComplexity);
                godClassLcom4 = getInt(gc, "lcom4", godClassLcom4);
            }
        }

        // Parse weights
        if (data.containsKey("weights")) {
            Map<String, Object> weights = (Map<String, Object>) data.get("weights");
            weightChurn = getDouble(weights, "churn", weightChurn);
            weightComplexity = getDouble(weights, "complexity", weightComplexity);
            weightCoupling = getDouble(weights, "coupling", weightCoupling);
        }

        // Parse exclusions
        if (data.containsKey("exclusions")) {
            List<String> excList = (List<String>) data.get("exclusions");
            if (excList != null && !excList.isEmpty()) {
                exclusions = new HashSet<>(excList);
            }
        }

        // Parse treemap settings
        if (data.containsKey("treemap")) {
            Map<String, Object> tm = (Map<String, Object>) data.get("treemap");
            treemapMaxFiles = getInt(tm, "max_files", treemapMaxFiles);
            treemapGroupByFolder = getBool(tm, "group_by_folder", treemapGroupByFolder);
            treemapMinLabelWidth = getInt(tm, "min_label_width", treemapMinLabelWidth);
            treemapMinLabelHeight = getInt(tm, "min_label_height", treemapMinLabelHeight);
        }

        // Update legacy fields
        maxComplexity = brainMethodMaxCC;
        maxLoc = bloatedLoc;
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number)
            return ((Number) val).intValue();
        return defaultVal;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number)
            return ((Number) val).doubleValue();
        return defaultVal;
    }

    private boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean)
            return (Boolean) val;
        return defaultVal;
    }

    // === Getters ===

    // Thresholds
    public int getTotalMessChurn() {
        return totalMessChurn;
    }

    public int getTotalMessComplexity() {
        return totalMessComplexity;
    }

    public int getBrainMethodMaxCC() {
        return brainMethodMaxCC;
    }

    public int getSplitCandidateLcom4() {
        return splitCandidateLcom4;
    }

    public int getBloatedLoc() {
        return bloatedLoc;
    }

    public int getGodClassComplexity() {
        return godClassComplexity;
    }

    public int getGodClassLcom4() {
        return godClassLcom4;
    }

    // Weights
    public double getWeightChurn() {
        return weightChurn;
    }

    public double getWeightComplexity() {
        return weightComplexity;
    }

    public double getWeightCoupling() {
        return weightCoupling;
    }

    // Exclusions
    public Set<String> getExclusions() {
        return exclusions;
    }

    // Treemap
    public int getTreemapMaxFiles() {
        return treemapMaxFiles;
    }

    public boolean isTreemapGroupByFolder() {
        return treemapGroupByFolder;
    }

    public int getTreemapMinLabelWidth() {
        return treemapMinLabelWidth;
    }

    public int getTreemapMinLabelHeight() {
        return treemapMinLabelHeight;
    }

    // Legacy getters for backward compatibility
    public int maxComplexity() {
        return maxComplexity;
    }

    public int maxLoc() {
        return maxLoc;
    }

    public int maxFanOut() {
        return maxFanOut;
    }

    public Set<String> excludePatterns() {
        return exclusions;
    }

    public Path compiledClassesPath() {
        return compiledClassesPath;
    }

    public boolean shouldExclude(Path path) {
        String pathStr = path.toString();
        for (String pattern : exclusions) {
            if (matchesGlob(pathStr, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGlob(String path, String glob) {
        String regex = glob
                .replace("**", "<<<DOUBLESTAR>>>")
                .replace("*", "[^/]*")
                .replace("<<<DOUBLESTAR>>>", ".*");
        return path.matches(".*" + regex + ".*");
    }

    public AnalyzerConfig withCompiledClassesPath(Path path) {
        this.compiledClassesPath = path;
        return this;
    }
}
