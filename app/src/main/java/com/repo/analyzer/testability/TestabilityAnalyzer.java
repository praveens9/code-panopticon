package com.repo.analyzer.testability;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Analyzes testability of source files.
 * Inspired by Michael Feathers' "Working Effectively with Legacy Code".
 * 
 * Detects:
 * - Whether a source file has an associated test file
 * - Testability score based on structure and dependencies
 * - Identifies untested hotspots (high-risk files without tests)
 */
public class TestabilityAnalyzer {

    // Common test directory patterns
    private static final List<String> TEST_DIRS = List.of(
            "test", "tests", "__tests__", "spec", "specs", "unittest");

    // Common test file naming patterns
    private static final List<String> TEST_PREFIXES = List.of("test_", "Test");
    private static final List<String> TEST_SUFFIXES = List.of("Test", "_test", "Spec", "_spec", ".test", ".spec");

    private final Path projectRoot;
    private final Set<String> testFileNames;
    private final Map<String, Path> testFileMap;

    public TestabilityAnalyzer(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.testFileNames = new HashSet<>();
        this.testFileMap = new HashMap<>();
        indexTestFiles();
    }

    /**
     * Index all test files in the project for fast lookup.
     */
    private void indexTestFiles() {
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(this::isLikelyTestFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        testFileNames.add(fileName.toLowerCase());
                        // Map the source file name pattern to the test file path
                        String sourcePattern = extractSourcePattern(fileName);
                        if (sourcePattern != null) {
                            testFileMap.put(sourcePattern.toLowerCase(), path);
                        }
                    });
        } catch (IOException e) {
            // Silently handle - test discovery is best-effort
        }
    }

    /**
     * Check if a path is likely a test file based on directory and name patterns.
     */
    private boolean isLikelyTestFile(Path path) {
        String pathStr = path.toString().toLowerCase();
        String fileName = path.getFileName().toString();

        // Check if in a test directory
        boolean inTestDir = TEST_DIRS.stream()
                .anyMatch(dir -> pathStr.contains("/" + dir + "/") || pathStr.contains("\\" + dir + "\\"));

        // Check naming patterns
        boolean hasTestPrefix = TEST_PREFIXES.stream().anyMatch(fileName::startsWith);
        boolean hasTestSuffix = TEST_SUFFIXES.stream().anyMatch(suffix -> {
            int extIdx = fileName.lastIndexOf('.');
            String nameWithoutExt = extIdx > 0 ? fileName.substring(0, extIdx) : fileName;
            return nameWithoutExt.endsWith(suffix);
        });

        return inTestDir || hasTestPrefix || hasTestSuffix;
    }

    /**
     * Extract the source file pattern from a test file name.
     * E.g., "FooTest.java" -> "Foo", "test_bar.py" -> "bar"
     */
    private String extractSourcePattern(String testFileName) {
        int extIdx = testFileName.lastIndexOf('.');
        String nameWithoutExt = extIdx > 0 ? testFileName.substring(0, extIdx) : testFileName;
        String ext = extIdx > 0 ? testFileName.substring(extIdx) : "";

        // Remove test suffixes
        for (String suffix : TEST_SUFFIXES) {
            if (nameWithoutExt.endsWith(suffix)) {
                return nameWithoutExt.substring(0, nameWithoutExt.length() - suffix.length()) + ext;
            }
        }

        // Remove test prefixes
        for (String prefix : TEST_PREFIXES) {
            if (nameWithoutExt.startsWith(prefix)) {
                return nameWithoutExt.substring(prefix.length()) + ext;
            }
        }

        return null;
    }

    /**
     * Find the test file for a given source file.
     * 
     * @param sourceFilePath The relative or absolute path to the source file
     * @return Optional containing the test file path if found
     */
    public Optional<Path> findTestFile(String sourceFilePath) {
        Path sourcePath = Path.of(sourceFilePath);
        String sourceFileName = sourcePath.getFileName().toString();
        int extIdx = sourceFileName.lastIndexOf('.');
        String baseName = extIdx > 0 ? sourceFileName.substring(0, extIdx) : sourceFileName;
        String ext = extIdx > 0 ? sourceFileName.substring(extIdx) : "";

        // Try common test file patterns
        List<String> candidateNames = List.of(
                baseName + "Test" + ext, // FooTest.java
                baseName + "_test" + ext, // foo_test.py
                "Test" + baseName + ext, // TestFoo.java
                "test_" + baseName + ext, // test_foo.py
                baseName + "Spec" + ext, // FooSpec.js
                baseName + ".test" + ext, // foo.test.js
                baseName + ".spec" + ext // foo.spec.ts
        );

        for (String candidate : candidateNames) {
            if (testFileMap.containsKey(candidate.toLowerCase())) {
                return Optional.of(testFileMap.get(candidate.toLowerCase()));
            }
        }

        // Also check if there's a direct match in test file names
        for (String candidate : candidateNames) {
            if (testFileNames.contains(candidate.toLowerCase())) {
                return Optional.of(Path.of(candidate)); // Return pattern for display
            }
        }

        return Optional.empty();
    }

    /**
     * Check if a source file has an associated test.
     */
    public boolean hasTest(String sourceFilePath) {
        return findTestFile(sourceFilePath).isPresent();
    }

    /**
     * Calculate testability score (0-100).
     * 
     * Factors:
     * - Has test file: +40 points
     * - Low fan-out (< 5 dependencies): +20 points
     * - High cohesion (LCOM4 <= 2): +20 points
     * - Low complexity (totalCC < 20): +20 points
     */
    public int calculateTestabilityScore(
            String sourceFilePath,
            double fanOut,
            double lcom4,
            double totalCC,
            boolean hasTestFile) {
        int score = 0;

        // Test coverage (+40)
        if (hasTestFile) {
            score += 40;
        }

        // Low fan-out = easier to mock (+20)
        if (fanOut < 5) {
            score += 20;
        } else if (fanOut < 10) {
            score += 10;
        }

        // High cohesion = focused class (+20)
        if (lcom4 <= 1.0) {
            score += 20;
        } else if (lcom4 <= 2.0) {
            score += 10;
        }

        // Low complexity = easier to understand (+20)
        if (totalCC < 10) {
            score += 20;
        } else if (totalCC < 20) {
            score += 10;
        }

        return score;
    }

    /**
     * Determine if a file is an "untested hotspot".
     * 
     * Criteria:
     * - No associated test file
     * - Risk score > 10 (significant risk)
     * - Churn > 5 (actively changed)
     */
    public boolean isUntestedHotspot(
            boolean hasTestFile,
            double riskScore,
            int churn) {
        return !hasTestFile && riskScore > 10.0 && churn > 5;
    }

    /**
     * Get statistics about test coverage in the project.
     */
    public int getTestFileCount() {
        return testFileNames.size();
    }
}
