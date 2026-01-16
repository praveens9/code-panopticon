package com.repo.analyzer.git;

import java.util.List;

/**
 * Social forensics data for a single file.
 * Captures team dynamics: who knows this code, knowledge concentration, and
 * risk.
 */
public record SocialForensics(
        /** Number of distinct authors who have contributed to this file */
        int authorCount,

        /** Email/name of the primary author (most lines) */
        String primaryAuthor,

        /** Percentage of code written by primary author (0-100) */
        double primaryAuthorPercentage,

        /** Days since the primary author last committed to this file */
        int daysSincePrimaryAuthor,

        /** Days since any commit to this file */
        int daysSinceLastCommit,

        /** Bus factor: number of authors needed to cover 50% of code */
        int busFactor,

        /** True if this is a knowledge island (single expert who is inactive) */
        boolean isKnowledgeIsland,

        /** True if many authors are actively fighting on this file */
        boolean isCoordinationBottleneck,

        /** Top contributors with their percentages */
        List<AuthorContribution> topContributors) {

    /**
     * Represents a single author's contribution to a file.
     */
    public record AuthorContribution(
            String author,
            double percentage,
            int daysSinceActive) {
    }

    /**
     * Create empty social forensics for files with no git blame data.
     */
    public static SocialForensics empty() {
        return new SocialForensics(
                0, "unknown", 0, 0, 0, 0, false, false, List.of());
    }

    /**
     * Calculate a social risk multiplier based on knowledge concentration.
     * Returns 1.0 for healthy files, up to 1.5 for critical knowledge islands.
     */
    public double calculateRiskMultiplier() {
        double multiplier = 1.0;

        // Knowledge island: single expert + inactive
        if (isKnowledgeIsland) {
            multiplier *= 1.5;
        } else if (busFactor == 1 && daysSincePrimaryAuthor > 60) {
            // Moderate risk: single expert but recently active
            multiplier *= 1.2;
        }

        // Coordination bottleneck adds risk too
        if (isCoordinationBottleneck) {
            multiplier *= 1.15;
        }

        return multiplier;
    }
}
