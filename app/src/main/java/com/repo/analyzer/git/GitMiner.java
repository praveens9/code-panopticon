package com.repo.analyzer.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Git history miner for evolutionary and social forensics.
 * Extracts churn, temporal coupling, and author distribution metrics.
 */
public class GitMiner {

    // Record to hold commit data with timestamp and author
    private record CommitTransaction(Set<String> files, long timestamp, String author) {
    }

    private static final int MIN_SHARED_COMMITS = 5;
    private static final int MIN_COUPLING_PERCENTAGE = 30; // 30% coupling strength
    private static final int RECENT_DAYS = 90; // Recent churn window
    private static final int KNOWLEDGE_ISLAND_THRESHOLD_PERCENTAGE = 80;
    private static final int KNOWLEDGE_ISLAND_INACTIVE_DAYS = 90;
    private static final int COORDINATION_BOTTLENECK_AUTHORS = 3;
    private static final int COORDINATION_BOTTLENECK_RECENT_DAYS = 30;

    public GitAnalysisResult scanHistory(Path repoRoot) throws IOException {
        System.out.println("Mining Git history for: " + repoRoot);

        // Parse Commit Transactions with timestamps and authors
        List<CommitTransaction> transactions = parseGitLogWithTimestamps(repoRoot);

        // 1. Calculate Churn (all time)
        Map<String, Integer> churnMap = calculateChurn(transactions);
        System.out.println("Found " + churnMap.size() + " active source files.");

        // 2. Calculate Recent Churn (last 90 days)
        Map<String, Integer> recentChurnMap = calculateRecentChurn(transactions);
        System.out.println("Found " + recentChurnMap.size() + " files with recent activity.");

        // 3. Calculate Temporal Coupling
        Map<String, Set<String>> couplingMap = calculateTemporalCoupling(transactions, churnMap);
        System.out.println("Identified " + couplingMap.size() + " files with significant temporal coupling.");

        // 4. Calculate Last Commit Dates
        Map<String, Long> lastCommitMap = calculateLastCommitDates(transactions);

        return new GitAnalysisResult(churnMap, recentChurnMap, couplingMap, lastCommitMap);
    }

