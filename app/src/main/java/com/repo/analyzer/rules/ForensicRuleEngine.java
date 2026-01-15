package com.repo.analyzer.rules;

import com.repo.analyzer.core.AnalyzerConfig;
import com.repo.analyzer.core.FileMetrics;
import com.repo.analyzer.git.SocialForensics;

import java.util.*;

/**
 * Threshold-based forensic rule engine.
 * Evaluates metrics against configurable thresholds to determine verdicts.
 * Designed to be extensible for future expression-based DSL.
 */
public class ForensicRuleEngine {

    /**
     * Functional interface for rule conditions.
     * Allows easy extension to expression-based evaluation.
     */
    @FunctionalInterface
    public interface RuleCondition {
        boolean evaluate(EvaluationContext ctx);
    }

    /**
     * A verdict rule with priority.
     */
    public record VerdictRule(
            String verdictName,
            int priority,
            RuleCondition condition,
            String description) {
    }

    /**
     * Context for rule evaluation.
     * Includes structural metrics, evolutionary data, and social forensics.
     */
    public record EvaluationContext(
            FileMetrics metrics,
            int churn,
            int recentChurn,
            int coupledPeers,
            AnalyzerConfig config,
            SocialForensics social,
            boolean isUntestedHotspot) {

        // Convenience constructor without social/testability (backward compatibility)
        public EvaluationContext(
                FileMetrics metrics, int churn, int recentChurn,
                int coupledPeers, AnalyzerConfig config) {
            this(metrics, churn, recentChurn, coupledPeers, config, SocialForensics.empty(), false);
        }

        // Constructor for compatibility with old tests causing lint errors
        public EvaluationContext(
                FileMetrics metrics, int churn, int recentChurn,
                int coupledPeers, AnalyzerConfig config, SocialForensics social) {
            this(metrics, churn, recentChurn, coupledPeers, config, social, false);
        }

        public double totalCC() {
            return metrics.totalComplexity();
        }

        public double maxCC() {
            return metrics.maxComplexity();
        }

        public double cohesion() {
            return metrics.cohesion();
        }

        public int fanOut() {
            return metrics.fanOut();
        }

        public int loc() {
            return metrics.loc();
        }

        public int methods() {
            return metrics.functionCount();
        }

        public double lcom4() {
            // Invert cohesion back to LCOM4-like value
            return metrics.cohesion() > 0 ? 1.0 / metrics.cohesion() : 1.0;
        }

        // Social forensics accessors
        public int busFactor() {
            return social.busFactor();
        }

        public boolean isKnowledgeIsland() {
            return social.isKnowledgeIsland();
        }

        public boolean isCoordinationBottleneck() {
            return social.isCoordinationBottleneck();
        }

        public int authorCount() {
            return social.authorCount();
        }

        public <T> T getExtra(String key, T defaultValue) {
            return metrics.getExtra(key, defaultValue);
        }
    }

    private final List<VerdictRule> rules;

    /**
     * Create engine with default forensic rules.
     */
    public ForensicRuleEngine() {
        this.rules = buildDefaultRules();
    }

    /**
     * Create engine with custom rules.
     */
    public ForensicRuleEngine(List<VerdictRule> customRules) {
        this.rules = new ArrayList<>(customRules);
        this.rules.sort(Comparator.comparingInt(VerdictRule::priority));
    }

    /**
     * Evaluate all rules and return the highest-priority matching verdict.
     */
    public String evaluate(EvaluationContext ctx) {
        for (VerdictRule rule : rules) {
            try {
                if (rule.condition().evaluate(ctx)) {
                    return rule.verdictName();
                }
            } catch (Exception e) {
                // Skip rule if evaluation fails
            }
        }
        return "OK";
    }

    /**
     * Convenience method to evaluate with minimal context.
     */
    public String evaluate(FileMetrics metrics, int churn, int recentChurn, int coupledPeers, AnalyzerConfig config) {
        return evaluate(new EvaluationContext(metrics, churn, recentChurn, coupledPeers, config));
    }

