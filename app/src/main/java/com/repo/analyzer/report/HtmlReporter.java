package com.repo.analyzer.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class HtmlReporter {

    public void generate(List<AnalysisData> data, Path outputPath) {
        String json = convertToJson(data);
        String treemapJson = convertToHierarchyJson(data);
        String networkJson = convertToNetworkJson(data);

        String html = new StringBuilder(TEMPLATE_HEAD).append(TEMPLATE_BODY).toString()
                .replace("{{DATA_PLACEHOLDER}}", json)
                .replace("{{TREEMAP_DATA}}", treemapJson)
                .replace("{{NETWORK_DATA}}", networkJson);

        try {
            Files.writeString(outputPath, html);
            System.out.println("Report generated at: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    String convertToJson(List<AnalysisData> data) {
        return data.stream()
                .map(d -> String.format(
                        "{ x: %d, y: %.2f, r: %.2f, label: '%s', cohesion: %.2f, maxCC: %.2f, coupled: %d, verdict: '%s', isDataClass: %b, brainMethods: [%s], lcom4Blocks: %s, churn: %d, recentChurn: %d, lcom4: %.2f, fanOut: %.0f, afferentCoupling: %.0f, instability: %.2f, loc: %.0f, riskScore: %.2f }",
                        d.churn(), d.totalCC(), Math.sqrt(d.methodCount()) * 3, d.className(), d.avgFields(), d.maxCC(),
                        d.coupledPeers(), d.verdict(), d.isDataClass(),
                        d.brainMethods().stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ")),
                        d.lcom4Blocks().stream()
                                .map(block -> block.stream().map(s -> "'" + s + "'")
                                        .collect(Collectors.joining(", ", "[", "]")))
                                .collect(Collectors.joining(", ", "[", "]")),
                        d.churn(), d.recentChurn(), d.lcom4(),
                        d.fanOut(), d.afferentCoupling(), d.instability(), d.loc(), d.riskScore()))
                .collect(Collectors.joining(", ", "[", "]"));
    }

    String convertToHierarchyJson(List<AnalysisData> data) {
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
                String fullPath = d.className(); // Simplified, conceptually path up to here

                HierarchyNode child = current.findChild(part);
                if (child == null) {
                    child = new HierarchyNode(part, isFile ? fullPath : part); // Only leaves need full path really
                    current.children.add(child);
                }
                current = child;

                // If it's a leaf (file), add the data
                if (isFile) {
                    current.value = d.loc(); // Size = LOC
                    current.riskScore = d.riskScore();
                    current.verdict = d.verdict();
                    current.complexity = d.totalCC();
                    current.churn = d.churn();
                }
            }
        }
        return root.toJson();
    }

    private static class HierarchyNode {
        String name;
        String fullName;
        List<HierarchyNode> children = new ArrayList<>();
        Double value; // LOC
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
                // Leaf properties
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

    private String convertToNetworkJson(List<AnalysisData> data) {
        // Only show top 50 riskiest classes to keep graph manageable
        List<AnalysisData> topRisky = data.stream()
                .filter(d -> d.coupledPeers() > 0)
                .sorted((a, b) -> Double.compare(b.riskScore(), a.riskScore()))
                .limit(50)
                .collect(Collectors.toList());

        StringBuilder nodes = new StringBuilder("[");
        StringBuilder links = new StringBuilder("[");

        boolean firstNode = true;
        Map<String, Integer> nodeIndex = new HashMap<>();
        Set<String> nodeIds = new HashSet<>();
        int index = 0;

        for (AnalysisData d : topRisky) {
            if (!firstNode)
                nodes.append(",");
            firstNode = false;

            String shortName = d.className(); // Use full path as requested
            nodes.append(String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"riskScore\":%.2f,\"coupled\":%d,\"verdict\":\"%s\"}",
                    d.className(), shortName, d.riskScore(), d.coupledPeers(), d.verdict()));
            nodeIndex.put(d.className(), index++);
            nodeIds.add(d.className());
        }
        nodes.append("]");

        // Create links: connect classes based on REAL temporal coupling
        boolean firstLink = true;
        for (AnalysisData source : topRisky) {
            for (String coupledClassName : source.coupledClassNames()) {
                // Check if coupled class is also in our top risky list (to keep graph connected
                // and relevant)
                // or if we want to show it even if it's not "risky" itself?
                // For simplicity and graph cleanliness, only link if both are in the graph or
                // if it's one level deep.
                // Let's stick to linking nodes that exist in our graph.

                if (nodeIds.contains(coupledClassName) && source.className().compareTo(coupledClassName) < 0) {
                    // Avoid duplicate links (A->B and B->A) by comparing class names
                    if (!firstLink)
                        links.append(",");
                    firstLink = false;

                    links.append(String.format(
                            "{\"source\":\"%s\",\"target\":\"%s\",\"value\":1}", // Value could be coupling strength if
                                                                                 // we tracked it per-pair
                            source.className(), coupledClassName));
                }
            }
        }
        links.append("]");

        return String.format("{\"nodes\":%s,\"links\":%s}", nodes, links);
    }

    private static final String TEMPLATE_HEAD = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Project Panopticon - Code Forensics</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@2.2.1"></script>
                <script src="https://d3js.org/d3.v7.min.js"></script>
                <style>
                    * { box-sizing: border-box; }
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 0; margin: 0; background: #f4f7f6; display: flex; height: 100vh; overflow: hidden; }
                    .main-content { flex: 1; padding: 20px; overflow-y: auto; }
                    .container { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); margin-bottom: 20px; }
                    h1 { color: #2c3e50; margin-top: 0; }

                    /* Tabs */
                    .tabs { display: flex; gap: 10px; margin-bottom: 20px; border-bottom: 2px solid #ddd; }
                    .tab { padding: 10px 20px; cursor: pointer; border: none; background: none; font-size: 1rem; color: #7f8c8d; transition: all 0.3s; }
                    .tab.active { color: #2c3e50; border-bottom: 3px solid #3498db; font-weight: bold; }
                    .tab:hover { color: #2c3e50; }

                    .tab-content { display: none; }
                    .tab-content.active { display: block; }

                    /* Chart Area */
                    .chart-container { position: relative; height: 60vh; width: 100%; }

                    /* System Map (Circle Packing) */
                    #treemap { width: 100%; height: 75vh; display: block; background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-radius: 12px; }
                    .node { cursor: pointer; transition: all 0.2s ease; }
                    .node:hover { stroke-width: 3px !important; }
                    .node--leaf { transition: all 0.2s ease; }
                    .node--leaf:hover { filter: drop-shadow(0 0 8px currentColor) brightness(1.1); }
                    .label { font: 11px "Helvetica Neue", Helvetica, Arial, sans-serif; text-anchor: middle; text-shadow: 0 1px 0 #fff, 1px 0 0 #fff, 0 -1px 0 #fff, -1px 0 0 #fff; pointer-events: none; fill: #2c3e50; font-weight: bold; }
                    .label, .node--root { pointer-events: none; }

                    /* Wayfinding Breadcrumb Bar */
                    #breadcrumbs {
                        font-size: 0.95rem;
                        padding: 10px 16px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        border-radius: 24px;
                        margin-bottom: 12px;
                        display: inline-flex;
                        align-items: center;
                        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
                    }
                    .crumb {
                        cursor: pointer;
                        color: rgba(255,255,255,0.85);
                        padding: 4px 10px;
                        border-radius: 12px;
                        transition: all 0.2s ease;
                        font-weight: 500;
                    }
                    .crumb:hover { background: rgba(255,255,255,0.2); color: #fff; }
                    .crumb:last-child { color: #fff; cursor: default; font-weight: 700; background: rgba(255,255,255,0.15); }
                    .crumb-separator { color: rgba(255,255,255,0.5); margin: 0 2px; font-size: 0.8rem; }

                    /* Virtual Lens Tooltip */
                    .virtual-lens {
                        position: fixed;
                        background: rgba(20, 20, 30, 0.92);
                        backdrop-filter: blur(12px);
                        -webkit-backdrop-filter: blur(12px);
                        border: 1px solid rgba(255,255,255,0.1);
                        border-radius: 12px;
                        padding: 14px 18px;
                        color: #fff;
                        font-size: 13px;
                        pointer-events: none;
                        z-index: 1000;
                        box-shadow: 0 10px 40px rgba(0,0,0,0.4), 0 0 0 1px rgba(255,255,255,0.05) inset;
                        max-width: 300px;
                        opacity: 0;
                        transition: opacity 0.15s ease;
                    }
                    .virtual-lens .lens-title {
                        font-size: 14px;
                        font-weight: 700;
                        margin-bottom: 10px;
                        padding-bottom: 8px;
                        border-bottom: 1px solid rgba(255,255,255,0.1);
                        word-break: break-word;
                    }
                    .virtual-lens .lens-metrics {
                        display: grid;
                        grid-template-columns: repeat(3, 1fr);
                        gap: 8px;
                        margin-bottom: 10px;
                    }
                    .virtual-lens .lens-metric {
                        text-align: center;
                        background: rgba(255,255,255,0.05);
                        padding: 6px 4px;
                        border-radius: 6px;
                    }
                    .virtual-lens .lens-metric-value {
                        font-size: 16px;
                        font-weight: 700;
                        display: block;
                    }
                    .virtual-lens .lens-metric-label {
                        font-size: 10px;
                        color: rgba(255,255,255,0.6);
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .virtual-lens .lens-verdict {
                        display: inline-block;
                        padding: 4px 10px;
                        border-radius: 6px;
                        font-size: 11px;
                        font-weight: 700;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }

                    /* Network Graph */
                    #network-container { width: 100%; height: 60vh; overflow: auto; position: relative; border: 1px solid #ddd; border-radius: 4px; background: #fafafa; }
                    #network { display: block; }
                    .link { stroke: #999; stroke-opacity: 0.6; stroke-width: 1px; }


                    /* DataTable - Scrollable with Sticky Header */
                    #table-tab { max-height: 70vh; overflow-y: auto; }
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; table-layout: fixed; }
                    th { resize: horizontal; overflow: auto; min-width: 80px; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
                    th { background: #34495e; color: white; cursor: pointer; user-select: none; position: sticky; top: 0; z-index: 1; }
                    th:hover { background: #2c3e50; }
                    tr:hover { background: #f5f5f5; cursor: pointer; }
                    td:first-child { max-width: 350px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                    td:first-child:hover { overflow: visible; white-space: normal; word-break: break-all; background: #fff; position: relative; z-index: 2; box-shadow: 0 2px 8px rgba(0,0,0,0.15); }
                    .filter-row { display: flex; gap: 10px; margin-bottom: 15px; flex-wrap: wrap; }
                    .filter-row input, .filter-row select { padding: 8px; border: 1px solid #ddd; border-radius: 4px; }

                    /* Side Panel */
                    #detailsPanel {
                        width: 400px;
                        background: white;
                        border-left: 1px solid #ddd;
                        padding: 25px;
                        box-shadow: -2px 0 15px rgba(0,0,0,0.05);
                        display: none;
                        flex-direction: column;
                        overflow-y: auto;
                    }

                    #detailsPanel.active { display: flex !important; }
                    .panel-header { border-bottom: 2px solid #f0f0f0; padding-bottom: 15px; margin-bottom: 15px; }
                    .panel-header h2 { margin: 0; font-size: 1.2rem; word-break: break-all; color: #34495e; }
                    .verdict-badge { display: inline-block; padding: 5px 10px; border-radius: 4px; color: white; font-weight: bold; font-size: 0.8rem; margin-top: 10px; }

                    /* Verdict Colors - Modern Pastel Palette */
                    .verdict-TOTAL_MESS { background-color: #ff7675; }
                    .verdict-SHOTGUN_SURGERY { background-color: #e17055; }
                    .verdict-BRAIN_METHOD { background-color: #a29bfe; }
                    .verdict-GOD_CLASS { background-color: #636e72; }
                    .verdict-HIDDEN_DEPENDENCY { background-color: #fdcb6e; color: #2d3436; }
                    .verdict-HIGH_COUPLING { background-color: #ffeaa7; color: #2d3436; }
                    .verdict-COMPLEX { background-color: #fab1a0; color: #2d3436; }
                    .verdict-SPLIT_CANDIDATE { background-color: #d63031; }
                    .verdict-FRAGILE_HUB { background-color: #e17055; }
                    .verdict-BLOATED { background-color: #ff7675; }
                    .verdict-DATA_CLASS { background-color: #74b9ff; }
                    .verdict-CONFIGURATION { background-color: #a29bfe; }
                    .verdict-ORCHESTRATOR { background-color: #81ecec; color: #2d3436; }
                    .verdict-OK { background-color: #00b894; }

                    .stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 20px; }
                    .stat-item { background: #f8f9fa; padding: 10px; border-radius: 4px; text-align: center; }
                    .stat-val { display: block; font-weight: bold; font-size: 1.1rem; color: #2c3e50; }
                    .stat-label { font-size: 0.75rem; color: #7f8c8d; text-transform: uppercase; }

                    .forensic-report { background: #e8f6f3; padding: 15px; border-left: 4px solid #1abc9c; border-radius: 4px; margin-bottom: 20px; }
                    .forensic-report h3 { margin-top: 0; font-size: 1rem; color: #16a085; }
                    .forensic-report h3::before { content: '🕵️'; margin-right: 8px; }
                    .forensic-report p { margin: 0 0 10px 0; line-height: 1.5; font-size: 0.95rem; color: #34495e; }

                    .tips { padding: 15px; background: #fff8e1; border-left: 4px solid #f1c40f; border-radius: 4px; font-size: 0.9rem; color: #7f8c8d; }
                    .tips h4 { margin: 0 0 5px 0; color: #f39c12; font-size: 0.9rem; }

                    .close-btn { align-self: flex-end; cursor: pointer; color: #999; font-size: 1.5rem; line-height: 1; border: none; background: none; }
                    .close-btn:hover { color: #555; }

                    /* System Map HUD - Fixed Hover Inspector */
                    #system-map-hud {
                        position: absolute;
                        bottom: 20px;
                        left: 20px;
                        width: 300px;
                        background: rgba(255, 255, 255, 0.95);
                        border: 1px solid #e0e0e0;
                        border-radius: 8px;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.1);
                        padding: 15px;
                        z-index: 1000;
                        backdrop-filter: blur(5px);
                        display: none; /* Hidden by default */
                        transition: opacity 0.2s ease;
                    }
                    #system-map-hud h3 { margin: 0 0 10px 0; font-size: 14px; color: #2c3e50; border-bottom: 1px solid #eee; padding-bottom: 5px; }
                    .hud-row { display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 12px; }
                    .hud-label { color: #7f8c8d; }
                    .hud-value { font-weight: 600; color: #2d3436; }
                    .hud-verdict { margin-top: 10px; padding: 4px 8px; border-radius: 4px; text-align: center; font-weight: bold; font-size: 11px; color: #fff; }

                    .legend { display: flex; gap: 20px; margin-top: 15px; flex-wrap: wrap; }
                    .legend-item { display: flex; align-items: center; gap: 5px; font-size: 0.9rem; }
                    .legend-color { width: 20px; height: 20px; border-radius: 3px; }
                </style>
            </head>
            """;

    private static String TEMPLATE_BODY_PART1 = """
                                <body>
                                    <div id="wrapper" style="display: flex; width: 100%; height: 100%;">
                                        <div class="main-content">
                                            <div class="container">
                                                <h1>Code Forensics: Risk Analysis</h1>

                                                <div class="tabs">
                                                    <button class="tab active" onclick="switchTab('quadrant')">Quadrant View</button>
                                                    <button class="tab" onclick="switchTab('table')">Data Table</button>
                                                    <button class="tab" onclick="switchTab('treemap')">System Map</button>
                                                    <button class="tab" onclick="switchTab('network')">Network</button>
                                                </div>

                                                <!-- Quadrant Tab -->
                                                <div id="quadrant-tab" class="tab-content active">
                                                    <p><strong>X-Axis:</strong> Churn | <strong>Y-Axis:</strong> Complexity | <strong>Size:</strong> Methods</p>
                                                    <div class="chart-container">
                                                        <canvas id="riskChart"></canvas>
                                                    </div>
                                                    <div class="legend">
                                                        <div class="legend-item"><div class="legend-color" style="background: rgba(231, 76, 60, 0.3);"></div><span>🔴 Burning Platform</span></div>
                                                        <div class="legend-item"><div class="legend-color" style="background: rgba(241, 196, 15, 0.3);"></div><span>🟡 Complex but Stable</span></div>
                                                        <div class="legend-item"><div class="legend-color" style="background: rgba(46, 204, 113, 0.3);"></div><span>🟢 Healthy</span></div>
                                                    </div>
                                                </div>

                                                <!-- Table Tab -->
                                                <div id="table-tab" class="tab-content">
                                                    <div class="filter-row">
                                                        <input type="text" id="classFilter" placeholder="Filter by class name..." style="flex: 1;">
                                                        <select id="verdictFilter">
                                                            <option value="">All Verdicts</option>
                                                            <option value="TOTAL_MESS">TOTAL_MESS</option>
                                                            <option value="SHOTGUN_SURGERY">SHOTGUN_SURGERY</option>
                                                            <option value="BRAIN_METHOD">BRAIN_METHOD</option>
                                                            <option value="GOD_CLASS">GOD_CLASS</option>
                                                        </select>
                                                    </div>
                                                    <table id="dataTable">
                                                        <thead>
                                                            <tr>
                                                                <th onclick="sortTable(0)">Class Name ▼</th>
                                                                <th onclick="sortTable(1)">Churn ▼</th>
                                                                <th onclick="sortTable(2)">Recent Churn ▼</th>
                                                                <th onclick="sortTable(3)">Risk Score ▼</th>
                                                                <th onclick="sortTable(4)">Complexity ▼</th>
                                                                <th onclick="sortTable(5)">LCOM4 ▼</th>
                                                                <th onclick="sortTable(6)">Verdict ▼</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody id="tableBody"></tbody>
                                                    </table>
                                                </div>

                                                <!-- System Map Tab -->
                                                <div id="treemap-tab" class="tab-content">
                                                    <div class="row">
                                                        <div class="col-md-12">
                                                            <div class="card">
                                                                <div class="card-header">
                                                                    <div class="breadcrumbs" id="breadcrumbs">
                                                                        <span class="crumb active">root</span>
                                                                    </div>
                                                                </div>
                                                                <div class="card-body">
                                                                    <div id="treemap-container">
                                                                        <div id="loading">Loading visualization...</div>
                                                                        <svg id="treemap"></svg>
                                                                        <div id="system-map-hud">
                                                                            <!-- HUD Content populated by JS -->
                                                                            <h3>Hover over a file</h3>
                                                                            <div class="hud-row"><span class="hud-label">Select a node to see details</span></div>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div style="margin-bottom: 2px; font-size: 0.9rem; color: #7f8c8d;">
                                                        <strong>Circle Size:</strong> Lines of Code | <strong>Color:</strong> Risk Score
                                                    </div>
                                                    <div style="margin-bottom: 10px; font-size: 0.85rem; color: #95a5a6; font-style: italic;">
                                                        👉 <strong>Click Folder</strong> to Zoom In | <strong>Click File</strong> for Details | <strong>Click Background</strong> to Zoom Out
                                                    </div>
                                                </div>

                                                <!-- Network Tab -->
                                                <div id="network-tab" class="tab-content">
                                                    <p><strong>Nodes:</strong> Classes with coupling | <strong>Size:</strong> Risk Score | <strong>Color:</strong> Verdict</p>
                                                    <div id="network-container">
                                                        <svg id="network"></svg>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>

                                        <div id="detailsPanel">
                                            <button class="close-btn" onclick="hideDetails()">&times;</button>
                                            <div id="panelContent"></div>
                                        </div>
                                    </div>

                                    <script>
                                        const rawData = {{DATA_PLACEHOLDER}};
                                        const treemapData = {{TREEMAP_DATA}};
                                        const networkData = {{NETWORK_DATA}};
                                        let currentSort = { column: 3, ascending: false };

                                        // Fix: Hide loading overlay immediately as data is embedded
                                        try { document.getElementById('loading').style.display = 'none'; } catch(e) {}

                                        // Tab switching
                                        function switchTab(tabName) {
                                            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
                                            document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));

                                            event.target.classList.add('active');
                                            document.getElementById(tabName + '-tab').classList.add('active');

                                            if (tabName === 'table') renderTable();
                                            if (tabName === 'treemap') renderTreemap();
                                            if (tabName === 'network') renderNetwork();
                                        }

                                        const getColor = (riskScore) => {
                                            // Keep distinct from getRiskColor if needed for bubble chart, merging logic for consistency
                                            if (riskScore > 20) return 'rgba(231, 76, 60, 0.7)'; // Red
                                            if (riskScore > 5) return 'rgba(241, 196, 15, 0.7)';  // Yellow
                                            return 'rgba(46, 204, 113, 0.7)'; // Green
                                        };

                                        const getRiskColor = (d) => {
                                            // Use Verdict for explicit coloring if available
                                            const v = d.verdict;
                                            if (v === 'TOTAL_MESS' || v === 'GOD_CLASS' || v === 'BLOATED' || v === 'SHOTGUN_SURGERY') return '#e74c3c'; // Red
                                            if (v === 'BRAIN_METHOD' || v === 'COMPLEX' || v === 'SPLIT_CANDIDATE' || v === 'HIGH_COUPLING' || v === 'HIDDEN_DEPENDENCY') return '#f39c12'; // Orange

                                            // Fallback to numeric risk score
                                            const score = d.riskScore || 0;
                                            if (score > 20) return '#e74c3c';
                                            if (score > 5) return '#f39c12';
                                            return '#27ae60'; // Green
                                        };

                                        // Quadrant Chart with Dynamic Axis Scaling
                                        const ctx = document.getElementById('riskChart').getContext('2d');

                                        // Calculate dynamic axis bounds based on data
                                        const maxChurn = Math.max(...rawData.map(d => d.x), 10);
                                        const maxComplexity = Math.max(...rawData.map(d => d.y), 100);
                                        const xAxisMax = Math.ceil(maxChurn * 1.2);
                                        const yAxisMax = Math.ceil(maxComplexity * 1.1);

                                        // Calculate percentile-based dividers (75th percentile)
                                        const sortedChurn = [...rawData.map(d => d.x)].sort((a, b) => a - b);
                                        const sortedComplexity = [...rawData.map(d => d.y)].sort((a, b) => a - b);
                                        const churnDivider = sortedChurn[Math.floor(sortedChurn.length * 0.75)] || 10;
                                        const complexityDivider = sortedComplexity[Math.floor(sortedComplexity.length * 0.5)] || 50;

                                        const backgroundZones = {
                                            id: 'backgroundZones',
                                            beforeDraw: (chart) => {
                                                const ctx = chart.ctx;
                                                const chartArea = chart.chartArea;
                                                const xScale = chart.scales.x;
                                                const yScale = chart.scales.y;
                                                const xMid = xScale.getPixelForValue(churnDivider);
                                                const yMid = yScale.getPixelForValue(complexityDivider);
                                                ctx.save();
                                                ctx.fillStyle = 'rgba(231, 76, 60, 0.1)';
                                                ctx.fillRect(xMid, chartArea.top, chartArea.right - xMid, yMid - chartArea.top);
                                                ctx.fillStyle = 'rgba(241, 196, 15, 0.1)';
                                                ctx.fillRect(chartArea.left, chartArea.top, xMid - chartArea.left, yMid - chartArea.top);
                                                ctx.fillStyle = 'rgba(46, 204, 113, 0.1)';
                                                ctx.fillRect(chartArea.left, yMid, chartArea.right - chartArea.left, chartArea.bottom - yMid);
                                                ctx.restore();
                                            }
                                        };

                                        const chart = new Chart(ctx, {
                                            type: 'bubble',
                                            data: {
                                                datasets: [{
                                                    label: 'Classes',
                                                    data: rawData,
                                                    backgroundColor: rawData.map(d => getColor(d.riskScore)),
                                                    borderColor: rawData.map(d => getColor(d.riskScore).replace('0.7', '1')),
                                                    borderWidth: 1
                                                }]
                                            },
                                            options: {
                                                responsive: true,
                                                maintainAspectRatio: false,
                                                onClick: (e) => {
                                                    const points = chart.getElementsAtEventForMode(e, 'nearest', { intersect: true }, true);
                                                    if (points.length) showDetails(rawData[points[0].index]);
                                                },
                                                plugins: {
                                                    tooltip: {
                                                        callbacks: {
                                                            label: function(context) {
                                                                const d = context.raw;
                                                                return [d.label, `Risk: ${d.riskScore.toFixed(1)}`, `Churn: ${d.churn}`, `CC: ${d.y.toFixed(0)}`];
                                                            }
                                                        }
                                                    },
                                                    legend: { display: false },
                                                    annotation: {
                                                        annotations: {
                                                            churnLine: { type: 'line', xMin: churnDivider, xMax: churnDivider, borderColor: 'rgba(0,0,0,0.3)', borderWidth: 2, borderDash: [5,5] },
                                                            complexityLine: { type: 'line', yMin: complexityDivider, yMax: complexityDivider, borderColor: 'rgba(0,0,0,0.3)', borderWidth: 2, borderDash: [5,5] }
                                                        }
                                                    }
                                                },
                                                scales: {
                                                    x: { title: { display: true, text: 'Churn' }, min: 0, max: xAxisMax },
                                                    y: { title: { display: true, text: 'Complexity' }, min: 0, max: yAxisMax }
                                                }
                                            },
                                            plugins: [backgroundZones]
                                        });

                                        // Side Panel Details
                                        function showDetails(d) {
                                            const panel = document.getElementById('detailsPanel');
                                            panel.classList.add('active');
                                            const content = document.getElementById('panelContent');
                                            content.innerHTML = `
                                                <div class="panel-header">
                                                    <h2>${d.label}</h2>
                                                    <span class="verdict-badge verdict-${d.verdict.split(' ')[0]}">${d.verdict}</span>
                                                </div>
                                                <div class="stat-grid">
                                                    <div class="stat-item"><span class="stat-val">${d.riskScore.toFixed(1)}</span><span class="stat-label">Risk Score</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.churn}</span><span class="stat-label">Churn</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.recentChurn}</span><span class="stat-label">Recent Churn</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.y.toFixed(0)}</span><span class="stat-label">Complexity (CC)</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.lcom4.toFixed(1)}</span><span class="stat-label">LCOM4</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.coupled}</span><span class="stat-label">Coupled Peers</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.fanOut.toFixed(0)}</span><span class="stat-label">Fan Out</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.afferentCoupling.toFixed(0)}</span><span class="stat-label">Afferent Coupling</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.instability.toFixed(2)}</span><span class="stat-label">Instability</span></div>
                                                    <div class="stat-item"><span class="stat-val">${d.loc.toFixed(0)}</span><span class="stat-label">LOC</span></div>
                                                </div>
                                                ${d.isDataClass ? `<div class="forensic-report"><h3>Data Class Detected</h3><p>This class primarily holds data and lacks significant behavior. Consider encapsulating behavior or refactoring into a more active role.</p></div>` : ''}
                                                ${d.brainMethods.length > 0 ? `<div class="forensic-report"><h3>Brain Method(s) Detected</h3><p>Methods like <strong>${d.brainMethods.join(', ')}</strong> exhibit high complexity and/or high churn, indicating they are central to the class's complexity and change. Consider refactoring these methods.</p></div>` : ''}
                                                ${d.lcom4Blocks.length > 1 ? `<div class="forensic-report"><h3>Low Cohesion (LCOM4)</h3><p>This class has ${d.lcom4Blocks.length} distinct groups of methods accessing different sets of fields, suggesting it might be doing too many things. Consider splitting it into multiple, more cohesive classes.</p></div>` : ''}
                                                ${d.verdict === 'GOD_CLASS' ? `<div class="forensic-report"><h3>God Class Detected</h3><p>This class is highly complex, has many responsibilities, and is central to many changes. It's a prime candidate for refactoring to improve maintainability.</p></div>` : ''}
                                                ${d.verdict === 'TOTAL_MESS' ? `<div class="forensic-report"><h3>Total Mess Detected</h3><p>This class is a severe hotspot, exhibiting high churn, complexity, and coupling. It requires immediate attention and significant refactoring.</p></div>` : ''}
                                                ${d.verdict === 'SHOTGUN_SURGERY' ? `<div class="forensic-report"><h3>Shotgun Surgery Candidate</h3><p>This class is frequently changed alongside many other classes, suggesting that a single conceptual change requires modifications across many places. Consider consolidating related responsibilities.</p></div>` : ''}
                                                ${d.verdict === 'HIGH_COUPLING' ? `<div class="forensic-report"><h3>High Coupling Detected</h3><p>This class is highly coupled to ${d.coupled} other classes, making it hard to change in isolation. Look for opportunities to reduce dependencies.</p></div>` : ''}
                                                ${d.verdict === 'COMPLEX' ? `<div class="forensic-report"><h3>Complex Class Detected</h3><p>This class has high cyclomatic complexity, making it hard to understand and test. Consider breaking down complex methods or responsibilities.</p></div>` : ''}
                                                ${d.verdict === 'HIDDEN_DEPENDENCY' ? `<div class="forensic-report"><h3>Hidden Dependency Detected</h3><p>This class frequently changes with other classes, indicating a temporal coupling that might not be obvious from the code structure. Consider making the dependency explicit or refactoring to reduce it.</p></div>` : ''}
                                                <div class="tips">
                                                    <h4>Tips for Improvement:</h4>
                                                    <ul>
                                                        <li><strong>Refactor:</strong> Break down large methods or classes into smaller, more focused units.</li>
                                                        <li><strong>Encapsulate:</strong> Group related data and behavior.</li>
                                                        <li><strong>Reduce Coupling:</strong> Minimize dependencies between classes.</li>
                                                        <li><strong>Improve Cohesion:</strong> Ensure classes have a single, clear responsibility.</li>
                                                    </ul>
                                                </div>
                                            `;
                                        }

                                        function hideDetails() {
                                            document.getElementById('detailsPanel').classList.remove('active');
                                        }

                                        // Table rendering
                                        function renderTable() {
                                            const tbody = document.getElementById('tableBody');
                                            const classFilter = document.getElementById('classFilter').value.toLowerCase();
                                            const verdictFilter = document.getElementById('verdictFilter').value;

                                            let filtered = rawData.filter(d => {
                                                return d.label.toLowerCase().includes(classFilter) && (!verdictFilter || d.verdict === verdictFilter);
                                            });

                                            filtered.sort((a, b) => {
                                                let aVal, bVal;
                                                switch(currentSort.column) {
                                                    case 0: aVal = a.label; bVal = b.label; break;
                                                    case 1: aVal = a.churn; bVal = b.churn; break;
                                                    case 2: aVal = a.recentChurn; bVal = b.recentChurn; break;
                                                    case 3: aVal = a.riskScore; bVal = b.riskScore; break;
                                                    case 4: aVal = a.y; bVal = b.y; break;
                                                    case 5: aVal = a.lcom4; bVal = b.lcom4; break;
                                                    case 6: aVal = a.verdict; bVal = b.verdict; break;
                                                }
                                                if (typeof aVal === 'string') return currentSort.ascending ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
                                                return currentSort.ascending ? aVal - bVal : bVal - aVal;
                                            });

                                            tbody.innerHTML = filtered.map(d => {
                                                const fileName = d.label.split('/').pop();
                                                return `
                                                <tr onclick='showDetails(${JSON.stringify(d).replace(/'/g, "\\\\'")})'>
                                                    <td title="${d.label}" style="white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:200px;">${fileName}</td>
                                                    <td>${d.churn}</td>
                                                    <td>${d.recentChurn}</td>
                                                    <td>${d.riskScore.toFixed(1)}</td>
                                                    <td>${d.y.toFixed(0)}</td>
                                                    <td>${d.lcom4.toFixed(1)}</td>
                                                    <td><span class="verdict-badge verdict-${d.verdict.split(' ')[0]}">${d.verdict}</span></td>
                                                </tr>
                                            `;}).join('');
                                        }

                                        function sortTable(column) {
                                            if (currentSort.column === column) currentSort.ascending = !currentSort.ascending;
                                            else { currentSort.column = column; currentSort.ascending = false; }
                                            renderTable();
                                        }

                                        document.getElementById('classFilter').addEventListener('input', renderTable);
                                        document.getElementById('verdictFilter').addEventListener('change', renderTable);

                                        // System Map (Circle Packing) rendering - System Map 2.0
                                        let treemapTooltip = null;
                                        let resizeTimeout = null;
                                        let systemMapHud = null;

                                        // Debounce helper
                                        function debounce(fn, delay) {
                                            return function(...args) {
                                                clearTimeout(resizeTimeout);
                                                resizeTimeout = setTimeout(() => fn.apply(this, args), delay);
                                            };
                                        }

                                        // Responsive resize handler
                                        window.addEventListener('resize', debounce(() => {
                                            if (document.getElementById('treemap-tab').classList.contains('active')) {
                                                renderTreemap();
                                            }
                                        }, 250));

                                        function renderTreemap() {
                                            // 1. Validate Container & Dimensions
                                            const container = document.getElementById('treemap-tab');
                                            const svgEl = document.getElementById('treemap');
                                            if (!container || !svgEl) {
                                                console.error("Missing container or svg element");
                                                return;
                                            }

                                            let width = svgEl.clientWidth;
                                            let height = container.clientHeight - 80;

                                            // Robust fallback for hidden/unmounted state
                                            if (!width || width === 0) width = container.clientWidth || window.innerWidth || 800;
                                            if (!height || height <= 0) height = (window.innerHeight * 0.75) - 80;
                                            if (height < 400) height = 600;

                                            // 2. Clear previous and create/get tooltip
                                            d3.select('#treemap').selectAll('*').remove();
                                            d3.select('#treemap')
                                                .attr('width', width)
                                                .attr('height', height)
                                                .style('cursor', 'pointer');

                                            // Virtual Lens Tooltip - create once
                                            if (!treemapTooltip) {
                                                treemapTooltip = d3.select('body').append('div')
                                                    .attr('class', 'virtual-lens');
                                            }
                                            // System Map HUD - get once
                                            if (!systemMapHud) {
                                                systemMapHud = d3.select('#system-map-hud');
                                            }


                                            // Modern pastel color scale for risk
                                            const getRiskColorPastel = (d) => {
                                                const v = d.verdict;
                                                // High risk - Soft Coral
                                                if (v === 'TOTAL_MESS' || v === 'GOD_CLASS' || v === 'BLOATED' || v === 'SHOTGUN_SURGERY') return '#ff7675';
                                                // Medium risk - Soft Amber
                                                if (v === 'BRAIN_METHOD' || v === 'COMPLEX' || v === 'SPLIT_CANDIDATE' || v === 'HIGH_COUPLING' || v === 'HIDDEN_DEPENDENCY' || v === 'FRAGILE_HUB') return '#fdcb6e';
                                                // Special types
                                                if (v === 'DATA_CLASS') return '#74b9ff';
                                                if (v === 'CONFIGURATION') return '#a29bfe';
                                                if (v === 'ORCHESTRATOR') return '#81ecec';
                                                // OK - Mint Green
                                                if (v === 'OK') return '#00b894';
                                                // Fallback by score
                                                const score = d.riskScore || 0;
                                                if (score > 20) return '#ff7675';
                                                if (score > 5) return '#fdcb6e';
                                                return '#00b894';
                                            };

                                            // Verdict badge color for tooltip
                                            const getVerdictBadgeStyle = (verdict) => {
                                                const colors = {
                                                    'TOTAL_MESS': { bg: '#ff7675', text: '#fff' },
                                                    'SHOTGUN_SURGERY': { bg: '#e17055', text: '#fff' },
                                                    'BRAIN_METHOD': { bg: '#a29bfe', text: '#fff' },
                                                    'GOD_CLASS': { bg: '#636e72', text: '#fff' },
                                                    'HIDDEN_DEPENDENCY': { bg: '#fdcb6e', text: '#2d3436' },
                                                    'HIGH_COUPLING': { bg: '#ffeaa7', text: '#2d3436' },
                                                    'COMPLEX': { bg: '#fab1a0', text: '#2d3436' },
                                                    'SPLIT_CANDIDATE': { bg: '#d63031', text: '#fff' },
                                                    'FRAGILE_HUB': { bg: '#e17055', text: '#fff' },
                                                    'BLOATED': { bg: '#ff7675', text: '#fff' },
                                                    'DATA_CLASS': { bg: '#74b9ff', text: '#fff' },
                                                    'CONFIGURATION': { bg: '#a29bfe', text: '#fff' },
                                                    'ORCHESTRATOR': { bg: '#81ecec', text: '#2d3436' },
                                                    'OK': { bg: '#00b894', text: '#fff' }
                                                };
                                                return colors[verdict] || { bg: '#636e72', text: '#fff' };
                                            };

                                            try {
                                                // 3. Data Check
                                                if (!treemapData || !treemapData.children) {
                                                    throw new Error("No hierarchical data available");
                                                }

                                                // 4. Setup Hierarchy
                                                const root = d3.hierarchy(treemapData)
                                                    .sum(d => d.value || 0)
                                                    .sort((a, b) => b.value - a.value);

                                                let focus = root;
                                                let view;

                                                // 5. Layout
                                                const pack = d3.pack()
                                                    .size([width, height])
                                                    .padding(3);

                                                pack(root);

                                                // 6. Rendering
                                                const svg = d3.select('#treemap');

                                                // Background click handler
                                                svg.on('click', (event) => zoom(event, root));

                                                // Helper: Check if node is descendant of target
                                                const isDescendantOf = (node, target) => {
                                                    let current = node;
                                                    while (current) {
                                                        if (current === target) return true;
                                                        current = current.parent;
                                                    }
                                                    return false;
                                                };

                                                // Circles
                                                const node = svg.append('g')
                                                    .selectAll('circle')
                                                    .data(root.descendants())
                                                    .join('circle')
                                                    .attr('class', d => d.children ? 'node' : 'node node--leaf')
                                                    .attr('fill', d => {
                                                        if (d.children) return '#dfe6e9'; // Soft gray for packages
                                                        try { return getRiskColorPastel(d.data); } catch(e) { return '#b2bec3'; }
                                                    })
                                                    .attr('stroke', d => d.children ? '#b2bec3' : 'rgba(255,255,255,0.8)')
                                                    .attr('stroke-width', d => d.children ? 1 : 2)
                                                    .style('opacity', 1)
                                                    .on('mouseover', function(event, d) {
                                                        // Hover glow effect
                                                        const fillColor = d.children ? '#b2bec3' : getRiskColorPastel(d.data);
                                                        d3.select(this)
                                                            .attr('stroke', fillColor)
                                                            .attr('stroke-width', 4)
                                                            .style('filter', d.children ? 'none' : 'drop-shadow(0 0 10px ' + fillColor + ')');

                                                        // Populate HUD (Fixed Hover Inspector)
                                                        const hud = d3.select('#system-map-hud');
                                                        const nodeData = d.data;
                                                        const isLeaf = !d.children;
                                                        const fullData = isLeaf ? rawData.find(r => r.label === nodeData.fullName || r.label === nodeData.name) : null;

                                                        let hudHtml = `<h3>${nodeData.name}</h3>`;

                                                        if (isLeaf && fullData) {
                                                            const verdictStyle = getVerdictBadgeStyle(fullData.verdict);
                                                            hudHtml += `
                                                                <div class="hud-row"><span class="hud-label">Risk Score</span> <span class="hud-value">${fullData.riskScore.toFixed(1)}</span></div>
                                                                <div class="hud-row"><span class="hud-label">Complexity</span> <span class="hud-value">${fullData.y.toFixed(0)}</span></div>
                                                                <div class="hud-row"><span class="hud-label">Churn</span> <span class="hud-value">${fullData.churn}</span></div>
                                                                <div class="hud-valign-center hud-verdict" style="background: ${verdictStyle.bg}; color: ${verdictStyle.text}; text-align:center;">${fullData.verdict}</div>
                                                            `;
                                                        } else if (isLeaf) {
                                                            hudHtml += `<div class="hud-row"><span class="hud-label">No analysis data available</span></div>`;
                                                        } else {
                                                            const childCount = d.descendants().filter(n => !n.children).length;
                                                            hudHtml += `
                                                                <div class="hud-row"><span class="hud-label">Items (Recursive)</span> <span class="hud-value">${childCount}</span></div>
                                                                <div class="hud-row"><span class="hud-label">Direct Children</span> <span class="hud-value">${d.children.length}</span></div>
                                                                <div class="hud-row"><span class="hud-label">Total Size</span> <span class="hud-value">${(d.value || 0).toLocaleString()}</span></div>
                                                            `;
                                                        }

                                                        hud.html(hudHtml)
                                                           .style('display', 'block')
                                                           .style('opacity', 1);
                                                    })
                                                    .on('mouseout', function(d) {
                                                        d3.select(this)
                                                            .attr('stroke', d.children ? '#b2bec3' : 'rgba(255,255,255,0.8)')
                                                            .attr('stroke-width', d.children ? 1 : 2)
                                                            .style('filter', 'none');
                                                        // Hide HUD on mouseout
                                                        d3.select('#system-map-hud').style('opacity', 0);
                                                    })
                                                    .on('click', (event, d) => {
                                                        event.stopPropagation();
                                                        updateBreadcrumbs(d);
                                                        // Fix: Zoom to parent if leaf node to maintain context
                                                        if (d.children) zoom(event, d);
                                                        else if (d.parent) zoom(event, d.parent);
                                                        else zoom(event, d);

                                                        // RESTORED: Show details panel for files
                                                        if (!d.children) {
                                                            const classData = rawData.find(r => r.label === d.data.fullName || r.label === d.data.name);
                                                            if (classData) showDetails(classData);
                                                        }
                                                    });

                                                // Labels - show actual node name (filename for files, folder for packages)
                                                const label = svg.append('g')
                                                    .style('font', '10px sans-serif')
                                                    .attr('pointer-events', 'none')
                                                    .attr('text-anchor', 'middle')
                                                    .selectAll('text')
                                                    .data(root.descendants())
                                                    .join('text')
                                                    .style('fill-opacity', d => d.parent === root ? 1 : 0)
                                                    .style('display', d => d.parent === root ? 'inline' : 'none')
                                                    .style('font-weight', d => d.children ? 'bold' : 'normal')
                                                    .style('fill', '#1a1a2e')
                                                    .style('paint-order', 'stroke')
                                                    .style('stroke', 'rgba(255,255,255,0.85)')
                                                    .style('stroke-width', '2.5px')
                                                    .text(d => {
                                                        // Show the node's OWN name (filename for files, folder name for directories)
                                                        const name = d.data.name;
                                                        // Truncate long names to prevent overlap
                                                        return name.length > 15 ? name.substring(0, 12) + '...' : name;
                                                    });
            """;
    private static String TEMPLATE_BODY_PART2 = """
                                    // Initial Zoom
                                    zoomTo([root.x, root.y, root.r * 2]);

                                    // --- Helper Functions ---

                                    function zoom(event, d) {
                                        focus = d;

                                        // Cinematic smooth zoom with d3.interpolateZoom
                                        // Use shorter duration for large datasets (performance optimization)
                                        const nodeCount = root.descendants().length;
                                        const duration = nodeCount > 500 ? 400 : 750;

                                        const transition = svg.transition()
                                            .duration(duration)
                                            .tween('zoom', () => {
                                                const i = d3.interpolateZoom(view, [focus.x, focus.y, focus.r * 2]);
                                                return t => zoomTo(i(t));
                                            });

                                        // Fade effect for non-relevant siblings
                                        node.transition(transition)
                                            .style('opacity', n => {
                                                // Always show root and current focus tree
                                                if (n === root || isDescendantOf(n, focus) || isDescendantOf(focus, n)) return 1;
                                                // Fade siblings
                                                return 0.15;
                                            });

                                        // Label transitions
                                        label.filter(function(d) { return d.parent === focus || this.style.display === 'inline'; })
                                             .transition(transition)
                                             .style('fill-opacity', d => d.parent === focus ? 1 : 0)
                                             .on('start', function(d) { if (d.parent === focus) this.style.display = 'inline'; })
                                             .on('end', function(d) { if (d.parent !== focus) this.style.display = 'none'; });

                                        updateBreadcrumbs(d);
                                    }

                                    function zoomTo(v) {
                                        const k = Math.min(width, height) / v[2];
                                        view = v;
                                        label.attr('transform', d => `translate(${(d.x - v[0]) * k + width / 2},${(d.y - v[1]) * k + height / 2})`);
                                        node.attr('transform', d => `translate(${(d.x - v[0]) * k + width / 2},${(d.y - v[1]) * k + height / 2})`);
                                        node.attr('r', d => d.r * k);

                                        // Show labels on circles with visible radius > 20px (Fix 6)
                                        label.each(function(d) {
                                            const visibleR = d.r * k;
                                            const isLeaf = !d.children;
                                            const shouldShow = (d.parent === focus) || (isLeaf && visibleR > 20);
                                            d3.select(this)
                                                .style('display', shouldShow ? 'inline' : 'none')
                                                .style('fill-opacity', shouldShow ? Math.min(1, (visibleR - 15) / 20) : 0)
                                                .style('font-size', Math.max(10, Math.min(14, visibleR / 3)) + 'px');
                                        });
                                    }

                                    function updateBreadcrumbs(n) {
                                        const bc = d3.select('#breadcrumbs');
                                        bc.selectAll('*').remove();
                                        const path = [];
                                        let curr = n;
                                        while(curr) { path.unshift(curr); curr = curr.parent; }

                                        path.forEach((node, i) => {
                                            const isLast = i === path.length - 1;
                                            const span = bc.append('span')
                                                .text(node.data.name)
                                                .attr('class', 'crumb')
                                                .style('cursor', isLast ? 'default' : 'pointer');
                                            if (!isLast) {
                                                span.on('click', (e) => {
                                                    e.stopPropagation();
                                                    zoom(e, node);
                                                });
                                                bc.append('span').text('›').attr('class', 'crumb-separator');
                                            }
                                        });
                                    }

                                    // Init Breadcrumbs
                                    updateBreadcrumbs(root);

                                } catch (e) {
                                    console.error("Render Error:", e);
                                    d3.select('#treemap')
                                        .append('text')
                                        .attr('x', width / 2).attr('y', height / 2)
                                        .attr('text-anchor', 'middle')
                                        .attr('fill', '#ff7675')
                                        .style('font-size', '18px')
                                        .text('Error: ' + e.message);
                                }
                            }


                        // Network rendering
                        function renderNetwork() {
                            const container = document.getElementById('network-container');
                            const width = container.clientWidth || 800;
                            const height = container.clientHeight || 600;

                            d3.select('#network').selectAll('*').remove();

                            const svg = d3.select('#network')
                                .attr('width', width)
                                .attr('height', height);

                            if (!networkData.nodes || networkData.nodes.length === 0) {
                                const msgGroup = svg.append('g')
                                    .attr('transform', `translate(${width/2}, ${height/2})`);

                                msgGroup.append('text')
                                    .attr('text-anchor', 'middle')
                                    .attr('y', -30)
                                    .text('📊 No Temporal Coupling Detected')
                                    .style('font-size', '18px')
                                    .style('font-weight', 'bold')
                                    .style('fill', '#666');

                                msgGroup.append('text')
                                    .attr('text-anchor', 'middle')
                                    .attr('y', 5)
                                    .text('Temporal coupling requires files that change together frequently.')
                                    .style('font-size', '14px')
                                    .style('fill', '#999');

                                msgGroup.append('text')
                                    .attr('text-anchor', 'middle')
                                    .attr('y', 30)
                                    .text('Try running with deeper git history: --min-churn 2')
                                    .style('font-size', '13px')
                                    .style('fill', '#aaa')
                                    .style('font-style', 'italic');
                                return;
                            }

                            // Add zoom behavior
                const g = svg.append('g');
                const zoomBehavior = d3.zoom()
                    .extent([[0, 0], [width, height]])
                    .scaleExtent([0.1, 4])
                    .on('zoom', (event) => g.attr('transform', event.transform));

                svg.call(zoomBehavior)
                    .on('click', (event) => {
                                    if (event.target.tagName !== 'circle') {
                                        // Reset highlights on background click
                                        node.style('opacity', 1);
                                        link.style('opacity', 1);
                                        nodeGroup.selectAll('text').style('opacity', 1);
                                    }
                                });

                            const simulation = d3.forceSimulation(networkData.nodes)
                                .force('link', d3.forceLink(networkData.links).id(d => d.id).distance(100))
                                .force('charge', d3.forceManyBody().strength(-300))
                                .force('center', d3.forceCenter(width / 2, height / 2))
                                .force('collision', d3.forceCollide().radius(d => Math.sqrt(d.riskScore) * 2 + 10));

                            const link = g.append('g')
                                .attr('class', 'links')
                                .selectAll('line')
                                .data(networkData.links)
                                .join('line')
                                .attr('class', 'link')
                                .attr('stroke-width', d => Math.sqrt(d.value));

                            // Add node groups to hold circle and text
                            const nodeGroup = g.append('g')
                                .attr('class', 'nodes')
                                .selectAll('g')
                                .data(networkData.nodes)
                                .join('g')
                                .call(d3.drag()
                                    .on('start', dragstarted)
                                    .on('drag', dragged)
                                    .on('end', dragended))
                                .on('end', () => {
                                     // Delayed auto-zoom to ensure layout stabilization
                                     setTimeout(() => {
                                         const bounds = g.node().getBBox();
                                         const fullWidth = width;
                                         const fullHeight = height;
                                         const midX = bounds.x + bounds.width / 2;
                                         const midY = bounds.y + bounds.height / 2;
                                         if (bounds.width > 0 && bounds.height > 0) {
                                             const scale = 0.85 / Math.max(bounds.width / fullWidth, bounds.height / fullHeight);
                                             const translate = [fullWidth / 2 - scale * midX, fullHeight / 2 - scale * midY];
                                             svg.transition().duration(750).call(
                                                 zoomBehavior.transform,
                                                 d3.zoomIdentity.translate(translate[0], translate[1]).scale(scale)
                                             );
                                         }
                                     }, 500);
                                });

                // Add Zoom Controls
                const controls = d3.select('#network-container').append('div')
                    .style('position', 'absolute')
                    .style('bottom', '20px')
                    .style('right', '20px')
                    .style('display', 'flex')
                    .style('gap', '5px')
                    .style('z-index', '1000');

                const createButton = (text, onClick, title) => {
                controls.append('button')
                    .text(text)
                    .attr('title', title)
                    .style('width', '30px')
                    .style('height', '30px')
                    .style('border', 'none')
                    .style('background', '#fff')
                    .style('box-shadow', '0 2px 5px rgba(0,0,0,0.2)')
                    .style('border-radius', '4px')
                    .style('cursor', 'pointer')
                    .style('font-weight', 'bold')
                    .style('color', '#555')
                    .on('mousedown', function() { d3.select(this).style('background', '#f0f0f0'); })
                    .on('mouseup', function() { d3.select(this).style('background', '#fff'); })
                    .on('click', onClick);
            };

            createButton('+', () => svg.transition().call(zoomBehavior.scaleBy, 1.3), 'Zoom In');
            createButton('-', () => svg.transition().call(zoomBehavior.scaleBy, 0.7), 'Zoom Out');
            createButton('⤢', () => {
                 const bounds = g.node().getBBox();
                 // Re-calculate dimensions to handle resizing/hidden tab initializations
                 const container = document.getElementById('network-container');
                 const fullWidth = container ? (container.clientWidth || width) : width;
                 const fullHeight = container ? (container.clientHeight || height) : height;
                 // Validate bounds
                 if (!isFinite(bounds.width) || !isFinite(bounds.height) || bounds.width === 0 || bounds.height === 0) {
                     console.warn("Invalid bounds for fit:", bounds);
                     return;
                 }

                 const midX = bounds.x + bounds.width / 2;
                 const midY = bounds.y + bounds.height / 2;

                 // Clamp scale to prevent blowing up
                 let scale = 0.85 / Math.max(bounds.width / fullWidth, bounds.height / fullHeight);
                 scale = Math.max(0.1, Math.min(scale, 4)); // Clamp between 0.1x and 4x

                 const translate = [fullWidth / 2 - scale * midX, fullHeight / 2 - scale * midY];

                 if (!isFinite(translate[0]) || !isFinite(translate[1])) {
                      console.warn("Invalid translate for fit:", translate);
                      return;
                 }

                 svg.transition().duration(750).call(
                     zoomBehavior.transform,
                     d3.zoomIdentity.translate(translate[0], translate[1]).scale(scale)
                 );
            }, 'Fit to Screen');

                            // Network tooltip (Virtual Lens style)
                             const networkTooltip = d3.select('body').append('div')
                                .attr('class', 'treemap-tooltip')
                                .style('opacity', 0)
                                .style('z-index', '9999'); // Ensure tooltip is on top

                            // Add nodes
                            const node = nodeGroup.append('circle')
                                .attr('class', 'node')
                                .attr('r', d => Math.max(10, Math.sqrt(d.riskScore) * 4 + 8))
                                .attr('fill', d => {
                                    // Non-linear gradient for better contrast
                                    // Using power 0.4 makes mid-range risks appear more colorful/intense
                                    const baseColor = getRiskColor(d);
                                    const maxRisk = Math.max(...networkData.nodes.map(n => n.riskScore)) || 100;
                                    const ratio = d.riskScore / maxRisk;
                                    const intensity = Math.pow(ratio, 0.4);
                                    // Interpolate between a light pastel (0.1) and full rich color
                                    return d3.interpolateRgb(d3.color(baseColor).brighter(1.5), baseColor)(0.2 + 0.8 * intensity);
                                })
                                .attr('stroke', '#fff')
                                .attr('stroke-width', 2)
                                .style('cursor', 'grab')
                                .on('mouseover', function(event, d) {
                                    d3.select(this).attr('stroke-width', 4).attr('stroke', '#667');
                                    // Show tooltip with full path
                                    const verdictStyle = getVerdictBadgeStyle(d.verdict);
                                    networkTooltip
                                        .html(`
                                            <div class="lens-title">${d.id}</div>
                                            <div class="lens-metrics">
                                                <div class="lens-metric">
                                                    <span class="lens-metric-value">${d.riskScore.toFixed(1)}</span>
                                                    <span class="lens-metric-label">Risk</span>
                                                </div>
                                                <div class="lens-metric">
                                                    <span class="lens-metric-value">${d.coupled}</span>
                                                    <span class="lens-metric-label">Coupled</span>
                                                </div>
                                            </div>
            """;
    private static String TEMPLATE_BODY_PART3 = """
                                    <div class="lens-verdict" style="background: ${verdictStyle.bg}; color: ${verdictStyle.text};">${d.verdict}</div>
                                `)
                                .style('opacity', 1)
                                .style('left', (event.pageX + 15) + 'px')
                                .style('top', (event.pageY - 10) + 'px');
                            // Ensure label is 100% visible on hover
                            d3.select(this.parentNode).select('text').style('opacity', 1);
                        })
                        .on('mousemove', function(event) {
                            networkTooltip
                                .style('left', (event.pageX + 15) + 'px')
                                .style('top', (event.pageY - 10) + 'px');
                        })
                        .on('mouseout', function(event, d) {
                            d3.select(this).attr('stroke-width', 2).attr('stroke', '#fff');
                            networkTooltip.style('opacity', 0);
                            // Restore labels
                            // connectedNodes logic might have dimmed them, so check if we are in a 'active' state
                            // For simplicity, restore to 1 unless pinned/filtered logic is added later
                             d3.select(this.parentNode).select('text').style('opacity', 1);
                        })
                        .on('click', (event, d) => {
                            event.stopPropagation();
                            d.pinned = !d.pinned; // Toggle pin state
                            const classData = rawData.find(r => r.label === d.id);
                            if (classData) showDetails(classData);

                            // Highlight only immediate connections
                            const connectedNodes = new Set();
                            connectedNodes.add(d.id);
                            networkData.links.forEach(l => {
                                if (l.source.id === d.id) connectedNodes.add(l.target.id);
                                if (l.target.id === d.id) connectedNodes.add(l.source.id);
                            });

                            node.transition().duration(200)
                                .style('opacity', n => connectedNodes.has(n.id) ? 1 : 0.15);
                            link.transition().duration(200)
                                .style('opacity', l => (l.source.id === d.id || l.target.id === d.id) ? 1 : 0.08)
                                .attr('stroke-width', l => (l.source.id === d.id || l.target.id === d.id) ? 3 : 1);

                            // Dim labels of non-connected nodes instead of hiding them completely
                            nodeGroup.selectAll('text').transition().duration(200)
                                .style('opacity', n => connectedNodes.has(n.id) ? 1 : 0.2);
                        });

                    // Add text label - ALL labels visible
                    nodeGroup.append('text')
                        .text(d => {
                            const parts = d.name.split('/');
                            return parts[parts.length - 1]; // Short filename
                        })
                        .attr('x', d => Math.max(12, Math.sqrt(d.riskScore) * 4 + 12))
                        .attr('y', 4)
                        .attr('class', 'node-label')
                        .style('font-size', d => Math.max(9, Math.sqrt(d.riskScore) + 6) + 'px') // Dynamic font size
                        .style('font-weight', 'bold')
                        .style('fill', '#2c3e50') // Corrected property
                        .style('opacity', 1) // Force 100% visibility
                        .style('pointer-events', 'none')
                        .style('paint-order', 'stroke')
                        .style('stroke', 'rgba(255,255,255,0.95)')
                        .style('stroke-width', '2.5px');

                    // Add link titles
                    link.append('title').text('Temporally Coupled');

                    simulation.on('tick', () => {
                        link
                            .attr('x1', d => d.source.x)
                            .attr('y1', d => d.source.y)
                            .attr('x2', d => d.target.x)
                            .attr('y2', d => d.target.y);

                        nodeGroup
                            .attr('transform', d => `translate(${d.x},${d.y})`);
                    });

                    function dragstarted(event, d) {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        d.fx = d.x;
                        d.fy = d.y;
                        d3.select(event.sourceEvent.target).style('cursor', 'grabbing');
                    }

                    function dragged(event, d) {
                        d.fx = event.x;
                        d.fy = event.y;
                    }

                    function dragended(event, d) {
                        if (!event.active) simulation.alphaTarget(0);
                        // STICKY: Keep position fixed where user dropped it
                        // d.fx and d.fy remain set, so node stays in place
                        d3.select(event.sourceEvent.target).style('cursor', 'grab');
                    }
                }

                    function diagnose(d) {
                        // Core verdicts from ForensicRuleEngine
                        if (d.verdict === 'SHOTGUN_SURGERY') return { title: "Shotgun Victim", desc: "Changes ripple everywhere", why: `Coupled to ${d.coupled} files`, fix: "Centralize logic into a single module" };
                        if (d.verdict === 'HIDDEN_DEPENDENCY') return { title: "Hidden Dependency", desc: "Temporal coupling without code link", why: `Changes with ${d.coupled} files but only ${d.fanOut} imports`, fix: "Make dependencies explicit via imports or extract shared logic" };
                        if (d.verdict.startsWith('GOD_CLASS')) return { title: "God Class", desc: "Too many responsibilities", why: `Total CC ${d.y}, Fan-out ${d.fanOut}`, fix: "Full decomposition required" };
                        if (d.verdict === 'TOTAL_MESS') return { title: "Kitchen Sink", desc: "No clear responsibility", why: `LCOM4 = ${d.lcom4.toFixed(1)}`, fix: "Split the class by responsibility" };
                        if (d.verdict === 'BRAIN_METHOD') return { title: "One-Method Monster", desc: "Contains massive methods", why: `Found ${d.brainMethods.length} complex methods`, fix: "Extract Method pattern" };
                        if (d.verdict.startsWith('COMPLEX')) return { title: "Complex Code", desc: "High cyclomatic complexity", why: `Max CC = ${d.maxCC}`, fix: "Simplify conditionals, extract helper methods" };
                        if (d.verdict === 'FRAGILE_HUB') return { title: "Fragile Hub", desc: "Central coordinator that changes often", why: `Fan-out ${d.fanOut}, Churn ${d.churn}`, fix: "Consider event-driven architecture or dependency injection" };
                        if (d.verdict === 'SPLIT_CANDIDATE') return { title: "Split Candidate", desc: "Contains disconnected clusters", why: `LCOM4 = ${d.lcom4.toFixed(1)}`, fix: "Split into separate classes based on field usage" };
                        if (d.verdict === 'HIGH_COUPLING') return { title: "High Coupling", desc: "Too many dependencies", why: `Fan-out = ${d.fanOut}`, fix: "Reduce dependencies, use interfaces or dependency injection" };
                        if (d.verdict === 'BLOATED') return { title: "Bloated File", desc: "Large file with many lines", why: `LOC = ${d.loc}`, fix: "Consider splitting into smaller, focused modules" };

                        // Benign verdicts (special cases)
                        if (d.verdict === 'DATA_CLASS') return { title: "Data Carrier", desc: "Mostly Getters/Setters", why: "80%+ of methods are boilerplate", fix: "Benign fragmentation. No action needed." };
                        if (d.verdict === 'CONFIGURATION') return { title: "App Blueprint", desc: "Spring @Configuration Class", why: "High fragmentation is expected for factory classes", fix: "Benign fragmentation. No action needed." };
                        if (d.verdict === 'ORCHESTRATOR') return { title: "Manager of Workers", desc: "Orchestrates complex flows", why: `High fan-out (${d.coupled}) but low internal complexity`, fix: "Maintainable pattern. Keep it cohesive." };

                        // OK verdict - healthy code
                        if (d.verdict === 'OK') return { title: "Healthy Code", desc: "No major issues", why: "Metrics OK", fix: "Keep it up!" };

                        // Fallback for any unhandled verdict
                        return { title: d.verdict, desc: "See metrics for details", why: "Custom or unrecognized verdict", fix: "Review metrics below" };
                    }

                    function getPrescriptions(d) {
                        const steps = [];
                        const roi = d.churn * d.y;
                        const isHighROI = roi > 1000;

                                    const isBenign = d.verdict === 'OK' || d.verdict === 'ORCHESTRATOR' || d.verdict === 'DATA_CLASS' || d.verdict === 'CONFIGURATION';

                                    if (isHighROI && !isBenign) {
                                        steps.push({
                                            icon: '🔥',
                                            title: 'HIGH IMPACT TARGET',
                                            desc: `Refactoring ROI is ${Math.round(roi)}. This file is complex AND changes often. Fixing this will dramatically reduce long-term maintenance costs.`
                                        });
                                    }

                                    // 1. Check LCOM4 (SRP Violated) - Suppress if Benign
                                    if (d.lcom4 > 1 && !d.isDataClass && d.verdict !== 'CONFIGURATION') {                                        let desc = `LCOM4 is ${d.lcom4.toFixed(1)}. This class likely handles ${Math.ceil(d.lcom4)} unrelated responsibilities. Group fields/methods by usage and extract.`;

                                        if (d.lcom4Blocks && d.lcom4Blocks.length > 1) {
                                            const componentsHtml = d.lcom4Blocks.map((block, index) => {
                                                // Filter out noise (lambdas) for the display list
                                                const cleanBlock = block.filter(m => !m.includes('lambda$'));
                                                if (cleanBlock.length === 0) return ''; // Skip if only lambdas

                                                return `<div style="margin-top:4px; padding:4px; background:white; border:1px solid #ddd; border-radius:3px;">
                                                    <strong>Group ${index + 1}:</strong> <span style="font-family:monospace; color:#555;">${cleanBlock.slice(0, 5).join(', ')}${cleanBlock.length > 5 ? '...' : ''}</span>
                                                 </div>`;
                                            }).filter(html => html !== '').join('');
                                            desc += `<div style="margin-top:8px;">${componentsHtml}</div>`;
                                        }

                                        steps.push({
                                            icon: '✂️',
                                            title: 'Split the Class',
                                            desc: desc
                                        });
                                    }
                        if (d.isDataClass) {
                            steps.push({
                                icon: '📦',
                                title: 'Data Class Verified',
                                desc: 'High fragmentation is normal for a DTO/POJO. It has no behavior to split.'
                            });
                        }

                        // 2. Check Brain Methods (Complexity)
                        if (d.brainMethods && d.brainMethods.length > 0) {
                            steps.push({
                                icon: '🧠',
                                title: 'Extract Brain Methods',
                                desc: `Extract the ${d.brainMethods.length} complex methods identified below into their own helper classes or services.`
                            });
                        }

                        // 3. Check Coupling (Dependencies)
                        if (d.coupled > 10) {
                            steps.push({
                                icon: '🔗',
                                title: 'Reduce Coupling',
                                desc: `This class is coupled to ${d.coupled} others. Invert dependencies or use events to decouple.`
                            });
                        }

                        // 4. Check Churn (Stability)
                        if (d.recentChurn > 5) {
                            steps.push({
                                icon: '🔥',
                                title: 'Stabilize (Burning Platform)',
                                desc: `High recent churn (${d.recentChurn} commits). Add unit tests before refactoring to prevent regressions.`
                            });
                        }

                        // 5. Check GOD Class
                         if (d.y > 100 && d.lcom4 > 2) { // d.y is Total CC
                            steps.push({
                                icon: '⛪',
                                title: 'Decompose God Class',
                                desc: `This class is too big and complex. Stop adding features here. Refactor strictly.`
                            });
                        }

                        if (steps.length === 0) {
                            steps.push({ icon: '✅', title: 'Celebrate', desc: 'This class looks healthy. Keep it up!' });
                        }

                        return steps;
                    }

                    function showDetails(d) {
                        const diagnosis = diagnose(d);
                        const prescriptions = getPrescriptions(d);
                        const panel = document.getElementById('detailsPanel');
                        const content = document.getElementById('panelContent');
                        const badgeClass = `verdict-${d.verdict.split(' ')[0]}`;

                        content.innerHTML = `
                            <div class="panel-header">
                                <h2>${d.label}</h2>
                                <span class="verdict-badge ${badgeClass}">${d.verdict}</span>
                            </div>

                            <!-- Action Plan Section -->
                            <div class="action-plan" style="background: #e3f2fd; padding: 15px; border-radius: 6px; margin-bottom: 20px; border-left: 4px solid #2196f3;">
                                <h3 style="margin-top: 0; color: #1565c0;">📋 Action Plan</h3>
                                <ul style="list-style: none; padding: 0; margin: 0;">
                                    ${prescriptions.map(p => `
                                        <li style="margin-bottom: 10px; display: flex; align-items: flex-start; gap: 10px;">
                                            <span style="font-size: 1.2rem;">${p.icon}</span>
                                            <div>
                                                <strong style="color: #0d47a1;">${p.title}</strong>
                                                <p style="margin: 2px 0 0 0; font-size: 0.9rem; color: #546e7a;">${p.desc}</p>
                                            </div>
                                        </li>
                                    `).join('')}
                                </ul>
                            </div>

                            <div class="forensic-report">
                                <h3>${diagnosis.title}</h3>
                                <p>${diagnosis.desc}</p>
                                <p><strong>Why?</strong> ${diagnosis.why}</p>
                                ${d.brainMethods && d.brainMethods.length > 0 ? `
                                    <div style="background: #ffebee; border-left: 4px solid #c0392b; padding: 10px; margin: 10px 0; border-radius: 4px;">
                                        <h4 style="margin: 0 0 5px 0; color: #c0392b;">🧠 Brain Methods Detected</h4>
                                        <ul style="margin: 5px 0 5px 20px; padding: 0;">
                                            ${d.brainMethods.map(m => `<li><code>${m}</code></li>`).join('')}
                                        </ul>
                                        <p style="margin: 5px 0 0 0; font-size: 0.9em;">These methods are disproportionately complex. Extract them!</p>
                                    </div>
                                ` : ''}
                                <div class="tips"><h4>💡 Recommendation</h4>${diagnosis.fix}</div>
                            </div>
                            <div class="stat-grid">
                                <div class="stat-item"><span class="stat-val">${d.riskScore.toFixed(1)}</span><span class="stat-label">Risk Score</span></div>
                                <div class="stat-item"><span class="stat-val">${d.churn}</span><span class="stat-label">Churn</span></div>
                                <div class="stat-item"><span class="stat-val">${d.recentChurn}</span><span class="stat-label">Recent Churn</span></div>
                                <div class="stat-item"><span class="stat-val">${d.coupled}</span><span class="stat-label">Coupled</span></div>
                                <div class="stat-item"><span class="stat-val">${d.maxCC}</span><span class="stat-label">Max CC</span></div>
                                <div class="stat-item"><span class="stat-val">${d.lcom4.toFixed(1)}</span><span class="stat-label">LCOM4</span></div>
                                <div class="stat-item"><span class="stat-val">${d.instability.toFixed(2)}</span><span class="stat-label">Instability</span></div>
                                <div class="stat-item"><span class="stat-val">${d.loc.toFixed(0)}</span><span class="stat-label">LOC</span></div>
                            </div>
                            <p style="font-size: 0.8rem; color: #999;">Full Path: ${d.label}</p>
                        `;
                        panel.classList.add('active');
                    }

                    function hideDetails() {
                        document.getElementById('detailsPanel').classList.remove('active');
                    }
                </script>
            </body>
            </html>
            """;

    private static final String TEMPLATE_BODY;
    static {
        TEMPLATE_BODY = TEMPLATE_BODY_PART1 + TEMPLATE_BODY_PART2 + TEMPLATE_BODY_PART3;
    }
}
