#!/usr/bin/env node
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
    CallToolRequestSchema,
    ErrorCode,
    ListResourcesRequestSchema,
    ListToolsRequestSchema,
    McpError,
    ReadResourceRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import fs from "fs-extra";
import path from "path";
import { z } from "zod";
import { exec } from "child_process";
import { promisify } from "util";

const execAsync = promisify(exec);

const REPO_ROOT = path.resolve(__dirname, "../../");
const REPORTS_DIR = path.join(REPO_ROOT, "reports");
const JSON_REPORT_PATH = path.join(REPORTS_DIR, "panopticon-data.json");
const HTML_REPORT_PATH = path.join(REPORTS_DIR, "panopticon-report.html");

class PanopticonServer {
    private server: Server;

    constructor() {
        this.server = new Server(
            {
                name: "code-panopticon",
                version: "1.0.0",
            },
            {
                capabilities: {
                    resources: {},
                    tools: {},
                },
            }
        );

        this.setupResourceHandlers();
        this.setupToolHandlers();

        // Error handling
        this.server.onerror = (error) => console.error("[MCP Error]", error);
        process.on("SIGINT", async () => {
            await this.server.close();
            process.exit(0);
        });
    }

    private setupResourceHandlers() {
        this.server.setRequestHandler(ListResourcesRequestSchema, async () => ({
            resources: [
                {
                    uri: "panopticon://latest-report",
                    name: "Latest Analysis Report",
                    mimeType: "application/json",
                    description: "Full JSON content of the latest Code Panopticon analysis",
                },
            ],
        }));

        this.server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
            const uri = request.params.uri;
            if (uri === "panopticon://latest-report") {
                if (await fs.pathExists(JSON_REPORT_PATH)) {
                    const content = await fs.readFile(JSON_REPORT_PATH, "utf-8");
                    return {
                        contents: [
                            {
                                uri: "panopticon://latest-report",
                                mimeType: "application/json",
                                text: content,
                            },
                        ],
                    };
                } else {
                    throw new McpError(ErrorCode.InvalidRequest, "No report found. Run analyze_codebase first.");
                }
            }
            // Handle file specific metrics if needed in future
            throw new McpError(ErrorCode.InvalidRequest, `Unknown resource: ${uri}`);
        });
    }

    private setupToolHandlers() {
        this.server.setRequestHandler(ListToolsRequestSchema, async () => ({
            tools: [
                {
                    name: "analyze_codebase",
                    description: "Run Code Panopticon analysis on a repository. Returns HTML report path and full JSON analysis data for AI context.",
                    inputSchema: {
                        type: "object",
                        properties: {
                            path: {
                                type: "string",
                                description: "Path to the repository to analyze (local path or git URL)",
                            },
                            hotspotsOnly: {
                                type: "boolean",
                                description: "If true, only analyze files with git activity (faster for large repos)",
                            },
                        },
                        required: ["path"],
                    },
                },
                {
                    name: "get_file_insights",
                    description: "Get detailed metrics and forensics for a specific file from the latest report.",
                    inputSchema: {
                        type: "object",
                        properties: {
                            filePath: {
                                type: "string",
                                description: "Relative path of the file to inspect (e.g., 'src/main/java/Main.java')",
                            },
                        },
                        required: ["filePath"],
                    },
                },
                {
                    name: "get_risk_summary",
                    description: "Get the top risky files and overall verdict distribution.",
                    inputSchema: {
                        type: "object",
                        properties: {
                            limit: {
                                type: "number",
                                description: "Number of top risky files to return (default 10)",
                            }
                        }
                    }
                }
            ],
        }));

        this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
            switch (request.params.name) {
                case "analyze_codebase":
                    return this.handleAnalyzeCodebase(request.params.arguments);
                case "get_file_insights":
                    return this.handleGetFileInsights(request.params.arguments);
                case "get_risk_summary":
                    return this.handleGetRiskSummary(request.params.arguments);
                default:
                    throw new McpError(ErrorCode.MethodNotFound, "Unknown tool");
            }
        });
    }

    private async handleAnalyzeCodebase(args: any) {
        if (!args.path) {
            throw new McpError(ErrorCode.InvalidParams, "Path is required");
        }

        const repoPath = args.path;
        const hotspotsOnly = args.hotspotsOnly ? "--hotspots-only" : "";

        // Construct command - assuming existing wrapper or invoking gradlew directly from root
        // We use ./gradlew run from the root of the project
        const cmd = `./gradlew run --args="--repo ${repoPath} ${hotspotsOnly} --output reports"`;

        try {
            // Execute analysis
            // Note: This might take time. We wait for it.
            const { stdout, stderr } = await execAsync(cmd, { cwd: REPO_ROOT, maxBuffer: 1024 * 1024 * 10 }); // 10MB buffer for logs

            // Check if report exists
            if (!(await fs.pathExists(JSON_REPORT_PATH))) {
                return {
                    content: [{
                        type: "text",
                        text: `Analysis failed or produced no output.\nStderr: ${stderr}\nStdout: ${stdout}`
                    }],
                    isError: true
                }
            }

            // Read JSON content
            const jsonContent = await fs.readFile(JSON_REPORT_PATH, "utf-8");

            // Safety check for JSON size (though 2MB is fine for most contexts, let's just warn if huge)
            const dataSize = jsonContent.length;
            let returnContent = jsonContent;
            let note = "";

            if (dataSize > 50 * 1024 * 1024) { // 50MB limit
                note = "NOTE: Full JSON report is too large (>50MB). Returning summary only. Use get_file_insights for details.";
                // TODO: Implement lighter summary parse if needed, for now just slice or depend on tools
                // Actually, let's parse and strip 'files' detailed list if too big, just keeping top 50 
                const data = JSON.parse(jsonContent);
                if (data.files && data.files.length > 100) {
                    data.files = data.files.sort((a: any, b: any) => b.riskScore - a.riskScore).slice(0, 100);
                    returnContent = JSON.stringify(data);
                }
            }

            return {
                content: [
                    {
                        type: "text",
                        text: `Analysis Complete.\n\nHTML Report: ${HTML_REPORT_PATH}\n\n${note}`,
                    },
                    {
                        type: "text", // Can use "resource" type but sticking to text for immediate Context inclusion as per request works well with Claude
                        text: returnContent
                    }
                ],
            };

        } catch (error: any) {
            return {
                content: [
                    {
                        type: "text",
                        text: `Error executing analysis: ${error.message}\n${error.stderr || ""}`,
                    },
                ],
                isError: true,
            };
        }
    }

    private async handleGetFileInsights(args: any) {
        if (!args.filePath) {
            throw new McpError(ErrorCode.InvalidParams, "filePath is required");
        }

        const data = await this.loadReport();
        const fileData = data.files.find((f: any) => f.label.includes(args.filePath)); // Loose match or exact match?

        if (!fileData) {
            return {
                content: [{ type: "text", text: `File not found in report: ${args.filePath}` }],
                isError: true
            };
        }

        return {
            content: [{ type: "text", text: JSON.stringify(fileData, null, 2) }]
        };
    }

    private async handleGetRiskSummary(args: any) {
        const limit = args.limit || 10;
        const data = await this.loadReport();

        // Sort by risk score
        const topRisks = data.files
            .sort((a: any, b: any) => b.riskScore - a.riskScore)
            .slice(0, limit)
            .map((f: any) => `${f.label} (Risk: ${f.riskScore.toFixed(1)}, Verdict: ${f.verdict})`)
            .join("\n");

        return {
            content: [{ type: "text", text: `Top ${limit} Risky Files:\n${topRisks}` }]
        };
    }

    private async loadReport() {
        if (!(await fs.pathExists(JSON_REPORT_PATH))) {
            throw new McpError(ErrorCode.InvalidRequest, "Report not found. Please run analyze_codebase first.");
        }
        return JSON.parse(await fs.readFile(JSON_REPORT_PATH, "utf-8"));
    }

    async run() {
        const transport = new StdioServerTransport();
        await this.server.connect(transport);
        console.error("Code Panopticon MCP Server running on stdio");
    }
}

const server = new PanopticonServer();
server.run().catch(console.error);
