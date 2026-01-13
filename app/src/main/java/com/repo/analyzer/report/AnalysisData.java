package com.repo.analyzer.report;

public record AnalysisData(
        String className,
        int churn,
        int recentChurn,
        int coupledPeers,
        double methodCount,
        double avgFields,
        double lcom4,
        double totalCC,
        double maxCC,
        double fanOut,
        double afferentCoupling,
        double instability,
        double loc,
        double riskScore,
        String verdict,
        boolean isDataClass,
        java.util.List<String> brainMethods,
        java.util.List<java.util.Set<String>> lcom4Blocks,
        java.util.Set<String> coupledClassNames) {
}
