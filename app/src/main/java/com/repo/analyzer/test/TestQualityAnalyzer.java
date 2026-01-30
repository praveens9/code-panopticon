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
    private static final Pattern JS_SET_TIMEOUT = Pattern.compile("setTimeout\\s*\\(");
    private static final Pattern CYPRESS_WAIT = Pattern.compile("cy\\.wait\\s*\\(\\s*\\d+\\s*\\)"); // cy.wait(1000)

    // Patterns for Assertion Logic
    private static final Pattern ASSERT_PATTERN = Pattern.compile("assert|expect\\(|verify\\(",
            Pattern.CASE_INSENSITIVE);

    // Patterns for Manual Selectors (simplified)
    private static final Pattern XPATH_USAGE = Pattern.compile("By\\.xpath|xpath\\s*\\(");
    private static final Pattern CSS_USAGE = Pattern.compile("By\\.cssSelector|css\\s*\\(");

    public List<String> analyze(Path file) {
        List<String> issues = new ArrayList<>();
        try {
            String content = Files.readString(file);

            // Check for Hardcoded Waits
            if (find(content, THREAD_SLEEP) || find(content, AWAIT_DELAY) ||
                    find(content, PY_TIME_SLEEP) || find(content, CYPRESS_WAIT)) {
                issues.add("Hardcoded Wait Detected");
            }

            // Check for low-level selectors in what should be high-level tests
            // (Heuristic: If file uses By.xpath multiple times, it might be brittle)
            long xpathCount = count(content, XPATH_USAGE);
            if (xpathCount > 5) {
                issues.add("Heavy XPath Usage (" + xpathCount + ")");
            }

            // Check Assertion Density
            // A heuristic: if it's a test file but has NO assertions, it's a "Smoke Test"
            // or useless
            // but we need to be careful about @Test annotation presence vs helper files
            // For now, let's just count them.
            // If it has > 0 asserts, checking for "Assertion Roulette" (too many)
            long assertCount = count(content, ASSERT_PATTERN);
            if (assertCount == 0) {
                // Maybe it's a helper? Or maybe it's a test without assert.
                // safe to skip flagging "No Assertions" aggressively to avoid noise for helpers
            } else if (assertCount > 20) {
                issues.add("Assertion Roulette (>20 assertions)");
            }

        } catch (IOException e) {
            System.err.println("Could not read test file for quality analysis: " + file);
        }
        return issues;
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
