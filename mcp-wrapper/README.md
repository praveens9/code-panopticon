# Code Panopticon MCP Server

This is a Model Context Protocol (MCP) server for Code Panopticon, allowing AI agents to analyze this repository and understand its architectural risks.

## Prerequisites

- Node.js 18+
- Java 17+ (for running the underlying Code Panopticon CLI)

## Configuration

Add this to your `claude_desktop_config.json` (usually in `~/Library/Application Support/Claude/` on macOS):

```json
{
  "mcpServers": {
    "code-panopticon": {
      "command": "node",
      "args": [
        "/absolute/path/to/code-panopticon/mcp-wrapper/dist/index.js"
      ]
    }
  }
}
```

## Tools

- `analyze_codebase`: Runs analysis and returns top risks + path to HTML report.
  - `path`: Repository path or URL (required)
  - `hotspotsOnly`: Analyze only active files (boolean)
  - `treatAllAsTest`: Treat all files as test code (boolean)
  - `framework`: Manual framework override (string)
- `get_risk_summary`: Get a quick summary of the "Burning Platforms".
- `get_file_insights`: Get detailed metrics for a specific file.
- `get_test_health_summary`: Get overall testing stats (asserts, frameworks, hotspots).
- `find_untested_hotspots`: List critical files needing tests.
- `get_file_test_profile`: Get test-specific details for a file.

## Development

1. `npm install`
2. `npm run build`
3. `npm start` (Runs via stdio)
