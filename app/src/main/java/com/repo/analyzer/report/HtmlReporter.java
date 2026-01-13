package com.repo.analyzer.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class HtmlReporter {

    public void generate(List<AnalysisData> data, Path outputPath) {
        String json = convertToJson(data);
        String treemapJson = convertToTreemapJson(data);
        String networkJson = convertToNetworkJson(data);

        String html = TEMPLATE
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

    private String convertToJson(List<AnalysisData> data) {
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

    private String convertToTreemapJson(List<AnalysisData> data) {
        // Build hierarchical structure: root -> packages -> classes
        Map<String, List<AnalysisData>> packageMap = new HashMap<>();

        for (AnalysisData d : data) {
            String pkg = extractPackage(d.className());
            packageMap.computeIfAbsent(pkg, k -> new ArrayList<>()).add(d);
        }

        StringBuilder json = new StringBuilder("{\"name\":\"root\",\"children\":[");
        boolean first = true;
        for (Map.Entry<String, List<AnalysisData>> entry : packageMap.entrySet()) {
            if (!first)
                json.append(",");
            first = false;

            json.append(String.format("{\"name\":\"%s\",\"children\":[", entry.getKey()));

            boolean firstClass = true;
            for (AnalysisData d : entry.getValue()) {
                if (!firstClass)
                    json.append(",");
                firstClass = false;

                // Use the full filename (basename) as the short name
                int lastSlash = d.className().lastIndexOf('/');
                String shortName = lastSlash >= 0 ? d.className().substring(lastSlash + 1) : d.className();
                json.append(String.format(
                        "{\"name\":\"%s\",\"value\":%.0f,\"riskScore\":%.2f,\"churn\":%d,\"complexity\":%.0f,\"verdict\":\"%s\",\"fullName\":\"%s\"}",
                        shortName, d.loc(), d.riskScore(), d.churn(), d.totalCC(), d.verdict(), d.className()));
            }
            json.append("]}");
        }
        json.append("]}");

        return json.toString();
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

            int lastSlash = d.className().lastIndexOf('/');
            String shortName = lastSlash >= 0 ? d.className().substring(lastSlash + 1) : d.className();
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

    private String extractPackage(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            String fullPkg = className.substring(0, lastDot);
            // Get top 2-3 package levels for better grouping
            String[] parts = fullPkg.split("\\.");
            if (parts.length > 3) {
                return String.join(".", Arrays.copyOfRange(parts, 0, Math.min(4, parts.length)));
            }
            return fullPkg;
        }
        return "default";
    }

    private static final String TEMPLATE = """
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

                    /* Treemap */
                    #treemap { width: 100%; height: 60vh; }
                    .treemap-cell { stroke: #fff; stroke-width: 2px; cursor: pointer; }
                    .treemap-cell:hover { stroke: #000; stroke-width: 3px; }
                    .treemap-label { font-size: 12px; fill: white; text-anchor: middle; pointer-events: none; }
                    .treemap-package { font-size: 14px; font-weight: bold; fill: #333; }

                    /* Network Graph */
                    #network-container { width: 100%; height: 60vh; overflow: auto; position: relative; border: 1px solid #ddd; border-radius: 4px; background: #fafafa; }
                    #network { display: block; }
                    .node { cursor: pointer; stroke: #fff; stroke-width: 2px; }
                    .node:hover { stroke: #000; stroke-width: 3px; }
                    .link { stroke: #999; stroke-opacity: 0.6; stroke-width: 1px; }

                    /* DataTable */
                    table { width: 100%; border-collapse: collapse; margin-top: 20px; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
                    th { background: #34495e; color: white; cursor: pointer; user-select: none; }
                    th:hover { background: #2c3e50; }
                    tr:hover { background: #f5f5f5; cursor: pointer; }
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

                    #detailsPanel.active { display: flex; }
                    .panel-header { border-bottom: 2px solid #f0f0f0; padding-bottom: 15px; margin-bottom: 15px; }
                    .panel-header h2 { margin: 0; font-size: 1.2rem; word-break: break-all; color: #34495e; }
                    .verdict-badge { display: inline-block; padding: 5px 10px; border-radius: 4px; color: white; font-weight: bold; font-size: 0.8rem; margin-top: 10px; }

                    /* Verdict Colors */
                    .verdict-TOTAL_MESS { background-color: #c0392b; }
                    .verdict-SHOTGUN_SURGERY { background-color: #d35400; }
                    .verdict-BRAIN_METHOD { background-color: #8e44ad; }
                    .verdict-GOD_CLASS { background-color: #2c3e50; }
                    .verdict-HIDDEN_DEPENDENCY { background-color: #e67e22; }
                    .verdict-HIGH_COUPLING { background-color: #f39c12; }
                    .verdict-COMPLEX { background-color: #f1c40f; color: #333; }
                            .verdict-DATA_CLASS { background-color: #3498db; }
                            .verdict-CONFIGURATION { background-color: #6c5ce7; }
                            .verdict-ORCHESTRATOR { background-color: #1abc9c; }                    .verdict-OK { background-color: #27ae60; }

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
                    .close-btn:hover { color: #333; }

                    .legend { display: flex; gap: 20px; margin-top: 15px; flex-wrap: wrap; }
                    .legend-item { display: flex; align-items: center; gap: 5px; font-size: 0.9rem; }
                    .legend-color { width: 20px; height: 20px; border-radius: 3px; }
                </style>
            </head>
            <body>
                <div id="wrapper" style="display: flex; width: 100%; height: 100%;">
                    <div class="main-content">
                        <div class="container">
                            <h1>Code Forensics: Risk Analysis</h1>

                            <div class="tabs">
                                <button class="tab active" onclick="switchTab('quadrant')">Quadrant View</button>
                                <button class="tab" onclick="switchTab('table')">Data Table</button>
                                <button class="tab" onclick="switchTab('treemap')">Treemap</button>
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

                            <!-- Treemap Tab -->
                            <div id="treemap-tab" class="tab-content">
                                <p><strong>Size:</strong> Lines of Code | <strong>Color:</strong> Risk Score (Red = High, Green = Low)</p>
                                <svg id="treemap"></svg>
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
                        if (riskScore > 100) return 'rgba(231, 76, 60, 0.7)';
                        if (riskScore > 50) return 'rgba(241, 196, 15, 0.7)';
                        return 'rgba(46, 204, 113, 0.7)';
                    };

                    const getRiskColor = (riskScore) => {
                        if (riskScore > 100) return '#e74c3c';
                        if (riskScore > 50) return '#f39c12';
                        return '#27ae60';
                    };

                    // Quadrant Chart (existing code)
                    const ctx = document.getElementById('riskChart').getContext('2d');
                    const backgroundZones = {
                        id: 'backgroundZones',
                        beforeDraw: (chart) => {
                            const ctx = chart.ctx;
                            const chartArea = chart.chartArea;
                            const xScale = chart.scales.x;
                            const yScale = chart.scales.y;
                            const xMid = xScale.getPixelForValue(10);
                            const yMid = yScale.getPixelForValue(50);
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
                                        churnLine: { type: 'line', xMin: 10, xMax: 10, borderColor: 'rgba(0,0,0,0.3)', borderWidth: 2, borderDash: [5,5] },
                                        complexityLine: { type: 'line', yMin: 50, yMax: 50, borderColor: 'rgba(0,0,0,0.3)', borderWidth: 2, borderDash: [5,5] }
                                    }
                                }
                            },
                            scales: {
                                x: { title: { display: true, text: 'Churn' }, beginAtZero: true },
                                y: { title: { display: true, text: 'Complexity' }, beginAtZero: true }
                            }
                        },
                        plugins: [backgroundZones]
                    });

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

                        tbody.innerHTML = filtered.map(d => `
                            <tr onclick='showDetails(${JSON.stringify(d).replace(/'/g, "\\\\'")})'>
                                <td>${d.label}</td>
                                <td>${d.churn}</td>
                                <td>${d.recentChurn}</td>
                                <td>${d.riskScore.toFixed(1)}</td>
                                <td>${d.y.toFixed(0)}</td>
                                <td>${d.lcom4.toFixed(1)}</td>
                                <td><span class="verdict-badge verdict-${d.verdict.split(' ')[0]}">${d.verdict}</span></td>
                            </tr>
                        `).join('');
                    }

                    function sortTable(column) {
                        if (currentSort.column === column) currentSort.ascending = !currentSort.ascending;
                        else { currentSort.column = column; currentSort.ascending = false; }
                        renderTable();
                    }

                    document.getElementById('classFilter').addEventListener('input', renderTable);
                    document.getElementById('verdictFilter').addEventListener('change', renderTable);

                    // Treemap rendering
                    function renderTreemap() {
                        const width = document.getElementById('treemap').clientWidth;
                        const height = 600;

                        d3.select('#treemap').selectAll('*').remove();

                        const svg = d3.select('#treemap')
                            .attr('width', width)
                            .attr('height', height);

                        const root = d3.hierarchy(treemapData)
                            .sum(d => d.value || 0)
                            .sort((a, b) => b.value - a.value);

                        d3.treemap()
                            .size([width, height])
                            .padding(2)
                            (root);

                        const cell = svg.selectAll('g')
                            .data(root.leaves())
                            .join('g')
                            .attr('transform', d => `translate(${d.x0},${d.y0})`);

                        cell.append('rect')
                            .attr('class', 'treemap-cell')
                            .attr('width', d => d.x1 - d.x0)
                            .attr('height', d => d.y1 - d.y0)
                            .attr('fill', d => getRiskColor(d.data.riskScore || 0))
                            .on('click', (event, d) => {
                                const classData = rawData.find(r => r.label === d.data.fullName);
                                if (classData) showDetails(classData);
                            });

                        cell.append('text')
                            .attr('class', 'treemap-label')
                            .attr('x', d => (d.x1 - d.x0) / 2)
                            .attr('y', d => (d.y1 - d.y0) / 2)
                            .text(d => d.data.name)
                            .style('font-size', d => Math.min((d.x1 - d.x0) / 8, (d.y1 - d.y0) / 3, 12) + 'px');
                    }

                    // Network rendering


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
                        svg.append('text')
                            .attr('x', container.clientWidth / 2)
                            .attr('y', container.clientHeight / 2)
                            .attr('text-anchor', 'middle')
                            .text('No significant temporal coupling found in top risky classes')
                            .style('font-size', '16px')
                            .style('fill', '#999');
                        return;
                    }

                    // Add zoom behavior
                    const g = svg.append('g');
                    svg.call(d3.zoom()
                        .extent([[0, 0], [width, height]])
                        .scaleExtent([0.1, 4])
                        .on('zoom', (event) => g.attr('transform', event.transform)))
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
                            .on('end', dragended));

                    const node = nodeGroup.append('circle')
                        .attr('class', 'node')
                        .attr('r', d => Math.sqrt(d.riskScore) * 2 + 5)
                        .attr('fill', d => getRiskColor(d.riskScore))
                        .on('click', (event, d) => {
                            event.stopPropagation(); // Prevent background click
                            const classData = rawData.find(r => r.label === d.id);
                            if (classData) showDetails(classData);

                            // Highlight Logic
                            const connectedNodes = new Set();
                            connectedNodes.add(d.id);

                            networkData.links.forEach(l => {
                                if (l.source.id === d.id) connectedNodes.add(l.target.id);
                                if (l.target.id === d.id) connectedNodes.add(l.source.id);
                            });

                            node.style('opacity', n => connectedNodes.has(n.id) ? 1 : 0.1);
                            link.style('opacity', l => (l.source.id === d.id || l.target.id === d.id) ? 1 : 0.05);
                            nodeGroup.selectAll('text').style('opacity', n => connectedNodes.has(n.id) ? 1 : 0.1);
                        });

                    // Add text label
                    nodeGroup.append('text')
                        .text(d => d.name)
                        .attr('x', d => Math.sqrt(d.riskScore) * 2 + 8)
                        .attr('y', 4)
                        .attr('class', 'node-label')
                        .style('font-size', '12px')
                        .style('fill', '#333')
                        .style('pointer-events', 'none')
                        .style('text-shadow', '1px 1px 2px white');

                    // Add link titles
                    link.append('title').text('Temporally Coupled');
                    node.append('title').text(d => d.name + ' (Risk: ' + d.riskScore.toFixed(1) + ')');

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
                        }

                        function dragged(event, d) {
                            d.fx = event.x;
                            d.fy = event.y;
                        }

                        function dragended(event, d) {
                            if (!event.active) simulation.alphaTarget(0);
                            d.fx = null;
                            d.fy = null;
                        }
                    }

                    function diagnose(d) {
                        if (d.verdict === 'BRAIN_METHOD') return { title: "One-Method Monster", desc: "Contains massive methods", why: `Found ${d.brainMethods.length} complex methods`, fix: "Extract Method pattern" };
                        if (d.verdict === 'SHOTGUN_SURGERY') return { title: "Shotgun Victim", desc: "Changes ripple everywhere", why: `Coupled to ${d.coupled} files`, fix: "Centralize logic" };
                        if (d.verdict === 'TOTAL_MESS') return { title: "Kitchen Sink", desc: "No clear responsibility", why: `LCOM4 = ${d.lcom4}`, fix: "Split the class" };
                                    if (d.verdict === 'DATA_CLASS') return { title: "Data Carrier", desc: "Mostly Getters/Setters", why: "80%+ of methods are boilerplate", fix: "Benign fragmentation. No action needed." };
                                    if (d.verdict === 'CONFIGURATION') return { title: "App Blueprint", desc: "Spring @Configuration Class", why: "High fragmentation is expected for factory classes", fix: "Benign fragmentation. No action needed." };
                                    if (d.verdict === 'ORCHESTRATOR') return { title: "Manager of Workers", desc: "Orchestrates complex flows", why: `High fan-out (${d.coupled}) but low internal complexity`, fix: "Maintainable pattern. Keep it cohesive." };                        if (d.verdict === 'GOD_CLASS') return { title: "God Class", desc: "Does everything", why: `Total CC ${d.y}`, fix: "Full refactor" };
                        return { title: "Healthy Code", desc: "No major issues", why: "Metrics OK", fix: "Keep it up!" };
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
                                <h2>${d.label.split('.').pop()}</h2>
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
}