    /**
     * Get all rules for inspection/debugging.
     */
    public List<VerdictRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Build default forensic rules matching existing logic.
     */
    private List<VerdictRule> buildDefaultRules() {
        List<VerdictRule> defaultRules = new ArrayList<>();

        // Priority 0: Knowledge Island (social risk - highest priority)
        defaultRules.add(new VerdictRule(
                "KNOWLEDGE_ISLAND",
                0,
                ctx -> ctx.isKnowledgeIsland(),
                "Single developer dependency with inactive expert"));

        // Priority 1: Shotgun Surgery (changes ripple everywhere)
        defaultRules.add(new VerdictRule(
                "SHOTGUN_SURGERY",
                1,
                ctx -> ctx.coupledPeers() > 10,
                "Changes to this file require editing >10 other files"));

        // Priority 2: Untested Hotspot (Testability risk)
        defaultRules.add(new VerdictRule(
                "UNTESTED_HOTSPOT",
                2,
                ctx -> ctx.isUntestedHotspot,
                "High risk, high churn, and NO tests"));

        // Priority 3: Hidden Dependency
        defaultRules.add(new VerdictRule(
                "HIDDEN_DEPENDENCY",
                3,
                ctx -> ctx.coupledPeers() > 3 && ctx.fanOut() < 5,
                "High temporal coupling but low static imports"));

        // Priority 3: God Class (extreme cases)
        defaultRules.add(new VerdictRule(
                "GOD_CLASS",
                3,
                ctx -> ctx.totalCC() > 200 || (ctx.methods() > 50 && ctx.cohesion() < 0.5),
                "Extremely complex class with many responsibilities"));

        // Priority 4: God Class (coupling variant)
        defaultRules.add(new VerdictRule(
                "GOD_CLASS (Coupling)",
                4,
                ctx -> ctx.fanOut() > 50,
                "Depends on too many other types"));

        // Priority 5: Total Mess (very low cohesion)
        defaultRules.add(new VerdictRule(
                "TOTAL_MESS",
                5,
                ctx -> ctx.lcom4() > 4,
                "Contains multiple unrelated clusters of logic"));

        // Priority 6: Brain Method
        defaultRules.add(new VerdictRule(
                "BRAIN_METHOD",
                6,
                ctx -> ctx.maxCC() > ctx.config().getBrainMethodMaxCC() && !ctx.metrics().complexFunctions().isEmpty(),
                "Contains massive, dense algorithm methods"));

        // Priority 7: Complex (Low Risk)
        defaultRules.add(new VerdictRule(
                "COMPLEX (Low Risk)",
                7,
                ctx -> ctx.maxCC() > 15,
                "High complexity but possibly boilerplate"));

        // Priority 8: Fragile Hub
        defaultRules.add(new VerdictRule(
                "FRAGILE_HUB",
                8,
                ctx -> ctx.fanOut() > 30 && ctx.churn() > 10,
                "Central coordinator that changes frequently"));

        // Priority 9: Split Candidate
        defaultRules.add(new VerdictRule(
                "SPLIT_CANDIDATE",
                9,
                ctx -> ctx.lcom4() > ctx.config().getSplitCandidateLcom4(),
                "Contains disconnected clusters that could be separate classes"));

        // Priority 10: High Coupling
        defaultRules.add(new VerdictRule(
                "HIGH_COUPLING",
                10,
                ctx -> ctx.fanOut() > 30,
                "Too many dependencies"));

        // Priority 11: Bloated
        defaultRules.add(new VerdictRule(
                "BLOATED",
                11,
                ctx -> ctx.loc() > ctx.config().getBloatedLoc(),
                "File is too large"));

        return defaultRules;
    }

    /**
     * Add a custom rule dynamically.
     */
    public void addRule(VerdictRule rule) {
        rules.add(rule);
        rules.sort(Comparator.comparingInt(VerdictRule::priority));
    }

    /**
     * Create a simple threshold rule.
     * For future DSL: this could parse expressions like "churn > 10 AND fanOut < 5"
     */
    public static VerdictRule thresholdRule(String name, int priority, String description,
            String metric, String operator, double threshold) {
        RuleCondition condition = ctx -> {
            double value = switch (metric.toLowerCase()) {
                case "churn" -> ctx.churn();
                case "recentchurn" -> ctx.recentChurn();
                case "peers", "coupledpeers" -> ctx.coupledPeers();
                case "totalcc", "complexity" -> ctx.totalCC();
                case "maxcc" -> ctx.maxCC();
                case "cohesion" -> ctx.cohesion();
                case "lcom4" -> ctx.lcom4();
                case "fanout" -> ctx.fanOut();
                case "loc" -> ctx.loc();
                case "methods" -> ctx.methods();
                default -> 0;
            };

            return switch (operator) {
                case ">" -> value > threshold;
                case ">=" -> value >= threshold;
                case "<" -> value < threshold;
                case "<=" -> value <= threshold;
                case "==" -> value == threshold;
                default -> false;
            };
        };

        return new VerdictRule(name, priority, condition, description);
    }
}