    /**
     * Mine social forensics for a specific file using git blame.
     * This is called per-file during analysis for high-churn files.
     */
    public SocialForensics mineSocialForensics(Path repoRoot, String relativePath,
            int recentChurn, int totalChurn) {
        try {
            // Parse git blame for author distribution
            Map<String, Integer> authorLineCounts = new HashMap<>();
            Map<String, Long> authorLastCommit = new HashMap<>();

            ProcessBuilder builder = new ProcessBuilder(
                    "git", "blame", "--line-porcelain", relativePath);
            builder.directory(repoRoot.toFile());
            builder.redirectErrorStream(true);

            Process process = builder.start();

            String currentAuthor = null;
            long currentTimestamp = 0;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("author-mail ")) {
                        // Extract email: author-mail <email@example.com>
                        currentAuthor = line.substring(12).trim();
                        // Remove < and >
                        if (currentAuthor.startsWith("<") && currentAuthor.endsWith(">")) {
                            currentAuthor = currentAuthor.substring(1, currentAuthor.length() - 1);
                        }
                    } else if (line.startsWith("author-time ")) {
                        currentTimestamp = Long.parseLong(line.substring(12).trim());
                    } else if (line.startsWith("\t") && currentAuthor != null) {
                        // This is a code line - count it for the author
                        authorLineCounts.merge(currentAuthor, 1, (a, b) -> a + b);

                        // Track latest commit per author
                        Long existing = authorLastCommit.get(currentAuthor);
                        if (existing == null || currentTimestamp > existing) {
                            authorLastCommit.put(currentAuthor, currentTimestamp);
                        }
                        currentAuthor = null;
                    }
                }
            }

            process.waitFor();

            if (authorLineCounts.isEmpty()) {
                return SocialForensics.empty();
            }

            // Calculate total lines
            int totalLines = authorLineCounts.values().stream().mapToInt(Integer::intValue).sum();

            // Build contributor list sorted by contribution
            List<SocialForensics.AuthorContribution> contributors = new ArrayList<>();
            long now = Instant.now().getEpochSecond();

            for (Map.Entry<String, Integer> entry : authorLineCounts.entrySet()) {
                String author = entry.getKey();
                int lines = entry.getValue();
                double percentage = (double) lines / totalLines * 100;

                Long lastCommit = authorLastCommit.get(author);
                int daysSinceActive = lastCommit != null
                        ? (int) ((now - lastCommit) / (24 * 60 * 60))
                        : 999;

                contributors.add(new SocialForensics.AuthorContribution(author, percentage, daysSinceActive));
            }

            // Sort by percentage descending
            contributors.sort((a, b) -> Double.compare(b.percentage(), a.percentage()));

            // Calculate bus factor (authors needed to cover 50%)
            int busFactor = 0;
            double coverage = 0;
            for (SocialForensics.AuthorContribution c : contributors) {
                busFactor++;
                coverage += c.percentage();
                if (coverage >= 50)
                    break;
            }

            // Get primary author stats
            SocialForensics.AuthorContribution primary = contributors.get(0);
            String primaryAuthor = primary.author();
            double primaryPercentage = primary.percentage();
            int daysSincePrimary = primary.daysSinceActive();

            // Calculate days since last commit (any author)
            int daysSinceLastCommit = contributors.stream()
                    .mapToInt(SocialForensics.AuthorContribution::daysSinceActive)
                    .min()
                    .orElse(999);

            // Detect knowledge island: >80% by one author who is inactive (and author is
            // KNOWN)
            boolean isKnowledgeIsland = primaryPercentage > KNOWLEDGE_ISLAND_THRESHOLD_PERCENTAGE
                    && daysSincePrimary > KNOWLEDGE_ISLAND_INACTIVE_DAYS
                    && isKnownAuthor(primaryAuthor);

            // Detect coordination bottleneck: many recent authors on high-churn file
            long recentAuthorsCount = contributors.stream()
                    .filter(c -> c.daysSinceActive() < COORDINATION_BOTTLENECK_RECENT_DAYS)
                    .count();
            boolean isCoordinationBottleneck = recentAuthorsCount >= COORDINATION_BOTTLENECK_AUTHORS
                    && recentChurn > 10;

            // Limit to top 5 contributors for display
            List<SocialForensics.AuthorContribution> topContributors = contributors.stream()
                    .limit(5)
                    .collect(Collectors.toList());

            return new SocialForensics(
                    authorLineCounts.size(),
                    primaryAuthor,
                    primaryPercentage,
                    daysSincePrimary,
                    daysSinceLastCommit,
                    busFactor,
                    isKnowledgeIsland,
                    isCoordinationBottleneck,
                    topContributors);

        } catch (Exception e) {
            // If git blame fails, return empty
            return SocialForensics.empty();
        }
    }

    private boolean isKnownAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            return false;
        }
        String lower = author.trim().toLowerCase();
        return !lower.equals("unknown") && !lower.equals("null") && !lower.equals("undefined");
    }

    private List<CommitTransaction> parseGitLogWithTimestamps(Path repoRoot) throws IOException {
        // --name-only: Show changed files
        // --format="format:###%ct###%ae": Separator + Unix timestamp + author email
        // --no-merges: Skip merge commits
        ProcessBuilder builder = new ProcessBuilder(
                "git", "log", "--name-only", "--format=format:###%ct###%ae", "--no-merges");
        builder.directory(repoRoot.toFile());

        Process process = builder.start();

        List<CommitTransaction> transactions = new ArrayList<>();
        Set<String> currentCommit = new HashSet<>();
        long currentTimestamp = 0;
        String currentAuthor = "";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                if (line.startsWith("###")) {
                    // Save previous commit
                    if (!currentCommit.isEmpty()) {
                        transactions.add(new CommitTransaction(currentCommit, currentTimestamp, currentAuthor));
                        currentCommit = new HashSet<>();
                    }
                    // Parse timestamp and author: ###timestamp###author
                    String[] parts = line.substring(3).split("###");
                    currentTimestamp = Long.parseLong(parts[0]);
                    currentAuthor = parts.length > 1 ? parts[1] : "";

                    if (transactions.size() % 1000 == 0 && transactions.size() > 0) {
                        System.out.print("\r> mining history... " + transactions.size() + " commits parsed");
                        System.out.flush();
                    }
                } else if (isSourceFile(line)) {
                    currentCommit.add(line);
                }
            }
            // Add the last commit
            if (!currentCommit.isEmpty()) {
                transactions.add(new CommitTransaction(currentCommit, currentTimestamp, currentAuthor));
            }
            System.out.println("Parsed " + transactions.size() + " commits.");
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

    private Map<String, Long> calculateLastCommitDates(List<CommitTransaction> transactions) {
        Map<String, Long> lastCommit = new HashMap<>();

        for (CommitTransaction commit : transactions) {
            for (String file : commit.files()) {
                Long existing = lastCommit.get(file);
                if (existing == null || commit.timestamp() > existing) {
                    lastCommit.put(file, commit.timestamp());
                }
            }
        }

        return lastCommit;
    }

    private Map<String, Set<String>> calculateTemporalCoupling(List<CommitTransaction> transactions,
            Map<String, Integer> churnMap) {
        Map<String, Set<String>> couplingResult = new HashMap<>();

        // Optimization: Only analyze files that have enough churn to matter
        Set<String> significantFiles = churnMap.keySet().stream()
                .filter(f -> churnMap.get(f) >= MIN_SHARED_COMMITS)
                .collect(Collectors.toSet());

        int processedCount = 0;
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

            // Progress update for coupling analysis
            processedCount++;
            if (processedCount % 50 == 0 || processedCount == significantFiles.size()) {
                System.out.print(
                        "\r> analyzed coupling for " + processedCount + "/" + significantFiles.size() + " files...");
                System.out.flush();
            }
        }
        System.out.println(); // Newline after progress
        return couplingResult;
    }

    /**
     * Check if a file is a source file based on extension.
     * Supports: Java, Python, JavaScript, TypeScript, Go, Rust, Ruby, PHP, C/C++
     */
    private static boolean isSourceFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".py") ||
                lower.endsWith(".js") || lower.endsWith(".jsx") ||
                lower.endsWith(".ts") || lower.endsWith(".tsx") ||
                lower.endsWith(".mjs") || lower.endsWith(".cjs") ||
                lower.endsWith(".go") || lower.endsWith(".rs") ||
                lower.endsWith(".rb") || lower.endsWith(".php") ||
                lower.endsWith(".c") || lower.endsWith(".cpp") ||
                lower.endsWith(".h") || lower.endsWith(".hpp") ||
                lower.endsWith(".kt") || lower.endsWith(".kts") ||
                lower.endsWith(".swift") || lower.endsWith(".scala");
    }
}
