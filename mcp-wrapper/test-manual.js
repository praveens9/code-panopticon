
const { spawn } = require('child_process');
const path = require('path');

const serverPath = path.resolve(__dirname, 'dist/index.js');
const server = spawn('node', [serverPath], {
    stdio: ['pipe', 'pipe', process.stderr]
});

console.log('Server started. Sending list_tools request...');

// JSON-RPC 2.0 Request for list_tools
const listToolsRequest = {
    jsonrpc: "2.0",
    id: 1,
    method: "tools/list",
    params: {}
};

server.stdin.write(JSON.stringify(listToolsRequest) + '\n');

// JSON-RPC 2.0 Request for get_risk_summary
const riskSummaryRequest = {
    jsonrpc: "2.0",
    id: 2,
    method: "tools/call",
    params: {
        name: "get_risk_summary",
        arguments: { limit: 3 }
    }
};


let buffer = '';

server.stdout.on('data', (data) => {
    buffer += data.toString();
    const lines = buffer.split('\n');
    buffer = lines.pop(); // Keep incomplete line

    for (const line of lines) {
        if (!line.trim()) continue;
        console.log('Received:', line);
        try {
            const response = JSON.parse(line);
            if (response.id === 1) {
                console.log('Tools listed successfully.');
                // Send next request
                server.stdin.write(JSON.stringify(riskSummaryRequest) + '\n');
            } else if (response.id === 2) {
                console.log('Risk summary received successfully.');
                // console.log(JSON.stringify(response, null, 2));
                server.kill();
                process.exit(0);
            }
        } catch (e) {
            console.error('Failed to parse response:', e);
        }
    }
});
