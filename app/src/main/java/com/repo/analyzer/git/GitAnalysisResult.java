package com.repo.analyzer.git;

import java.util.Map;
import java.util.Set;

public record GitAnalysisResult(
        Map<String, Integer> churnMap,
        Map<String, Integer> recentChurnMap, // Commits in last 90 days
        Map<String, Set<String>> temporalCouplingMap // Key: FilePath, Value: Set of Coupled Peer FilePaths
) {
}
