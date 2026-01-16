package com.repo.analyzer.analyzers;

import com.repo.analyzer.core.AnalyzerConfig;
import com.repo.analyzer.core.FileMetrics;
import com.repo.analyzer.core.LanguageAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal fallback analyzer using regex heuristics.
 * Works on any text file with basic metrics.
 */
public class GenericTextAnalyzer implements LanguageAnalyzer {

    // Patterns to detect functions across languages
    private static final List<Pattern> FUNCTION_PATTERNS = List.of(
            // Python: def foo(
            Pattern.compile("^\\s*(async\\s+)?def\\s+(\\w+)\\s*\\("),
            // JavaScript/TypeScript: function foo( or async function foo(
            Pattern.compile("^\\s*(async\\s+)?function\\s+(\\w+)\\s*\\("),
            // Arrow functions: const foo = ( or const foo = async (
            Pattern.compile("^\\s*(const|let|var)\\s+(\\w+)\\s*=\\s*(async\\s+)?\\([^)]*\\)\\s*=>"),
            // Go: func foo(
            Pattern.compile("^\\s*func\\s+(\\w+)\\s*\\("),
            // Rust: fn foo( or pub fn foo(
            Pattern.compile("^\\s*(pub\\s+)?fn\\s+(\\w+)\\s*[<(]"),
            // Java/C#: public void foo( etc.
            Pattern.compile("^\\s*(public|private|protected|static|async|override)*\\s*(\\w+)\\s+(\\w+)\\s*\\("),
            // Kotlin: fun foo(
            Pattern.compile("^\\s*(suspend\\s+)?fun\\s+(\\w+)\\s*[<(]"),
            // Ruby: def foo
            Pattern.compile("^\\s*def\\s+(\\w+)"));

    // Patterns for branching/complexity keywords
    private static final Pattern BRANCH_PATTERN = Pattern.compile(
            "\\b(if|else|elif|elsif|for|foreach|while|switch|case|catch|except|when|match|&&|\\|\\|)\\b");

    // Patterns for imports
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*(import|from|require|include|use|using)\\b");

    @Override
    public String getLanguageId() {
        return "generic";
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return Set.of("*"); // Matches anything as fallback
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE; // Lowest priority (fallback)
    }

    @Override
    public Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config) {
        try {
            List<String> lines = Files.readAllLines(sourceFile);

            // Count non-blank lines
            int loc = countNonBlankLines(lines);

            // Detect functions
            List<String> functions = detectFunctions(lines);
            int functionCount = functions.size();

            // Estimate complexity from branching keywords
            double complexity = countBranchingKeywords(lines);
            double maxComplexity = estimateMaxComplexity(lines);

            // Count imports
            int fanOut = countImports(lines);

            // Find complex functions (high local branch count)
            List<String> complexFunctions = findComplexFunctions(lines, config.maxComplexity());

            return Optional.of(new FileMetrics(
                    sourceFile.toString(),
                    "generic",
                    loc,
                    functionCount,
                    complexity,
                    maxComplexity,
                    1.0, // Can't calculate cohesion generically
                    fanOut,
                    complexFunctions,
                    Map.of("detectedFunctions", functions)));

        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private int countNonBlankLines(List<String> lines) {
        return (int) lines.stream()
                .filter(line -> !line.trim().isEmpty())
                .count();
    }

    private List<String> detectFunctions(List<String> lines) {
        List<String> functions = new ArrayList<>();

        for (String line : lines) {
            for (Pattern pattern : FUNCTION_PATTERNS) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // Extract function name from the last capturing group that has a word
                    for (int i = matcher.groupCount(); i >= 1; i--) {
                        String group = matcher.group(i);
                        if (group != null && group.matches("\\w+") &&
                                !Set.of("async", "public", "private", "protected", "static",
                                        "override", "suspend", "pub", "const", "let", "var").contains(group)) {
                            functions.add(group);
                            break;
                        }
                    }
                    break;
                }
            }
        }

        return functions;
    }

    private double countBranchingKeywords(List<String> lines) {
        int count = 0;
        for (String line : lines) {
            Matcher matcher = BRANCH_PATTERN.matcher(line);
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }

    private record ComplexityResult(double score, int lineIndex) {
    }

    private ComplexityResult calculateMaxComplexity(List<String> lines) {
        // Simple heuristic: find the function with most branches
        double maxInWindow = 0;
        int maxLineIndex = 0;
        double windowComplexity = 0;
        int windowSize = 50; // Look at 50-line windows

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = BRANCH_PATTERN.matcher(line);
            while (matcher.find()) {
                windowComplexity++;
            }

            // Slide window
            if (i >= windowSize) {
                Matcher oldMatcher = BRANCH_PATTERN.matcher(lines.get(i - windowSize));
                while (oldMatcher.find()) {
                    windowComplexity--;
                }
            }

            if (windowComplexity > maxInWindow) {
                maxInWindow = windowComplexity;
                maxLineIndex = i;
            }
        }

        return new ComplexityResult(maxInWindow, maxLineIndex);
    }

    private double estimateMaxComplexity(List<String> lines) {
        return calculateMaxComplexity(lines).score();
    }

    private int countImports(List<String> lines) {
        return (int) lines.stream()
                .filter(line -> IMPORT_PATTERN.matcher(line).find())
                .count();
    }

    private List<String> findComplexFunctions(List<String> lines, int threshold) {
        ComplexityResult result = calculateMaxComplexity(lines);

        if (result.score() > threshold) {
            // Found complex code, try to find the function name
            String functionName = findFunctionNameBefore(lines, result.lineIndex());
            return List.of(functionName);
        }
        return List.of();
    }

    private String findFunctionNameBefore(List<String> lines, int index) {
        // Scan backwards for up to 100 lines to find a function declaration
        for (int i = index; i >= Math.max(0, index - 100); i--) {
            String line = lines.get(i);
            for (Pattern pattern : FUNCTION_PATTERNS) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    for (int g = matcher.groupCount(); g >= 1; g--) {
                        String group = matcher.group(g);
                        if (group != null && !group.trim().isEmpty()
                                && !Set.of("async", "public", "private", "protected", "static", "final", "override",
                                        "suspend", "pub", "const", "let", "var", "return", "switch", "if", "else",
                                        "new", "throw", "case", "class", "record").contains(group)) {
                            return group + " (approx)";
                        }
                    }
                }
            }
        }
        return "unknown_method_at_line_" + (index + 1);
    }
}
