import assert from 'node:assert/strict';
import test from 'node:test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { createOctaPointMcpServer, type OctaPointSdkPort } from '../src/server.js';

function createStubSdk(calls: Array<{ op: string; args: unknown }>): OctaPointSdkPort {
  return {
    async balance(customerId) {
      calls.push({ op: 'balance', args: customerId });
      return { customerId, balance: 250, tier: 'SILVER' };
    },
    async history(customerId) {
      calls.push({ op: 'history', args: customerId });
      return [{ id: 'tx_1', customerId: customerId ?? 'C1', type: 'ISSUE', amount: 100 }];
    },
    async rules() {
      calls.push({ op: 'rules', args: null });
      return { earnVndPerPoint: 1000, redeemValueVndPerPoint: 1000 };
    },
    async summary() {
      calls.push({ op: 'summary', args: null });
      return { circulatingPoints: 250, totalCustomers: 1 };
    },
    async issue(params) {
      calls.push({ op: 'issue', args: params });
      return { transactionId: 'tx_bonus_1', pointsIssued: 50 };
    },
  };
}

test('default MCP server exposes strictly the 4 read-only tools (FR-17)', async () => {
  const calls: Array<{ op: string; args: unknown }> = [];
  const server = createOctaPointMcpServer(createStubSdk(calls));
  const client = new Client({ name: 'test-client', version: '1.0.0' });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();

  await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);

  const toolsList = await client.listTools();
  const names = toolsList.tools.map((t) => t.name).sort();
  assert.deepEqual(names, [
    'get_customer_balance',
    'get_tenant_rules',
    'get_tenant_summary',
    'list_customer_transactions',
  ]);

  const balResult = await client.callTool({
    name: 'get_customer_balance',
    arguments: { customerId: 'C1' },
  });
  const text = (balResult.content[0] as { type: string; text: string }).text;
  assert.match(text, /"balance": 250/);
  assert.equal(calls[0]?.op, 'balance');

  await client.close();
  await server.close();
});

test('write-enabled MCP server enforces 2-step human confirmation before calling issue (FR-32)', async () => {
  const calls: Array<{ op: string; args: unknown }> = [];
  const server = createOctaPointMcpServer(createStubSdk(calls), { writeToolsEnabled: true });
  const client = new Client({ name: 'test-client-write', version: '1.0.0' });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();

  await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);

  const toolsList = await client.listTools();
  assert.equal(toolsList.tools.length, 6);

  const prep = await client.callTool({
    name: 'prepare_bonus_grant',
    arguments: { customerId: 'C9', amountVnd: 50000, reason: 'Compensation for shipping delay' },
  });
  const prepJson = JSON.parse((prep.content[0] as { text: string }).text);
  assert.equal(prepJson.status, 'AWAITING_HUMAN_CONFIRMATION');
  assert.equal(calls.length, 0);

  const confirmed = await client.callTool({
    name: 'confirm_bonus_grant',
    arguments: { confirmationToken: prepJson.confirmationToken, humanApproved: true },
  });
  const confJson = JSON.parse((confirmed.content[0] as { text: string }).text);
  assert.equal(confJson.status, 'EXECUTED');
  assert.equal(calls.length, 1);
  assert.equal(calls[0]?.op, 'issue');

  // Replaying same confirmationToken must fail (single-use)
  const replay = await client.callTool({
    name: 'confirm_bonus_grant',
    arguments: { confirmationToken: prepJson.confirmationToken, humanApproved: true },
  });
  assert.equal(replay.isError, true);

  await client.close();
  await server.close();
});
