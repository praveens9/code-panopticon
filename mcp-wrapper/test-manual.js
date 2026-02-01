
import { spawn } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const serverPath = path.resolve(__dirname, 'dist/index.js');
const server = spawn('node', [serverPath], {
    stdio: ['pipe', 'pipe', process.stderr]
});

console.log('Server started. Sending requests...');

// 1. List tools
const listToolsRequest = { jsonrpc: "2.0", id: 1, method: "tools/list", params: {} };

// 2. Analyze current repo (we use the current directory for testing)
const analyzeRequest = {
    jsonrpc: "2.0",
    id: 2,
    method: "tools/call",
    params: {
        name: "analyze_codebase",
        arguments: { path: process.cwd() } // this is mcp-wrapper dir
    }
};

// 3. Get risk summary
const riskSummaryRequest = {
    jsonrpc: "2.0",
    id: 3,
    method: "tools/call",
    params: {
        name: "get_risk_summary",
        arguments: { limit: 3 }
    }
};

server.stdin.write(JSON.stringify(listToolsRequest) + '\n');

let buffer = '';

server.stdout.on('data', (data) => {
    buffer += data.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop();

    for (const line of lines) {
        if (!line.trim()) continue;
        // console.log('RX:', line.substring(0, 100)); 
        try {
            const response = JSON.parse(line);
            if (response.id === 1) {
                console.log('Tools listed. Starting analysis (this may take time)...');
                // Use the PARENT directory (code-panopticon root) for analysis
                analyzeRequest.params.arguments.path = path.resolve(__dirname, '../');
                server.stdin.write(JSON.stringify(analyzeRequest) + '\n');
            } else if (response.id === 2) {
                console.log('Analysis complete.');
                if (response.error) {
                    console.error('Analysis failed:', response.error);
                    process.exit(1);
                }
                console.log('Requesting risk summary...');
                server.stdin.write(JSON.stringify(riskSummaryRequest) + '\n');
            } else if (response.id === 3) {
                console.log('Risk summary received.');
                if (response.error) {
                    console.error('Risk summary failed:', response.error);
                }
                // console.log(JSON.stringify(response, null, 2));
                server.kill();
                process.exit(0);
            }
        } catch (e) {
            console.error('Failed to parse response:', e);
        }
    }
});
