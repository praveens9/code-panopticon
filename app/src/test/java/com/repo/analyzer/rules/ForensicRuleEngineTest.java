package com.repo.analyzer.rules;

import com.repo.analyzer.core.AnalyzerConfig;
import com.repo.analyzer.core.FileMetrics;
import com.repo.analyzer.git.SocialForensics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForensicRuleEngineTest {

    private final ForensicRuleEngine engine = new ForensicRuleEngine();
    private final AnalyzerConfig config = new AnalyzerConfig(); // Uses defaults

    @Test
    void testShotgunSurgery() {
        // Condition: coupledPeers > 10
        String verdict = engine.evaluate(
                FileMetrics.basic("test.py", "python", 100, 5),
                5, // churn
                5, // recentChurn
                11, // coupledPeers (> 10)
                config);
        assertEquals("SHOTGUN_SURGERY", verdict);
    }

    @Test
    void testHiddenDependency() {
        // Condition: coupledPeers > 3 && fanOut < 5
        FileMetrics metrics = new FileMetrics(
                "hidden.py", "python", 100, 5,
                10.0, 2.0, 1.0,
                2, // fanOut < 5
                List.of(), Map.of());

        String verdict = engine.evaluate(
                metrics,
                10,
                5,
                4, // coupledPeers > 3
                config);
        assertEquals("HIDDEN_DEPENDENCY", verdict);
    }

    @Test
    void testGodClassComplexity() {
        // Condition: totalCC > 200
        FileMetrics metrics = new FileMetrics(
                "God.java", "java", 1000, 50,
                201.0, 10.0, 1.0, 10,
                List.of(), Map.of());

        String verdict = engine.evaluate(metrics, 10, 5, 0, config);
        assertEquals("GOD_CLASS", verdict);
    }

    @Test
    void testTotalMess() {
        // Condition: LCOM4 > 4 (Cohesion < 0.25)
        FileMetrics metrics = new FileMetrics(
                "Mess.java", "java", 200, 20,
                50.0, 5.0,
                0.2, // cohesion = 0.2 -> LCOM4 = 5.0
                5,
                List.of(), Map.of());

        String verdict = engine.evaluate(metrics, 5, 5, 0, config);
        assertEquals("TOTAL_MESS", verdict);
    }

    @Test
    void testBloated() {
        // Condition: LOC > bloatedLoc (default 500)
        FileMetrics metrics = FileMetrics.basic("Big.java", "java", 600, 20); // LOC 600

        String verdict = engine.evaluate(metrics, 5, 5, 0, config);
        assertEquals("BLOATED", verdict);
    }

    @Test
    void testOkFile() {
        // Everything within limits
        FileMetrics metrics = FileMetrics.basic("Clean.java", "java", 100, 5);

        String verdict = engine.evaluate(metrics, 1, 1, 0, config);
        assertEquals("OK", verdict);
    }

    @Test
    void testKnowledgeIsland() {
        // Condition: SocialForensics.isKnowledgeIsland() == true
        FileMetrics metrics = FileMetrics.basic("Secret.java", "java", 100, 5);
        SocialForensics social = new SocialForensics(
                20, "DevA", 0.8, 0, 0, 1, true, false, List.of());

        ForensicRuleEngine.EvaluationContext ctx = new ForensicRuleEngine.EvaluationContext(
                metrics, 5, 5, 0, config, social, false);

        assertEquals("KNOWLEDGE_ISLAND", engine.evaluate(ctx));
    }

    @Test
    void testUntestedHotspot() {
        // Condition: isUntestedHotspot == true (and not Knowledge Island)
        FileMetrics metrics = FileMetrics.basic("Risky.java", "java", 100, 5);

        ForensicRuleEngine.EvaluationContext ctx = new ForensicRuleEngine.EvaluationContext(
                metrics, 5, 5, 0, config, SocialForensics.empty(), true);

        assertEquals("UNTESTED_HOTSPOT", engine.evaluate(ctx));
    }
}
