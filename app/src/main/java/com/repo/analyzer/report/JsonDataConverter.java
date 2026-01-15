package com.repo.analyzer.report;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts AnalysisData to various JSON formats for the HTML report.
 * Extracted from HtmlReporter to improve separation of concerns.
 */
public class JsonDataConverter {

    /**
     * Converts analysis data to JSON array for the main data visualization.
     */
    public String convertToDataJson(List<AnalysisData> data) {
        return data.stream()
                .map(this::dataToJson)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String dataToJson(AnalysisData d) {
        return String.format(
                "{ \"x\": %d, \"y\": %.2f, \"r\": %.2f, \"label\": \"%s\", \"cohesion\": %.2f, \"maxCC\": %.2f, \"coupled\": %d, "
                        +
                        "\"verdict\": \"%s\", \"isDataClass\": %b, \"brainMethods\": [%s], \"lcom4Blocks\": %s, \"churn\": %d, "
                        +
                        "\"recentChurn\": %d, \"lcom4\": %.2f, \"fanOut\": %.0f, \"afferentCoupling\": %.0f, \"instability\": %.2f, "
                        +
                        "\"loc\": %.0f, \"riskScore\": %.2f, \"daysSinceLastCommit\": %d, \"authorCount\": %d, " +
                        "\"primaryAuthor\": \"%s\", \"primaryAuthorPercentage\": %.1f, \"busFactor\": %d, \"isKnowledgeIsland\": %b, "
                        +
                        "\"hasTestFile\": %b, \"testFilePath\": \"%s\", \"testabilityScore\": %d, \"isUntestedHotspot\": %b }",
                d.churn(), d.totalCC(), Math.sqrt(d.methodCount()) * 3, escapeJson(d.className()),
                d.avgFields(), d.maxCC(), d.coupledPeers(), d.verdict(), d.isDataClass(),
                formatBrainMethods(d.brainMethods()),
                formatLcom4Blocks(d.lcom4Blocks()),
                d.churn(), d.recentChurn(), d.lcom4(),
                d.fanOut(), d.afferentCoupling(), d.instability(), d.loc(), d.riskScore(),
                d.daysSinceLastCommit(), d.authorCount(),
                escapeJson(d.primaryAuthor()),
                d.primaryAuthorPercentage(), d.busFactor(), d.isKnowledgeIsland(),
                d.hasTestFile(), escapeJson(d.testFilePath()), d.testabilityScore(), d.isUntestedHotspot());
    }

    private String formatBrainMethods(List<String> methods) {
        return methods.stream()
                .map(s -> "\"" + escapeJson(s) + "\"")
                .collect(Collectors.joining(", "));
    }

    private String formatLcom4Blocks(List<? extends Collection<String>> blocks) {
        return blocks.stream()
                .map(block -> block.stream()
                        .map(s -> "\"" + escapeJson(s) + "\"")
                        .collect(Collectors.joining(", ", "[", "]")))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Converts analysis data to hierarchical JSON for the treemap/circle-pack view.
     */
    public String convertToHierarchyJson(List<AnalysisData> data) {
        HierarchyNode root = new HierarchyNode("root", "root");

        for (AnalysisData d : data) {
            String[] parts = d.className().split("/");
            // Group root-level files to avoid clutter
            if (parts.length == 1) {
                parts = new String[] { "Root Files", parts[0] };
            }

            HierarchyNode current = root;

            // Build/Traverse the tree structure
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isFile = (i == parts.length - 1);
                String fullPath = d.className();

                HierarchyNode child = current.findChild(part);
                if (child == null) {
                    child = new HierarchyNode(part, isFile ? fullPath : part);
                    current.children.add(child);
                }
                current = child;

                // If it's a leaf (file), add the data
                if (isFile) {
                    current.value = d.loc();
                    current.riskScore = d.riskScore();
                    current.verdict = d.verdict();
                    current.complexity = d.totalCC();
                    current.churn = d.churn();
                }
            }
        }
        return root.toJson();
    }

    /**
     * Converts analysis data to network graph JSON for the coupling visualization.
     */
    public String convertToNetworkJson(List<AnalysisData> data) {
        // Only show top 50 riskiest classes to keep graph manageable
        List<AnalysisData> topRisky = data.stream()
                .filter(d -> d.coupledPeers() > 0)
                .sorted((a, b) -> Double.compare(b.riskScore(), a.riskScore()))
                .limit(50)
                .collect(Collectors.toList());

        StringBuilder nodes = new StringBuilder("[");
        StringBuilder links = new StringBuilder("[");

        Set<String> nodeIds = new HashSet<>();
        boolean firstNode = true;

        for (AnalysisData d : topRisky) {
            if (!firstNode)
                nodes.append(",");
            firstNode = false;

            nodes.append(String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"riskScore\":%.2f,\"coupled\":%d,\"verdict\":\"%s\"}",
                    escapeJson(d.className()), escapeJson(d.className()),
                    d.riskScore(), d.coupledPeers(), d.verdict()));
            nodeIds.add(d.className());
        }
        nodes.append("]");

        // Create links based on temporal coupling
        boolean firstLink = true;
        for (AnalysisData source : topRisky) {
            for (String coupledClassName : source.coupledClassNames()) {
                if (nodeIds.contains(coupledClassName) && source.className().compareTo(coupledClassName) < 0) {
                    if (!firstLink)
                        links.append(",");
                    firstLink = false;

                    links.append(String.format(
                            "{\"source\":\"%s\",\"target\":\"%s\",\"value\":1}",
                            escapeJson(source.className()), escapeJson(coupledClassName)));
                }
            }
        }
        links.append("]");

        return String.format("{\"nodes\":%s,\"links\":%s}", nodes, links);
    }

    private String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Inner class for building hierarchy
    private static class HierarchyNode {
        String name;
        String fullName;
        List<HierarchyNode> children = new ArrayList<>();
        Double value;
        Double riskScore;
        Double complexity;
        Integer churn;
        String verdict;

        HierarchyNode(String name, String fullName) {
            this.name = name;
            this.fullName = fullName;
        }

        HierarchyNode findChild(String name) {
            for (HierarchyNode c : children) {
                if (c.name.equals(name))
                    return c;
            }
            return null;
        }

        String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"name\":\"").append(name).append("\"");

            if (!children.isEmpty()) {
                sb.append(",\"children\":[");
                sb.append(children.stream().map(HierarchyNode::toJson).collect(Collectors.joining(",")));
                sb.append("]");
            } else {
                sb.append(",\"value\":").append(value != null ? value : 0);
                sb.append(",\"riskScore\":").append(riskScore != null ? riskScore : 0);
                sb.append(",\"complexity\":").append(complexity != null ? complexity : 0);
                sb.append(",\"churn\":").append(churn != null ? churn : 0);
                sb.append(",\"verdict\":\"").append(verdict != null ? verdict : "OK").append("\"");
                sb.append(",\"fullName\":\"").append(fullName != null ? fullName : "").append("\"");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
