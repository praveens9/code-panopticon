package com.repo.analyzer;

import com.repo.analyzer.git.GitAnalysisResult;
import com.repo.analyzer.git.GitMiner;
import com.repo.analyzer.report.AnalysisData;
import com.repo.analyzer.report.CsvReporter;
import com.repo.analyzer.report.HtmlReporter;
import com.repo.analyzer.structural.BytecodeAnalyzer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class App {

    public static void main(String[] args) {
        System.out.println("=== Code Panopticon ===");

        if (args.length < 2) {
            System.err.println("Usage: ./gradlew run --args=\"<git_repo_path> <compiled_classes_path>\"");
            System.exit(1);
        }

        String targetRepo = args[0];
        String targetClasses = args[1];

        List<AnalysisData> reportData = new ArrayList<>();

        try {
            System.out.println("\n>>> PHASE 1: MINING EVOLUTION (GIT) <<<");
            GitMiner miner = new GitMiner();
            GitAnalysisResult gitResult = miner.scanHistory(Path.of(targetRepo));
            Map<String, Integer> churnData = gitResult.churnMap();
            Map<String, Integer> recentChurnData = gitResult.recentChurnMap();
            Map<String, Set<String>> couplingData = gitResult.temporalCouplingMap();

            System.out.println("\n>>> PHASE 2: ANALYZING STRUCTURE (BYTECODE) <<<");
            BytecodeAnalyzer analyzer = new BytecodeAnalyzer(Path.of(targetClasses));

            System.out.println("\n| %-50s | %-5s | %-5s | %-6s | %-6s | %-6s | %-20s |".formatted(
                    "Class", "Churn", "Peers", "LCOM4", "TotalCC", "MaxCC", "Verdict"));
            System.out.println("|" + "-".repeat(52) + "|" + "-".repeat(7) + "|" + "-".repeat(7) + "|" + "-".repeat(8)
                    + "|" + "-".repeat(8) + "|" + "-".repeat(8) + "|" + "-".repeat(22) + "|");

            int scanned = 0;
            // Create graphs directory
            Path graphsDir = Path.of("graphs");
            if (!graphsDir.toFile().exists())
                graphsDir.toFile().mkdirs();

            for (Map.Entry<String, Integer> entry : churnData.entrySet()) {
                String path = entry.getKey();
                int churn = entry.getValue();

                if (path.contains("/test/") || path.contains("Test.java"))
                    continue;

                String className = convertPathToClass(path);
                Optional<BytecodeAnalyzer.Result> result = analyzer.analyze(className);

                if (result.isPresent()) {
                    BytecodeAnalyzer.Result r = result.get();

                    int coupledPeers = couplingData.getOrDefault(path, Collections.emptySet()).size();
                    int recentChurn = recentChurnData.getOrDefault(path, 0);

                    // Calculate Risk Score: (Churn × TotalCC × LCOM4) / 100
                    double riskScore = (churn * r.getMetric("Total Cyclomatic Complexity")
                            * r.getMetric("LCOM4 (Components)")) / 100.0;

                    // Refine Verdict with Forensic Context
                    String verdict = r.verdict();
                    double fanOut = r.getMetric("Fan-Out (Coupling)");

                    if (coupledPeers > 10) {
                        verdict = "SHOTGUN_SURGERY"; // Changes ripple everywhere
                    } else if (coupledPeers > 3 && fanOut < 5 && verdict.equals("OK")) {
                        verdict = "HIDDEN_DEPENDENCY"; // Low static coupling, high temporal coupling
                    }

                    reportData.add(new AnalysisData(
                            r.className(),
                            churn,
                            recentChurn,
                            coupledPeers,
                            r.getMetric("Method Count"),
                            r.getMetric("Avg Fields per Method"),
                            r.getMetric("LCOM4 (Components)"),
                            r.getMetric("Total Cyclomatic Complexity"),
                            r.getMetric("Max Cyclomatic Complexity"),
                            r.getMetric("Fan-Out (Coupling)"),
                            r.getMetric("Afferent Coupling (Ca)"),
                            r.getMetric("Instability"),
                            r.getMetric("Lines of Code"),
                            riskScore,
                            verdict,
                            r.isDataClass(),
                            r.brainMethodNames(),
                            r.lcom4Blocks(),
                            couplingData.getOrDefault(path, java.util.Collections.emptySet())
                                    .stream()
                                    .map(App::convertPathToClass)
                                    .collect(java.util.stream.Collectors.toSet())));

                    System.out.println("| %-50s | %-5d | %-5d | %-6.0f | %-6.0f | %-6.0f | %-20s |".formatted(
                            truncate(r.className(), 50),
                            churn,
                            coupledPeers,
                            r.getMetric("LCOM4 (Components)"),
                            r.getMetric("Total Cyclomatic Complexity"),
                            r.getMetric("Max Cyclomatic Complexity"),
                            verdict));

                    // GRAPH GENERATION FOR MESSYCLASSES
                    if (verdict.contains("MESS") || verdict.contains("SPLIT")) {
                        Optional<String> dot = analyzer.generateGraph(className);
                        if (dot.isPresent()) {
                            String shortName = className.substring(className.lastIndexOf('.') + 1);
                            Files.writeString(graphsDir.resolve(shortName + ".dot"), dot.get());
                        }
                    }

                    scanned++;
                }
            }

            System.out.println("\n--- Legend ---");
            System.out.println("Verdict    : SPLIT_CANDIDATE (LCOM4 > 1), BRAIN_METHOD (High Logic Density).");
            System.out.println(
                    "New Verdicts: SHOTGUN_SURGERY (Coupled > 10 files), HIDDEN_DEPENDENCY (Coupled but no imports).");

            System.out.println("\n>>> PHASE 3: REPORTING <<<");
            HtmlReporter htmlReporter = new HtmlReporter();
            htmlReporter.generate(reportData, Path.of("panopticon-report.html"));
            CsvReporter csvReporter = new CsvReporter();
            csvReporter.generate(reportData, Path.of("panopticon-report.csv"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String convertPathToClass(String path) {
        String s = path.replace("src/main/java/", "");
        s = s.replace(".java", "");
        return s.replace("/", ".");
    }

    private static String truncate(String s, int len) {
        if (s.length() <= len)
            return s;
        return "..." + s.substring(s.length() - (len - 3));
    }
}