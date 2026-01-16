package com.repo.analyzer.git;

import java.util.Map;
import java.util.Set;

/**
 * Result of Git history mining.
 * Contains evolutionary metrics: churn, recent churn, temporal coupling, and
 * last commit dates.
 */
public record GitAnalysisResult(
                /** Total commits per file (all time) */
                Map<String, Integer> churnMap,

                /** Commits per file in last 90 days */
                Map<String, Integer> recentChurnMap,

                /** Temporal coupling: files that change together */
                Map<String, Set<String>> temporalCouplingMap,

                /** Last commit timestamp per file (Unix epoch seconds) */
                Map<String, Long> lastCommitMap) {
        /**
         * Calculate days since last commit for a file.
         */
        public int daysSinceLastCommit(String filePath) {
                Long timestamp = lastCommitMap.get(filePath);
                if (timestamp == null)
                        return -1;
                long now = System.currentTimeMillis() / 1000;
                return (int) ((now - timestamp) / (24 * 60 * 60));
        }
}
