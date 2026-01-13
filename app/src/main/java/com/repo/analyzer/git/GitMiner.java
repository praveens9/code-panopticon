package com.repo.analyzer.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class GitMiner {

    // Record to hold commit data with timestamp
    private record CommitTransaction(Set<String> files, long timestamp) {
    }

    private static final int MIN_SHARED_COMMITS = 5;
    private static final int MIN_COUPLING_PERCENTAGE = 30; // 30% coupling strength
    private static final int RECENT_DAYS = 90; // Recent churn window

    public GitAnalysisResult scanHistory(Path repoRoot) throws IOException {
        System.out.println("Mining Git history for: " + repoRoot);

        // Parse Commit Transactions with timestamps
        List<CommitTransaction> transactions = parseGitLogWithTimestamps(repoRoot);

        // 1. Calculate Churn (all time)
        Map<String, Integer> churnMap = calculateChurn(transactions);
        System.out.println("Found " + churnMap.size() + " active Java files.");

        // 2. Calculate Recent Churn (last 90 days)
        Map<String, Integer> recentChurnMap = calculateRecentChurn(transactions);
        System.out.println("Found " + recentChurnMap.size() + " files with recent activity.");

        // 3. Calculate Temporal Coupling
        Map<String, Set<String>> couplingMap = calculateTemporalCoupling(transactions, churnMap);
        System.out.println("Identified " + couplingMap.size() + " files with significant temporal coupling.");

        return new GitAnalysisResult(churnMap, recentChurnMap, couplingMap);
    }

    private List<CommitTransaction> parseGitLogWithTimestamps(Path repoRoot) throws IOException {
        // --name-only: Show changed files
        // --format="format:###%ct": Separator + Unix timestamp
        // --no-merges: Skip merge commits
        ProcessBuilder builder = new ProcessBuilder(
                "git", "log", "--name-only", "--format=format:###%ct", "--no-merges");
        builder.directory(repoRoot.toFile());

        Process process = builder.start();

        List<CommitTransaction> transactions = new ArrayList<>();
        Set<String> currentCommit = new HashSet<>();
        long currentTimestamp = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (line.startsWith("###")) {
                    // Save previous commit
                    if (!currentCommit.isEmpty()) {
                        transactions.add(new CommitTransaction(currentCommit, currentTimestamp));
                        currentCommit = new HashSet<>();
                    }
                    // Parse timestamp
                    String timestampStr = line.substring(3);
                    currentTimestamp = Long.parseLong(timestampStr);
                } else if (line.endsWith(".java")) {
                    currentCommit.add(line);
                }
            }
            // Add the last commit
            if (!currentCommit.isEmpty()) {
                transactions.add(new CommitTransaction(currentCommit, currentTimestamp));
            }
        }

        return transactions;
    }

    private Map<String, Integer> calculateChurn(List<CommitTransaction> transactions) {
        Map<String, Integer> churn = new HashMap<>();
        for (CommitTransaction commit : transactions) {
            for (String file : commit.files()) {
                churn.put(file, churn.getOrDefault(file, 0) + 1);
            }
        }

        // Sort by Churn Descending
        return churn.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    private Map<String, Integer> calculateRecentChurn(List<CommitTransaction> transactions) {
        long cutoffTimestamp = System.currentTimeMillis() / 1000 - (RECENT_DAYS * 24 * 60 * 60);
        Map<String, Integer> recentChurn = new HashMap<>();

        for (CommitTransaction commit : transactions) {
            if (commit.timestamp() >= cutoffTimestamp) {
                for (String file : commit.files()) {
                    recentChurn.put(file, recentChurn.getOrDefault(file, 0) + 1);
                }
            }
        }

        return recentChurn.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    private Map<String, Set<String>> calculateTemporalCoupling(List<CommitTransaction> transactions,
            Map<String, Integer> churnMap) {
        Map<String, Set<String>> couplingResult = new HashMap<>();

        // Optimization: Only analyze files that have enough churn to matter
        Set<String> significantFiles = churnMap.keySet().stream()
                .filter(f -> churnMap.get(f) >= MIN_SHARED_COMMITS)
                .collect(Collectors.toSet());

        for (String targetFile : significantFiles) {
            Map<String, Integer> pairCounts = new HashMap<>();
            int targetCommits = churnMap.get(targetFile);

            // Scan all transactions where targetFile appears
            for (CommitTransaction commit : transactions) {
                if (commit.files().contains(targetFile)) {
                    for (String peer : commit.files()) {
                        if (!peer.equals(targetFile)) {
                            pairCounts.put(peer, pairCounts.getOrDefault(peer, 0) + 1);
                        }
                    }
                }
            }

            // Filter for strong coupling
            Set<String> coupledPeers = new HashSet<>();
            for (Map.Entry<String, Integer> entry : pairCounts.entrySet()) {
                String peer = entry.getKey();
                int sharedCommits = entry.getValue();

                // Formula: P(Peer | Target) = Shared / TargetTotal
                double strength = (double) sharedCommits / targetCommits * 100;

                if (sharedCommits >= MIN_SHARED_COMMITS && strength >= MIN_COUPLING_PERCENTAGE) {
                    coupledPeers.add(peer);
                }
            }

            if (!coupledPeers.isEmpty()) {
                couplingResult.put(targetFile, coupledPeers);
            }
        }
        return couplingResult;
    }
}
