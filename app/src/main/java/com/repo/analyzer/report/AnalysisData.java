package com.repo.analyzer.report;

import com.repo.analyzer.git.SocialForensics;

/**
 * Complete analysis data for a single file.
 * Combines structural, evolutionary, social, and testability metrics.
 */
public record AnalysisData(
                // Identification
                String className,

                // Evolutionary metrics
                int churn,
                int recentChurn,
                int coupledPeers,
                int daysSinceLastCommit,

                // Structural metrics
                double methodCount,
                double avgFields,
                double lcom4,
                double totalCC,
                double maxCC,
                double fanOut,
                double afferentCoupling,
                double instability,
                double loc,

                // Composite
                double riskScore,
                String verdict,
                boolean isDataClass,

                // Detailed analysis
                java.util.List<String> brainMethods,
                java.util.List<java.util.Set<String>> lcom4Blocks,
                java.util.Set<String> coupledClassNames,

                // Social forensics (v3.0)
                int authorCount,
                String primaryAuthor,
                double primaryAuthorPercentage,
                int busFactor,
                boolean isKnowledgeIsland,
                java.util.List<SocialForensics.AuthorContribution> topContributors,

                // Testability (v3.0 Phase 2)
                boolean hasTestFile,
                String testFilePath,
                int testabilityScore,
                boolean isUntestedHotspot) {

        /**
         * Create AnalysisData with empty social forensics and testability (for backward
         * compatibility).
         */
        public static AnalysisData withoutSocialAndTestability(
                        String className, int churn, int recentChurn, int coupledPeers, int daysSinceLastCommit,
                        double methodCount, double avgFields, double lcom4,
                        double totalCC, double maxCC, double fanOut,
                        double afferentCoupling, double instability, double loc,
                        double riskScore, String verdict, boolean isDataClass,
                        java.util.List<String> brainMethods,
                        java.util.List<java.util.Set<String>> lcom4Blocks,
                        java.util.Set<String> coupledClassNames) {
                return new AnalysisData(
                                className, churn, recentChurn, coupledPeers, daysSinceLastCommit,
                                methodCount, avgFields, lcom4, totalCC, maxCC, fanOut,
                                afferentCoupling, instability, loc, riskScore, verdict, isDataClass,
                                brainMethods, lcom4Blocks, coupledClassNames,
                                0, "unknown", 0, 0, false, java.util.List.of(),
                                false, "", 0, false);
        }

        /**
         * Create AnalysisData without testability data (for backward compatibility).
         */
        public static AnalysisData withoutTestability(
                        String className, int churn, int recentChurn, int coupledPeers, int daysSinceLastCommit,
                        double methodCount, double avgFields, double lcom4,
                        double totalCC, double maxCC, double fanOut,
                        double afferentCoupling, double instability, double loc,
                        double riskScore, String verdict, boolean isDataClass,
                        java.util.List<String> brainMethods,
                        java.util.List<java.util.Set<String>> lcom4Blocks,
                        java.util.Set<String> coupledClassNames,
                        int authorCount, String primaryAuthor, double primaryAuthorPercentage,
                        int busFactor, boolean isKnowledgeIsland,
                        java.util.List<SocialForensics.AuthorContribution> topContributors) {
                return new AnalysisData(
                                className, churn, recentChurn, coupledPeers, daysSinceLastCommit,
                                methodCount, avgFields, lcom4, totalCC, maxCC, fanOut,
                                afferentCoupling, instability, loc, riskScore, verdict, isDataClass,
                                brainMethods, lcom4Blocks, coupledClassNames,
                                authorCount, primaryAuthor, primaryAuthorPercentage, busFactor, isKnowledgeIsland,
                                topContributors,
                                false, "", 0, false);
        }
}
