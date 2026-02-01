package com.repo.analyzer;

import com.repo.analyzer.analyzers.*;
import com.repo.analyzer.core.*;
import com.repo.analyzer.git.GitAnalysisResult;
import com.repo.analyzer.git.GitMiner;
import com.repo.analyzer.git.SocialForensics;
import com.repo.analyzer.report.AnalysisData;
import com.repo.analyzer.report.CsvReporter;
import com.repo.analyzer.report.HtmlReporter;
import com.repo.analyzer.report.JsonDataConverter;
import com.repo.analyzer.rules.ForensicRuleEngine;
import com.repo.analyzer.test.TestQualityAnalyzer;
import com.repo.analyzer.testability.TestabilityAnalyzer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Polyglot Code Panopticon - Analyzes code in any language.
 * 
 * Usage: ./gradlew run --args="--repo <path> [--classes <path>] [--output
 * <dir>
 * ]"
 */
public class PolyglotApp {

    public static void main(String[] args) {
        System.out.println("=== Code Panopticon (Polyglot) ===");

        // Parse arguments
        CliArgs cliArgs = parseArgs(args);
        if (cliArgs == null) {
            printUsage();
            System.exit(1);
        }

        try {
            new PolyglotApp().run(cliArgs);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("""
                Usage: ./gradlew run --args="--repo <path> [--classes <path>] [--output <dir>]"

                Arguments:
                  --repo <path>      Path to the Git repository to analyze (required)
                  --classes <path>   Path to compiled Java classes (optional, for bytecode analysis)
                  --output <dir>     Output directory for reports (default: current directory)
                  --name <name>      Custom project name for report title (default: auto-detect)
                  --test-only        Treat all files as test code (for test automation repos)
                  --framework <name> Manual test framework override (e.g. playwright, cypress)
                  --hotspots-only    Only analyze files with Git churn (for large repos)
                  --min-churn <n>    Minimum churn to include a file (default: 1)
                  --keep-clone       Keep cloned repo after analysis (for remote URLs)
                """);
    }

    private record CliArgs(
            String repoInput, // Original input (path or URL)
            Path repoPath, // Resolved local path
            Path classesPath,
            Path outputDir,
            String projectName, // Custom project name (optional)
            boolean hotspotsOnly,
            boolean treatAllAsTest,
            String framework, // Manual framework override (optional)
            int minChurn,
            boolean keepClone) {
    }

    private static CliArgs parseArgs(String[] args) {
        String repoInput = null;
        Path classesPath = null;
        Path outputDir = Path.of("reports");
        String projectName = null;
        boolean hotspotsOnly = false;
        boolean treatAllAsTest = false;
        String framework = null;
        int minChurn = 1;
        boolean keepClone = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--repo" -> {
                    if (i + 1 < args.length)
                        repoInput = args[++i];
                }
                case "--classes" -> {
                    if (i + 1 < args.length)
                        classesPath = Path.of(args[++i]);
                }
                case "--output" -> {
                    if (i + 1 < args.length)
                        outputDir = Path.of(args[++i]);
                }
                case "--name" -> {
                    if (i + 1 < args.length)
                        projectName = args[++i];
                }
                case "--test-only" -> treatAllAsTest = true;
                case "--framework" -> {
                    if (i + 1 < args.length)
                        framework = args[++i];
                }
                case "--hotspots-only" -> hotspotsOnly = true;
                case "--min-churn" -> {
                    if (i + 1 < args.length)
                        minChurn = Integer.parseInt(args[++i]);
                }
                case "--keep-clone" -> keepClone = true;
            }
        }

        if (repoInput == null) {
            return null;
        }

        // Resolve repo path - will be set after potential clone
        Path repoPath = isRemoteUrl(repoInput) ? null : Path.of(repoInput);

        return new CliArgs(repoInput, repoPath, classesPath, outputDir, projectName, hotspotsOnly, treatAllAsTest,
                framework,
                minChurn, keepClone);
    }

    private static boolean isRemoteUrl(String input) {
        return input.startsWith("https://") || input.startsWith("git@") || input.startsWith("http://");
    }

    private Path cloneRemoteRepo(String url) throws Exception {
        System.out.println("Cloning remote repository: " + url);

        // Create temp directory
        Path tempDir = Files.createTempDirectory("panopticon-");
        System.out.println("Cloning to: " + tempDir);

        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "100", url, tempDir.toString());
        pb.inheritIO(); // Show git progress directly in console
        Process process = pb.start();

        boolean finished = process.waitFor(300, TimeUnit.SECONDS); // 5 min timeout
        if (!finished || process.exitValue() != 0) {
            throw new RuntimeException("Failed to clone repository: " + url);
        }

        System.out.println("Clone complete.");
        return tempDir;
    }

    private void run(CliArgs args) throws Exception {
        Path repoPath = args.repoPath();
        Path tempCloneDir = null;

        // Handle remote URLs
        if (isRemoteUrl(args.repoInput())) {
            tempCloneDir = cloneRemoteRepo(args.repoInput());
            repoPath = tempCloneDir;
        }

        try {
            runAnalysis(repoPath, args);
        } finally {
            // Cleanup temp directory if not keeping
            if (tempCloneDir != null && !args.keepClone()) {
                System.out.println("\nCleaning up temporary clone...");
                deleteDirectory(tempCloneDir);
            } else if (tempCloneDir != null) {
                System.out.println("\nCloned repo kept at: " + tempCloneDir);
            }
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            /* ignore */ }
                    });
        } catch (Exception e) {
            System.err.println("Warning: Could not fully clean up temp directory: " + dir);
        }
    }

    private void runAnalysis(Path repoPath, CliArgs args) throws Exception {
        // Build configuration from YAML (or defaults)
        AnalyzerConfig config = AnalyzerConfig.load(repoPath);

        // Apply CLI overrides
        if (args.classesPath() != null) {
            config = config.withCompiledClassesPath(args.classesPath());
        }
        if (args.treatAllAsTest()) {
            config.setTreatAllAsTest(true);
            System.out.println("Mode: Test-Only (All files treated as tests)");
        }
        if (args.framework() != null) {
            config.setManualFramework(args.framework());
            System.out.println("Framework Override: " + args.framework());
        }

        // Initialize analyzers
        System.out.println("\n>>> INITIALIZING ANALYZERS <<<");
        AnalyzerRegistry registry = buildRegistry(args.classesPath());
        registry.printSummary();

        // Phase 1: Git Analysis
        System.out.println("\n>>> PHASE 1: MINING EVOLUTION (GIT) <<<");
        GitMiner miner = new GitMiner();
        GitAnalysisResult gitResult = miner.scanHistory(repoPath);
        Map<String, Integer> churnData = gitResult.churnMap();
        Map<String, Integer> recentChurnData = gitResult.recentChurnMap();
        Map<String, Set<String>> couplingData = gitResult.temporalCouplingMap();

        // Filter by minimum churn if hotspots-only mode
        Set<String> filesToAnalyze;
        if (args.hotspotsOnly()) {
            filesToAnalyze = churnData.entrySet().stream()
                    .filter(e -> e.getValue() >= args.minChurn())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            System.out.println(
                    "Hotspot mode: analyzing " + filesToAnalyze.size() + " files with churn >= " + args.minChurn());
        } else {
            filesToAnalyze = churnData.keySet();
        }

        // Phase 2: Structural Analysis
        System.out.println("\n>>> PHASE 2: ANALYZING STRUCTURE <<<");
        ForensicRuleEngine ruleEngine = new ForensicRuleEngine();
        TestabilityAnalyzer testabilityAnalyzer = new TestabilityAnalyzer(repoPath);
        TestQualityAnalyzer testQualityAnalyzer = new TestQualityAnalyzer(config.getManualFramework());
        List<AnalysisData> reportData = new ArrayList<>();

        // Print header
        System.out.println("\n| %-50s | %-10s | %-5s | %-5s | %-6s | %-6s | %-20s |".formatted(
                "File", "Lang", "Churn", "Peers", "CC", "MaxCC", "Verdict"));
        System.out.println("|" + "-".repeat(52) + "|" + "-".repeat(12) + "|" + "-".repeat(7) + "|" + "-".repeat(7)
                + "|" + "-".repeat(8) + "|" + "-".repeat(8) + "|" + "-".repeat(22) + "|");

        int analyzed = 0;
        int skipped = 0;
        int processed = 0;
        int totalFiles = filesToAnalyze.size();

        for (String relativePath : filesToAnalyze) {
            processed++;
            Path filePath = repoPath.resolve(relativePath);
            if (!Files.exists(filePath)) {
                continue;
            }

            boolean isTest = config.isTestFile(Paths.get(relativePath));

            // Analyze with appropriate analyzer
            Optional<FileMetrics> metricsOpt = registry.analyze(filePath, config);
            if (metricsOpt.isEmpty()) {
                continue;
            }

            FileMetrics metrics = metricsOpt.get();
            int churn = churnData.getOrDefault(relativePath, 0);
            int recentChurn = recentChurnData.getOrDefault(relativePath, 0);
            int coupledPeers = couplingData.getOrDefault(relativePath, Collections.emptySet()).size();
            int daysSinceLastCommit = gitResult.daysSinceLastCommit(relativePath);

            // Mine social forensics for files with significant churn
            SocialForensics social = SocialForensics.empty();
            if (churn >= 3) { // Only mine blame for active files (performance optimization)
                social = miner.mineSocialForensics(repoPath, relativePath, recentChurn, churn);
            }

            // Calculate risk score with social amplifier
            double lcom4 = metrics.cohesion() > 0 ? 1.0 / metrics.cohesion() : 1.0;
            double baseRisk = (churn * metrics.totalComplexity() * lcom4) / 100.0;
            double riskScore = baseRisk * social.calculateRiskMultiplier();

            // Determine verdict with social context
            ForensicRuleEngine.EvaluationContext ctx = new ForensicRuleEngine.EvaluationContext(
                    metrics, churn, recentChurn, coupledPeers, config, social);
            String verdict = ruleEngine.evaluate(ctx);

            // Convert to AnalysisData for reporting
            String className = extractClassName(relativePath);
            // Testability analysis
            boolean hasTestFile = testabilityAnalyzer.hasTest(relativePath);
            String testFilePath = testabilityAnalyzer.findTestFile(relativePath)
                    .map(Path::toString).orElse("");
            int testabilityScore = testabilityAnalyzer.calculateTestabilityScore(
                    relativePath, metrics.fanOut(), lcom4, metrics.totalComplexity(), hasTestFile);
            boolean isUntestedHotspot = testabilityAnalyzer.isUntestedHotspot(
                    hasTestFile, riskScore, churn);

            // Test Quality Analysis
            List<String> testIssues = new ArrayList<>();
            java.util.Map<String, String> testProfile = new java.util.HashMap<>();
            if (isTest) {
                TestQualityAnalyzer.Result result = testQualityAnalyzer.analyze(filePath);
                testIssues = result.issues();
                testProfile = result.stats();
            }

            reportData.add(new AnalysisData(
                    className,
                    churn,
                    recentChurn,
                    coupledPeers,
                    daysSinceLastCommit,
                    metrics.functionCount(),
                    metrics.cohesion(),
                    lcom4,
                    metrics.totalComplexity(),
                    metrics.maxComplexity(),
                    metrics.fanOut(),
                    metrics.getExtra("afferentCoupling", 0.0),
                    metrics.getExtra("instability", 0.0),
                    metrics.loc(),
                    riskScore,
                    verdict,
                    false, // isDataClass
                    metrics.complexFunctions(),
                    List.of(), // lcom4Blocks
                    couplingData.getOrDefault(relativePath, Collections.emptySet()).stream()
                            .map(this::extractClassName)
                            .collect(Collectors.toSet()),
                    social.authorCount(),
                    social.primaryAuthor(),
                    social.primaryAuthorPercentage(),
                    social.busFactor(),
                    social.isKnowledgeIsland(),
                    social.topContributors(),
                    hasTestFile,
                    testFilePath,
                    testabilityScore,

                    isUntestedHotspot,
                    isTest,
                    testIssues,
                    testProfile));

            // Print row
            System.out.println("| %-50s | %-10s | %-5d | %-5d | %-6.0f | %-6.0f | %-20s |".formatted(
                    truncate(className, 50),
                    metrics.language(),
                    churn,
                    coupledPeers,
                    metrics.totalComplexity(),
                    metrics.maxComplexity(),
                    verdict));

            analyzed++;
            // if (isTest) skipped++; // Removed: Tests are now analyzed, not skipped
            // User view: Skipped usually means ignored. Let's rename in output or add new
            // metric.
            // For now, let's just count Analyzed = total. But maybe we want separate stats.

            // Progress update
            if (processed % 50 == 0 || processed == totalFiles) {
                System.out.print("\r> analyzing structure... " + processed + "/" + totalFiles + " files processed");
                System.out.flush();
            }
        }
        System.out.println(); // Newline after progress

        System.out.printf("%nAnalyzed: %d files | Skipped: %d%n", analyzed, skipped);

        // Phase 3: Generate Reports
        System.out.println("\n>>> PHASE 3: GENERATING REPORTS <<<");

        // Create output directory if it does not exist
        Files.createDirectories(args.outputDir());

        Path htmlPath = args.outputDir().resolve("panopticon-report.html");
        Path csvPath = args.outputDir().resolve("panopticon-report.csv");

        // Use CLI-provided name or auto-detect from path
        String projectName = args.projectName() != null
                ? args.projectName()
                : extractProjectName(repoPath, args.repoInput());

        HtmlReporter.AnalysisStats stats = new HtmlReporter.AnalysisStats(analyzed, skipped, totalFiles);
        new HtmlReporter().generate(reportData, stats, htmlPath, projectName);
        new CsvReporter().generate(reportData, csvPath);

        // Generate JSON for AI/Tools
        Path jsonPath = args.outputDir().resolve("panopticon-data.json");
        String jsonOutput = new JsonDataConverter().convertToSelfDescribingJson(reportData);
        Files.writeString(jsonPath, jsonOutput);
        System.out.println("JSON Report generated at: " + jsonPath);

        // Summary
        printSummary(reportData);
    }

    private AnalyzerRegistry buildRegistry(Path classesPath) {
        List<LanguageAnalyzer> analyzers = new ArrayList<>();

        // Add Java bytecode analyzer if classes path provided
        if (classesPath != null && Files.exists(classesPath)) {
            analyzers.add(JavaBytecodeAnalyzer.create(classesPath));
        }

        // Add language-specific analyzers
        analyzers.add(new PythonAnalyzer());
        analyzers.add(new JavaScriptAnalyzer());

        // Fallback analyzer
        GenericTextAnalyzer fallback = new GenericTextAnalyzer();

        return new AnalyzerRegistry(analyzers, fallback);
    }

    private String extractClassName(String path) {
        // Keep full relative path with extension for clarity
        // Makes backend/main.py vs frontend/main.js distinguishable
        String s = path;

        // Remove common source prefixes only
        for (String prefix : List.of("src/main/java/", "src/main/", "src/", "lib/", "app/src/")) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
                break;
            }
        }

        // Keep the path with slashes and extension - don't convert to dots
        return s;
    }

    private String truncate(String s, int len) {
        if (s.length() <= len)
            return s;
        return "..." + s.substring(s.length() - (len - 3));
    }

    private String extractProjectName(Path repoPath, String repoInput) {
        // Try to get name from URL (e.g., https://github.com/user/project -> project)
        if (isRemoteUrl(repoInput)) {
            String url = repoInput;
            // Remove .git suffix
            if (url.endsWith(".git"))
                url = url.substring(0, url.length() - 4);
            // Remove trailing slash
            if (url.endsWith("/"))
                url = url.substring(0, url.length() - 1);
            // Get last segment
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash >= 0) {
                return url.substring(lastSlash + 1);
            }
        }
        // Fall back to directory name (resolve to handle "." case)
        if (repoPath != null) {
            try {
                Path absPath = repoPath.toAbsolutePath().normalize();
                Path fileName = absPath.getFileName();
                if (fileName != null) {
                    return fileName.toString();
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return "Code Forensics";
    }

    private void printSummary(List<AnalysisData> data) {
        Map<String, Long> verdictCounts = data.stream()
                .collect(Collectors.groupingBy(AnalysisData::verdict, Collectors.counting()));

        System.out.println("\n=== SUMMARY ===");
        System.out.println("Verdict Distribution:");
        verdictCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> System.out.printf("  %-25s: %d%n", e.getKey(), e.getValue()));

        // Top risks
        List<AnalysisData> topRisks = data.stream()
                .sorted((a, b) -> Double.compare(b.riskScore(), a.riskScore()))
                .limit(5)
                .toList();

        if (!topRisks.isEmpty()) {
            System.out.println("\nTop 5 Risk Files:");
            for (int i = 0; i < topRisks.size(); i++) {
                AnalysisData d = topRisks.get(i);
                System.out.printf("  %d. %s (Risk: %.1f, %s)%n",
                        i + 1,
                        d.className().substring(Math.max(0, d.className().length() - 40)),
                        d.riskScore(),
                        d.verdict());
            }
        }
    }
}
