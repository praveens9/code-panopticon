package com.repo.analyzer.report;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlReporterTest {

    private final HtmlReporter reporter = new HtmlReporter();

    @Test
    void testConvertToJson() {
        AnalysisData data = new AnalysisData(
                "backend/utils.py",
                50, 10, 5, 30, // churn, recentChurn, coupledPeers, daysSinceLastCommit
                5.0, 0.5, 2.0, // methodCount, avgFields, lcom4
                20.0, 10.0, // totalCC, maxCC
                0.5, 0.5, 1.0, // fanOut, afferentCoupling, instability
                100.0, 5.0, // loc, riskScore
                "OK", false, // verdict, isDataClass
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), // brainMethods, lcom4Blocks,
                                                                                          // coupledClassNames
                2, "test@example.com", 60.0, 2, false, Collections.emptyList()); // social forensics

        String json = reporter.convertToJson(List.of(data));

        // precise assertions on key fields
        assertTrue(json.contains("verdict: 'OK'"), "JSON should contain verdict");
        assertTrue(json.contains("label: 'backend/utils.py'"), "JSON should contain label");
        assertTrue(json.contains("riskScore: 5.00"), "JSON should contain riskScore");
    }

    @Test
    void testConvertToHierarchyJson() {
        AnalysisData data1 = new AnalysisData(
                "network/server.go",
                50, 10, 5, 15,
                5.0, 0.5, 2.0,
                20.0, 10.0,
                0.5, 0.5, 1.0,
                100.0, 5.0,
                "OK", false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                3, "dev@example.com", 45.0, 2, false, Collections.emptyList());
        AnalysisData data2 = new AnalysisData(
                "README.md",
                1, 0, 0, 100,
                1.0, 0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0, 0.0,
                10.0, 0.0,
                "OK", false,
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet(),
                1, "author@example.com", 100.0, 1, false, Collections.emptyList());

        String json = reporter.convertToHierarchyJson(List.of(data1, data2));

        // Check for hierarchy structure (compact JSON)
        assertTrue(json.contains("\"name\":\"network\""), "Should contain folder name");
        assertTrue(json.contains("\"name\":\"server.go\""), "Should contain file name");

        // Check for Root Files grouping
        assertTrue(json.contains("\"name\":\"Root Files\""), "Should contain Root Files group");
        assertTrue(json.contains("\"name\":\"README.md\""), "Should contain root file");
    }
}
