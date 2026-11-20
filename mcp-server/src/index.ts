import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { OctaPointClient } from '@octapoint/sdk';
import { createOctaPointMcpServer } from './server.js';

function requiredEnv(name: string): string {
  const val = process.env[name];
  if (!val || val.trim() === '') {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return val.trim();
}

async function main() {
  const baseUrl = process.env.OCTAPOINT_BASE_URL?.trim() || 'http://localhost:8080';
  const apiKey = requiredEnv('OCTAPOINT_API_KEY');
  const apiSecret = requiredEnv('OCTAPOINT_API_SECRET');
  const writeToolsEnabled = process.env.MCP_WRITE_TOOLS_ENABLED === 'true';

  const sdkClient = new OctaPointClient({ baseUrl, apiKey, apiSecret });
  const mcpServer = createOctaPointMcpServer(sdkClient, { writeToolsEnabled });

  const transport = new StdioServerTransport();
  await mcpServer.connect(transport);
}

main().catch((err) => {
  console.error('[octapoint-mcp] Fatal startup error:', err instanceof Error ? err.message : String(err));
  process.exit(1);
});
