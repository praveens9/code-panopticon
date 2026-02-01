#!/bin/bash
set -e

# setup-mcp.sh - Builds the MCP server and prints configuration

echo "🔎 Checking prerequisites..."

if ! command -v git &> /dev/null; then
    echo "❌ git is not installed."
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "❌ node is not installed."
    exit 1
fi

if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed."
    exit 1
fi

# Check for Java 17+
if ! command -v java &> /dev/null; then
    echo "❌ java is not installed. Java 17+ is required."
    exit 1
fi

echo "✅ Prerequisites met."

REPO_ROOT=$(pwd)
MCP_DIR="$REPO_ROOT/mcp-wrapper"

echo "📦 Installing MCP server dependencies..."
cd "$MCP_DIR"
npm install

echo "🛠️  Building MCP server..."
npm run build

echo "✅ MCP Server built successfully."
echo ""
echo "================================================================"
echo "🚀 To use Code Panopticon with Claude Desktop, add this config:"
echo "   File: ~/Library/Application Support/Claude/claude_desktop_config.json"
echo "================================================================"

cat <<EOF
{
  "mcpServers": {
    "code-panopticon": {
      "command": "node",
      "args": [
        "$MCP_DIR/dist/index.js"
      ]
    }
  }
}
EOF

echo "================================================================"
echo ""
