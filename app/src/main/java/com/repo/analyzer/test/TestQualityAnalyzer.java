package com.repo.analyzer.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestQualityAnalyzer {

    // Patterns for Hardcoded Waits
    private static final Pattern THREAD_SLEEP = Pattern.compile("Thread\\.sleep\\s*\\(");
    private static final Pattern AWAIT_DELAY = Pattern.compile("await\\s+delay\\s*\\("); // Kotlin/JS
    private static final Pattern PY_TIME_SLEEP = Pattern.compile("time\\.sleep\\s*\\(");

    private static final Pattern CYPRESS_WAIT = Pattern.compile("cy\\.wait\\s*\\(\\s*\\d+\\s*\\)"); // cy.wait(1000)

    // Patterns for Assertion Logic
    private static final Pattern ASSERT_PATTERN = Pattern.compile("assert|expect\\s*\\(|verify\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    // Patterns for Manual Selectors (simplified)
    private static final Pattern XPATH_USAGE = Pattern.compile("By\\.xpath|xpath\\s*\\(");

    public record Result(List<String> issues, java.util.Map<String, String> stats) {
    }

    public Result analyze(Path file) {
        List<String> issues = new ArrayList<>();
        java.util.Map<String, String> stats = new java.util.HashMap<>();

        try {
            String content = Files.readString(file);

            // Detect Test Framework
            String framework = "Unknown";
            if (content.contains("org.junit"))
                framework = "JUnit";
            else if (content.contains("org.testng"))
                framework = "TestNG";
            else if (content.contains("cypress") || content.contains("cy.visit"))
                framework = "Cypress";
            else if (content.contains("playwright") || content.contains("@playwright/test"))
                framework = "Playwright";
            else if (content.contains("jest") || content.contains("describe(") || content.contains("test("))
                // Simple Jest/Mocha heuristic if no explicit import found
                framework = "Jest/Mocha";
            else if (content.contains("from unittest"))
                framework = "unittest (Python)";
            else if (content.contains("import pytest"))
                framework = "pytest";
            stats.put("Framework", framework);

            // Detect Mocking
            if (content.contains("Mockito") || content.contains("mock("))
                stats.put("Mocking", "Mockito/Mock");
            if (content.contains("jest.fn()") || content.contains("spyOn"))
                stats.put("Mocking", "Jest");

            // Count Assertions
            long assertCount = count(content, ASSERT_PATTERN);
            stats.put("Assertions", String.valueOf(assertCount));

            // Check for Hardcoded Waits
            if (find(content, THREAD_SLEEP) || find(content, AWAIT_DELAY) ||
                    find(content, PY_TIME_SLEEP) || find(content, CYPRESS_WAIT)) {
                issues.add("Hardcoded Wait Detected");
            }

            // Check for low-level selectors in what should be high-level tests
            long xpathCount = count(content, XPATH_USAGE);
            if (xpathCount > 5) {
                issues.add("Heavy XPath Usage (" + xpathCount + ")");
            }

            // Check Assertion Density
            if (assertCount > 20) {
                issues.add("Assertion Roulette (>20 assertions)");
            }

        } catch (IOException e) {
            System.err.println("Could not read test file for quality analysis: " + file);
        }
        return new Result(issues, stats);
    }

    private boolean find(String content, Pattern p) {
        return p.matcher(content).find();
    }

    private long count(String content, Pattern p) {
        Matcher m = p.matcher(content);
        long count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }
}
