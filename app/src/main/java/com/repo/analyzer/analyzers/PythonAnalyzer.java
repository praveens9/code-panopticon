package com.repo.analyzer.analyzers;

import com.repo.analyzer.core.AnalyzerConfig;
import com.repo.analyzer.core.FileMetrics;
import com.repo.analyzer.core.LanguageAnalyzer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Python analyzer that shells out to python_analyzer.py script.
 */
public class PythonAnalyzer implements LanguageAnalyzer {

    private static final Set<String> EXTENSIONS = Set.of(".py");
    private static final String SCRIPT_NAME = "python_analyzer.py";

    private final Path scriptPath;
    private Boolean pythonAvailable;

    public PythonAnalyzer() {
        // Look for script in multiple locations
        this.scriptPath = findScript();
    }

    private Path findScript() {
        // Check relative paths first
        List<Path> candidates = List.of(
                Path.of("analyzers", SCRIPT_NAME),
                Path.of("..", "analyzers", SCRIPT_NAME),
                Path.of(System.getProperty("user.dir"), "analyzers", SCRIPT_NAME));

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath();
            }
        }

        return Path.of("analyzers", SCRIPT_NAME); // Default, may not exist
    }

    @Override
    public String getLanguageId() {
        return "python";
    }

    @Override
    public Set<String> getSupportedExtensions() {
        return EXTENSIONS;
    }

    @Override
    public boolean isAvailable() {
        if (pythonAvailable == null) {
            pythonAvailable = checkPythonAvailable() && Files.exists(scriptPath);
        }
        return pythonAvailable;
    }

    @Override
    public int getPriority() {
        return 10; // High priority for Python files
    }

    @Override
    public Optional<FileMetrics> analyze(Path sourceFile, AnalyzerConfig config) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        try {
            String json = executeScript(sourceFile);
            return parseJsonToMetrics(json, sourceFile);
        } catch (Exception e) {
            System.err.println("Python analyzer error for " + sourceFile + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private boolean checkPythonAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String executeScript(Path sourceFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "python3",
                scriptPath.toString(),
                sourceFile.toAbsolutePath().toString());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Python script timed out");
        }

        return output.toString();
    }

    private Optional<FileMetrics> parseJsonToMetrics(String json, Path sourceFile) {
        // Simple JSON parsing without external dependencies
        try {
            // Check for error
            if (json.contains("\"error\"")) {
                return Optional.empty();
            }

            int loc = extractInt(json, "loc");
            int functionCount = extractInt(json, "functionCount");
            double totalComplexity = extractDouble(json, "totalComplexity");
            double maxComplexity = extractDouble(json, "maxComplexity");
            double cohesion = extractDouble(json, "cohesion");
            int fanOut = extractInt(json, "fanOut");
            List<String> complexFunctions = extractStringList(json, "complexFunctions");

            return Optional.of(new FileMetrics(
                    sourceFile.toString(),
                    "python",
                    loc,
                    functionCount,
                    totalComplexity,
                    maxComplexity,
                    cohesion,
                    fanOut,
                    complexFunctions,
                    Map.of("classCount", extractInt(json, "classCount"))));

        } catch (Exception e) {
            System.err.println("JSON parse error: " + e.getMessage());
            return Optional.empty();
        }
    }

    // Simple JSON extraction methods (avoiding external JSON library dependency)
    private int extractInt(String json, String key) {
        String pattern = "\"" + key + "\":\\s*(-?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private double extractDouble(String json, String key) {
        String pattern = "\"" + key + "\":\\s*(-?[\\d.]+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : 0.0;
    }

    private List<String> extractStringList(String json, String key) {
        String pattern = "\"" + key + "\":\\s*\\[([^\\]]*)\\]";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            String content = m.group(1);
            if (content.isEmpty())
                return List.of();

            List<String> items = new ArrayList<>();
            java.util.regex.Matcher itemMatcher = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(content);
            while (itemMatcher.find()) {
                items.add(itemMatcher.group(1));
            }
            return items;
        }
        return List.of();
    }
}
