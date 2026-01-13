package com.repo.analyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testDefaults() {
        AnalyzerConfig config = new AnalyzerConfig();
        assertEquals(20, config.getTotalMessChurn());
        assertEquals(500, config.getBloatedLoc());
        assertTrue(config.getExclusions().contains("**/node_modules/**"));
    }

    @Test
    void testYamlLoading() throws IOException {
        Path yamlFile = tempDir.resolve("panopticon.yaml");
        String yamlContent = """
                thresholds:
                  bloated:
                    loc: 1000
                  total_mess:
                    churn: 50
                exclusions:
                  - "**/secret/**"
                """;
        Files.writeString(yamlFile, yamlContent);

        AnalyzerConfig config = AnalyzerConfig.load(tempDir);

        assertEquals(1000, config.getBloatedLoc(), "Should override default bloated LOC");
        assertEquals(50, config.getTotalMessChurn(), "Should override default churn");
        assertTrue(config.getExclusions().contains("**/secret/**"), "Should contain custom exclusion");
        assertEquals(1, config.getExclusions().size(), "Should verify exclusion list replacement or addition behavior");
    }
}
