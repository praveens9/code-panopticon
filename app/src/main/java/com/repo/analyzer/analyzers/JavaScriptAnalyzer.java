package com.repo.analyzer.analyzers;

import com.repo.analyzer.core.AnalyzerConfig;
import com.repo.analyzer.core.FileMetrics;
import com.repo.analyzer.core.LanguageAnalyzer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript/TypeScript analyzer.
 * Uses ESLint if available, otherwise falls back to regex-based analysis.
 */
public class JavaScriptAnalyzer implements LanguageAnalyzer {

    private static final Set<String> EXTENSIONS = Set.of(".js", ".jsx", ".ts", ".tsx", ".mjs", ".cjs");

    private Boolean eslintAvailable;

    @Override
    public String getLanguageId() {
        return "javascript";
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available (falls back to regex)
    }

    @Override
    public int getPriority() {
        return 10; // High priority for JS/TS files
    }

    @Override
    public Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config) {
        try {
            // Try ESLint first if available
            if (isEslintAvailable()) {
                Optional<FileMetrics> result = analyzeWithEslint(sourceFile);
                if (result.isPresent()) {
                    return result;
                }
            }

            // Fallback to regex-based analysis
            return analyzeWithRegex(sourceFile, config);

        } catch (Exception e) {
            System.err.println("JavaScript analyzer error for " + sourceFile + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isEslintAvailable() {
        if (eslintAvailable == null) {
            try {
                ProcessBuilder pb = new ProcessBuilder("npx", "eslint", "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                eslintAvailable = finished && process.exitValue() == 0;
            } catch (Exception e) {
                eslintAvailable = false;
            }
        }
        return eslintAvailable;
    }

    private Optional<FileMetrics> analyzeWithEslint(Path sourceFile) {
        // For now, use regex-based analysis as ESLint requires project config
        // TODO: Add ESLint support with complexity rule
        return Optional.empty();
    }

    private Optional<FileMetrics> analyzeWithRegex(Path sourceFile, AnalyzerConfig config) throws IOException {
        List<String> lines = Files.readAllLines(sourceFile);

        // Count non-blank, non-comment lines
        int loc = countLoc(lines);

        // Detect functions
        List<FunctionInfo> functions = detectFunctions(lines);
        int functionCount = functions.size();

        // Calculate complexity
        double totalComplexity = functions.stream().mapToDouble(f -> f.complexity).sum();
        double maxComplexity = functions.stream().mapToDouble(f -> f.complexity).max().orElse(0);

        // Count imports
        int fanOut = countImports(lines);

        // Find complex functions
        List<String> complexFunctions = functions.stream()
                .filter(f -> f.complexity > config.maxComplexity())
                .map(f -> f.name)
                .toList();

        return Optional.of(new FileMetrics(
                sourceFile.toString(),
                "javascript",
                loc,
                functionCount,
                totalComplexity,
                maxComplexity,
                1.0, // Can't easily calculate cohesion in JS
                fanOut,
                complexFunctions,
                Map.of()));
    }

    private int countLoc(List<String> lines) {
        boolean inBlockComment = false;
        int count = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }

            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                count++;
            }
        }

        return count;
    }

    private record FunctionInfo(String name, int complexity, int startLine) {
    }

    private List<FunctionInfo> detectFunctions(List<String> lines) {
        List<FunctionInfo> functions = new ArrayList<>();

        // Patterns for function detection
        Pattern funcDecl = Pattern.compile("function\\s+(\\w+)\\s*\\(");
        Pattern arrowFunc = Pattern.compile("(const|let|var)\\s+(\\w+)\\s*=\\s*(async\\s+)?\\([^)]*\\)\\s*=>");
        Pattern methodDecl = Pattern.compile("(async\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*\\{");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String funcName = null;

            Matcher m = funcDecl.matcher(line);
            if (m.find()) {
                funcName = m.group(1);
            }

            if (funcName == null) {
                m = arrowFunc.matcher(line);
                if (m.find()) {
                    funcName = m.group(2);
                }
            }

            if (funcName == null) {
                m = methodDecl.matcher(line);
                if (m.find()) {
                    String name = m.group(2);
                    if (!Set.of("if", "for", "while", "switch", "catch", "with").contains(name)) {
                        funcName = name;
                    }
                }
            }

            if (funcName != null) {
                // Estimate complexity by counting branches in next ~50 lines
                int complexity = estimateFunctionComplexity(lines, i);
                functions.add(new FunctionInfo(funcName, complexity, i + 1));
            }
        }

        return functions;
    }

    private int estimateFunctionComplexity(List<String> lines, int startLine) {
        int complexity = 1; // Base complexity
        int braceCount = 0;
        boolean started = false;

        Pattern branchPattern = Pattern.compile("\\b(if|else|for|while|switch|case|catch|\\?|&&|\\|\\|)\\b");

        for (int i = startLine; i < Math.min(lines.size(), startLine + 100); i++) {
            String line = lines.get(i);

            // Track braces to know when function ends
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    started = true;
                } else if (c == '}') {
                    braceCount--;
                    if (started && braceCount <= 0) {
                        return complexity;
                    }
                }
            }

            // Count branches
            Matcher m = branchPattern.matcher(line);
            while (m.find()) {
                String match = m.group(1);
                if (match.equals("else") && line.contains("else if")) {
                    continue; // Count else-if as one
                }
                complexity++;
            }
        }

        return complexity;
    }

    private int countImports(List<String> lines) {
        Pattern importPattern = Pattern.compile("^\\s*(import|require)\\s*[({'\"]");
        int count = 0;

        for (String line : lines) {
            if (importPattern.matcher(line).find()) {
                count++;
            }
        }

        return count;
    }
}
