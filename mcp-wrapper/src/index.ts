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
import { fileURLToPath } from "url";

// Fix for __dirname in ESM
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const execAsync = promisify(exec);

const REPO_ROOT = path.resolve(__dirname, "../../");

class PanopticonServer {
  private server: Server;
  private currentReportPath: string | null = null;

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
        if (this.currentReportPath && await fs.pathExists(this.currentReportPath)) {
          const content = await fs.readFile(this.currentReportPath, "utf-8");
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
              treatAllAsTest: {
                type: "boolean",
                description: "If true, treats ALL files as test code. IMPORTANT: Inspect the repo first. If you detect a test-only repo (e.g. Playwright, Cypress, Selenium), you MUST set this to true."
              },
              framework: {
                type: "string",
                description: "Manually specify the test framework (e.g. playwright, cypress, selenium) if you have identified it from config files (e.g. pom.xml, package.json)."
              }
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
        },
        {
          name: "get_test_health_summary",
          description: "Get a high-level overview of the project's testing state (frameworks, assertions, untested hotspots).",
          inputSchema: {
            type: "object",
            properties: {}
          }
        },
        {
          name: "find_untested_hotspots",
          description: "List highly complex/active files that have NO associated test file. Prioritizes where to add tests first.",
          inputSchema: {
            type: "object",
            properties: {
              limit: {
                type: "number",
                description: "Number of files to return (default 10)"
              }
            }
          }
        },
        {
          name: "get_file_test_profile",
          description: "Get detailed testing insights for a file: associated test path, framework used, assertion count, and test issues.",
          inputSchema: {
            type: "object",
            properties: {
              filePath: {
                type: "string",
                description: "Path of the file to inspect"
              }
            },
            required: ["filePath"]
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
        case "get_test_health_summary":
          return this.handleGetTestHealth();
        case "find_untested_hotspots":
          return this.handleFindUntestedHotspots(request.params.arguments);
        case "get_file_test_profile":
          return this.handleGetFileTestProfile(request.params.arguments);
        default:
          throw new McpError(ErrorCode.MethodNotFound, "Unknown tool");
      }
    });
  }

  private async handleAnalyzeCodebase(args: any) {
    if (!args.path) {
      throw new McpError(ErrorCode.InvalidParams, "Path is required");
    }

    let repoPath = args.path;
    const hotspotsOnly = args.hotspotsOnly ? "--hotspots-only" : "";
    let testOnly = args.treatAllAsTest ? "--test-only" : "";
    let framework = args.framework ? `--framework ${args.framework}` : "";

    // Note: We previously attempted auto-detection here, but it is better handled by the AI Agent
    // inspecting the repository first and passing the correct flags.
    // The Agent has access to `ls`, `cat package.json`, `cat pom.xml` etc.

    // Determine output directory relative to the analyzed repo
    // We assume repoPath is the root of the repo
    const outputDir = path.join(repoPath, "panopticon-reports");
    const jsonReportPath = path.join(outputDir, "panopticon-data.json");
    const htmlReportPath = path.join(outputDir, "panopticon-report.html");

    // Construct command
    const cmd = `./gradlew run --args="--repo ${repoPath} ${hotspotsOnly} ${testOnly} ${framework} --output ${outputDir}"`;

    try {
      const { stdout, stderr } = await execAsync(cmd, { cwd: REPO_ROOT, maxBuffer: 1024 * 1024 * 10 });

      // Check if report exists
      if (!(await fs.pathExists(jsonReportPath))) {
        return {
          content: [{
            type: "text",
            text: `Analysis failed or produced no output at ${outputDir}.\nStderr: ${stderr}\nStdout: ${stdout}`
          }],
          isError: true
        }
      }

      // Update current report path state
      this.currentReportPath = jsonReportPath;

      // Read JSON content
      const jsonContent = await fs.readFile(jsonReportPath, "utf-8");
      const data = JSON.parse(jsonContent);

      // Always return a compact summary to avoid exceeding MCP client token limits
      // Full JSON can be retrieved via the get_file_insights and other specific tools
      const totalFiles = data.files?.length || 0;

      // Get verdict distribution
      const verdictCounts: Record<string, number> = {};
      (data.files || []).forEach((f: any) => {
        const v = f.verdict || 'UNKNOWN';
        verdictCounts[v] = (verdictCounts[v] || 0) + 1;
      });

      // Get top 20 risky files with essential data only
      const topRiskyFiles = (data.files || [])
        .sort((a: any, b: any) => b.riskScore - a.riskScore)
        .slice(0, 20)
        .map((f: any) => ({
          file: f.label,
          riskScore: Math.round(f.riskScore * 10) / 10,
          verdict: f.verdict,
          complexity: f.y,
          churn: f.x,
          isTest: f.isTest || false
        }));

      // Build compact summary object
      const summary = {
        reportPath: htmlReportPath,
        jsonReportPath: jsonReportPath,
        totalFilesAnalyzed: totalFiles,
        verdictDistribution: verdictCounts,
        topRiskyFiles: topRiskyFiles,
        note: "Use get_file_insights(filePath) for detailed metrics on specific files. Use get_risk_summary(limit) for more risky files. Use get_test_health_summary() for testing insights."
      };

      return {
        content: [
          {
            type: "text",
            text: `Analysis Complete.\n\nHTML Report: ${htmlReportPath}\nTotal Files Analyzed: ${totalFiles}\n\nUse these tools for detailed insights:\n- get_file_insights(filePath): Detailed metrics for a specific file\n- get_risk_summary(limit): Get top N risky files\n- get_test_health_summary(): Testing state overview\n- find_untested_hotspots(limit): Files that need tests\n- get_file_test_profile(filePath): Test details for a file`,
          },
          {
            type: "text",
            text: JSON.stringify(summary, null, 2)
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

    try {
      const data = await this.loadReport();
      // Normalize slashes for comparison
      const normalizedSearchPath = args.filePath.replace(/\\/g, '/');

      const fileData = data.files.find((f: any) => {
        // Try strict match or suffix match
        return f.label === normalizedSearchPath || f.label.endsWith('/' + normalizedSearchPath);
      });

      if (!fileData) {
        return {
          content: [{ type: "text", text: `File not found in report: ${args.filePath}` }],
          isError: true
        };
      }

      return {
        content: [{ type: "text", text: JSON.stringify(fileData, null, 2) }]
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: e.message }],
        isError: true
      };
    }
  }

  private async handleGetRiskSummary(args: any) {
    try {
      const limit = args.limit || 10;
      const data = await this.loadReport();

      const topRisks = data.files
        .sort((a: any, b: any) => b.riskScore - a.riskScore)
        .slice(0, limit)
        .map((f: any) => `${f.label} (Risk: ${f.riskScore.toFixed(1)}, Verdict: ${f.verdict})`)
        .join("\n");

      return {
        content: [{ type: "text", text: `Top ${limit} Risky Files:\n${topRisks}` }]
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: e.message }],
        isError: true
      };
    }
  }

  private async handleGetTestHealth() {
    try {
      const data = await this.loadReport();
      const files = data.files || [];

      const testFiles = files.filter((f: any) => f.isTest);
      const totalFiles = files.length;
      const totalTestFiles = testFiles.length;

      let totalAssertions = 0;
      const frameworks: Record<string, number> = {};
      let untestedHotspotsCount = 0;

      files.forEach((f: any) => {
        if (f.isTest) {
          const profile = f.testProfile || {};
          if (profile.Assertions) totalAssertions += parseInt(profile.Assertions) || 0;
          if (profile.Framework) {
            const fw = profile.Framework;
            frameworks[fw] = (frameworks[fw] || 0) + 1;
          }
        } else {
          // Check if untested hotspot
          if (f.verdict === 'UNTESTED_HOTSPOT' || f.isUntestedHotspot) {
            untestedHotspotsCount++;
          }
        }
      });

      return {
        content: [{
          type: "text",
          text: JSON.stringify({
            totalFiles,
            totalTestFiles,
            testRatio: totalFiles > 0 ? (totalTestFiles / totalFiles).toFixed(2) : 0,
            totalAssertions,
            frameworks,
            criticalUntestedHotspots: untestedHotspotsCount
          }, null, 2)
        }]
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: e.message }],
        isError: true
      };
    }
  }

  private async handleFindUntestedHotspots(args: any) {
    try {
      const limit = args.limit || 10;
      const data = await this.loadReport();

      const hotspots = (data.files || [])
        .filter((f: any) => !f.isTest && (f.verdict === 'UNTESTED_HOTSPOT' || f.isUntestedHotspot))
        .sort((a: any, b: any) => b.riskScore - a.riskScore)
        .slice(0, limit);

      if (hotspots.length === 0) {
        return { content: [{ type: "text", text: "No critical untested hotspots found. Great job!" }] };
      }

      const result = hotspots.map((f: any) => ({
        file: f.label,
        riskScore: f.riskScore,
        complexity: f.y,
        churn: f.x,
        verdict: f.verdict
      }));

      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }]
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: e.message }],
        isError: true
      };
    }
  }

  private async handleGetFileTestProfile(args: any) {
    if (!args.filePath) {
      throw new McpError(ErrorCode.InvalidParams, "filePath is required");
    }

    try {
      const data = await this.loadReport();
      const normalizedSearchPath = args.filePath.replace(/\\/g, '/');

      const fileData = data.files.find((f: any) => {
        return f.label === normalizedSearchPath || f.label.endsWith('/' + normalizedSearchPath);
      });

      if (!fileData) {
        return {
          content: [{ type: "text", text: `File not found in report: ${args.filePath}` }],
          isError: true
        };
      }

      const profile = {
        file: fileData.label,
        isTest: fileData.isTest || false,
        hasTestFile: fileData.hasTestFile || false,
        testFilePath: fileData.testFilePath || null,
        testabilityScore: fileData.testabilityScore,
        testProfile: fileData.testProfile || {},
        testIssues: fileData.testIssues || []
      };

      return {
        content: [{ type: "text", text: JSON.stringify(profile, null, 2) }]
      };
    } catch (e: any) {
      return {
        content: [{ type: "text", text: e.message }],
        isError: true
      };
    }
  }

  private async loadReport() {
    if (!this.currentReportPath) {
      throw new McpError(ErrorCode.InvalidRequest, "No analysis has been run yet. Please run analyze_codebase first.");
    }
    if (!(await fs.pathExists(this.currentReportPath))) {
      throw new McpError(ErrorCode.InvalidRequest, `Report file missing at ${this.currentReportPath}. Please analyze again.`);
    }
    return JSON.parse(await fs.readFile(this.currentReportPath, "utf-8"));
  }

  async run() {
    const transport = new StdioServerTransport();
    await this.server.connect(transport);
    console.error("Code Panopticon MCP Server running on stdio");
  }
}

const server = new PanopticonServer();
server.run().catch(console.error);
