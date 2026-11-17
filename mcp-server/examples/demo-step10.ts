import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { OctaPointApiError, OctaPointClient } from '@octapoint/sdk';
import { createOctaPointMcpServer } from '../src/server.js';

const BASE_URL = (process.env.OCTAPOINT_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const ADMIN_EMAIL = process.env.OCTAPOINT_ADMIN_EMAIL ?? 'admin@octapoint.io';
const ADMIN_PASSWORD = process.env.OCTAPOINT_ADMIN_PASSWORD ?? 'admin123';

async function jsonPost(path: string, body: unknown, token?: string) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} on ${path}: ${JSON.stringify(data)}`);
  }
  return data;
}

async function main() {
  console.log(`=== OctaPoint Step 10 MCP Read-Only Demo (${BASE_URL}) ===`);
  const uniqueSuffix = Date.now().toString().slice(-6);
  const email = `mcp-demo-${uniqueSuffix}@octamart.io`;

  const reg = await jsonPost('/api/portal/auth/register', {
    businessName: `MCP Demo Store ${uniqueSuffix}`,
    name: 'MCP Store Owner',
    email,
    phone: '0909123456',
    password: 'Password123!',
  });

  const adminLogin = await jsonPost('/api/portal/auth/login', {
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
  });

  await fetch(`${BASE_URL}/api/admin/tenants/${reg.tenantId}/status`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${adminLogin.token}`,
    },
    body: JSON.stringify({ status: 'APPROVED' }),
  });

  const merchantLogin = await jsonPost('/api/portal/auth/login', {
    email,
    password: 'Password123!',
  });

  // 1. Issue an initial sample credit with full-scope key so MCP has real ledger data to query
  const fullKey = await jsonPost(
    '/api/portal/api-keys',
    { name: 'POS Full Key', scopes: 'ISSUE,REDEEM,READ' },
    merchantLogin.token,
  );
  const writeClient = new OctaPointClient({
    baseUrl: BASE_URL,
    apiKey: fullKey.apiKey,
    apiSecret: fullKey.apiSecret,
  });
  const issued = await writeClient.issue({
    customerId: 'MCP_CUST_1',
    orderId: `ORD_MCP_${uniqueSuffix}`,
    amountVnd: 250000,
  });
  console.log('[1] Seeded credit transaction:', issued);

  // 2. Create strict READ-only API key for AI Assistant / MCP server
  const readKey = await jsonPost(
    '/api/portal/api-keys',
    { name: 'AI Assistant Read-Only Key', scopes: 'READ' },
    merchantLogin.token,
  );
  const readOnlyClient = new OctaPointClient({
    baseUrl: BASE_URL,
    apiKey: readKey.apiKey,
    apiSecret: readKey.apiSecret,
  });

  // 3. Connect MCP Client <-> OctaPoint MCP Server (writeToolsEnabled=false)
  const mcpServer = createOctaPointMcpServer(readOnlyClient, { writeToolsEnabled: false });
  const mcpClient = new Client({ name: 'step10-demo-assistant', version: '1.0.0' });
  const [cTransport, sTransport] = InMemoryTransport.createLinkedPair();
  await Promise.all([mcpServer.connect(sTransport), mcpClient.connect(cTransport)]);

  const tools = await mcpClient.listTools();
  console.log('[2] MCP tools exposed:', tools.tools.map((t) => t.name));

  const rulesRes = await mcpClient.callTool({ name: 'get_tenant_rules', arguments: {} });
  console.log('[3] get_tenant_rules:', (rulesRes.content[0] as { text: string }).text);

  const summaryRes = await mcpClient.callTool({ name: 'get_tenant_summary', arguments: {} });
  console.log('[4] get_tenant_summary:', (summaryRes.content[0] as { text: string }).text);

  const balanceRes = await mcpClient.callTool({
    name: 'get_customer_balance',
    arguments: { customerId: 'MCP_CUST_1' },
  });
  console.log('[5] get_customer_balance:', (balanceRes.content[0] as { text: string }).text);

  const historyRes = await mcpClient.callTool({
    name: 'list_customer_transactions',
    arguments: { customerId: 'MCP_CUST_1' },
  });
  console.log('[6] list_customer_transactions:', (historyRes.content[0] as { text: string }).text);

  // 4. Verify READ-only API key cannot bypass backend scope enforcement
  try {
    await readOnlyClient.issue({ customerId: 'MCP_CUST_1', amountVnd: 10000 });
    throw new Error('Expected READ-only API key to be rejected on /issue');
  } catch (err) {
    if (err instanceof OctaPointApiError && err.status === 403 && err.errorCode === 'INSUFFICIENT_SCOPE') {
      console.log(`[7] Confirmed backend rejected write with READ-only key: 403 ${err.errorCode}`);
    } else {
      throw err;
    }
  }

  await mcpClient.close();
  await mcpServer.close();
  console.log('=== Step 10 MCP Demo Completed Successfully ===');
}

main().catch((err) => {
  console.error('Demo failed:', err);
  process.exit(1);
});
