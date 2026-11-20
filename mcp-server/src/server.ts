import * as crypto from 'node:crypto';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';

export interface OctaPointSdkPort {
  balance(customerId: string): Promise<unknown>;
  history(customerId?: string): Promise<unknown>;
  rules(): Promise<unknown>;
  summary(): Promise<unknown>;
  issue(params: {
    customerId: string;
    orderId?: string;
    amountVnd: number;
    idempotencyKey?: string;
  }): Promise<unknown>;
}

export interface CreateMcpServerOptions {
  writeToolsEnabled?: boolean;
}

interface PendingGrantProposal {
  confirmationToken: string;
  customerId: string;
  amountVnd: number;
  reason: string;
  idempotencyKey: string;
  expiresAtMs: number;
}

const PROPOSAL_TTL_MS = 5 * 60 * 1000;

function jsonContent(payload: unknown) {
  return {
    content: [
      {
        type: 'text' as const,
        text: JSON.stringify(payload, null, 2),
      },
    ],
  };
}

/**
 * Builds the OctaPoint MCP Server (FR-17 read-only tools & FR-32 human-confirmed write tools).
 * Write tools are NOT registered unless options.writeToolsEnabled === true.
 */
export function createOctaPointMcpServer(
  client: OctaPointSdkPort,
  options?: CreateMcpServerOptions,
): McpServer {
  const writeToolsEnabled = options?.writeToolsEnabled === true;
  const pendingGrants = new Map<string, PendingGrantProposal>();

  const server = new McpServer({
    name: 'octapoint-mcp',
    version: '1.0.0',
  });

  server.registerTool(
    'get_customer_balance',
    {
      title: 'Get Customer Point Balance',
      description:
        'Retrieve current OctaPoint credit balance, lifetime points, loyalty tier, and latest Sui settlement state for a customer.',
      inputSchema: {
        customerId: z.string().min(1).describe('Merchant customer identifier (e.g., C1 or cust_123)'),
      },
    },
    async ({ customerId }) => {
      const data = await client.balance(customerId);
      return jsonContent(data);
    },
  );

  server.registerTool(
    'list_customer_transactions',
    {
      title: 'List Customer Credit Transactions',
      description:
        'List recent OctaPoint ISSUE/REDEEM ledger entries and settlement txDigests, optionally filtered by customerId.',
      inputSchema: {
        customerId: z.string().optional().describe('Optional customer ID filter; omit to list latest tenant entries'),
      },
    },
    async ({ customerId }) => {
      const data = await client.history(customerId);
      return jsonContent(data);
    },
  );

  server.registerTool(
    'get_tenant_rules',
    {
      title: 'Get Tenant Point & Tier Rules',
      description:
        'Read active earn rate, redeem rate, point expiry days, and Silver/Gold tier multipliers configured for the merchant tenant.',
      inputSchema: {},
    },
    async () => {
      const data = await client.rules();
      return jsonContent(data);
    },
  );

  server.registerTool(
    'get_tenant_summary',
    {
      title: 'Get Tenant Credit & Settlement Summary',
      description:
        'Read circulating points, customer count, issued/redeemed totals, and outbox settlement breakdown for the tenant.',
      inputSchema: {},
    },
    async () => {
      const data = await client.summary();
      return jsonContent(data);
    },
  );

  if (writeToolsEnabled) {
    server.registerTool(
      'prepare_bonus_grant',
      {
        title: 'Prepare Bonus Credit Grant (Requires Human Confirmation)',
        description:
          'Stage a bonus point grant proposal and return a single-use confirmationToken. Does NOT mutate balances until confirm_bonus_grant is called with humanApproved=true.',
        inputSchema: {
          customerId: z.string().min(1),
          amountVnd: z.number().int().positive(),
          reason: z.string().min(3),
        },
      },
      async ({ customerId, amountVnd, reason }) => {
        const confirmationToken = `grant_${crypto.randomBytes(8).toString('hex')}`;
        const idempotencyKey = `mcp_${crypto.randomUUID()}`;
        const expiresAtMs = Date.now() + PROPOSAL_TTL_MS;

        const proposal: PendingGrantProposal = {
          confirmationToken,
          customerId,
          amountVnd,
          reason,
          idempotencyKey,
          expiresAtMs,
        };
        pendingGrants.set(confirmationToken, proposal);

        return jsonContent({
          status: 'AWAITING_HUMAN_CONFIRMATION',
          confirmationToken,
          customerId,
          amountVnd,
          reason,
          expiresAt: new Date(expiresAtMs).toISOString(),
          instruction:
            'Present these details to the human operator and only call confirm_bonus_grant when explicitly approved.',
        });
      },
    );

    server.registerTool(
      'confirm_bonus_grant',
      {
        title: 'Confirm Bonus Credit Grant',
        description:
          'Execute a previously staged bonus grant only when humanApproved is explicitly true and the single-use confirmationToken is valid.',
        inputSchema: {
          confirmationToken: z.string().min(1),
          humanApproved: z.boolean(),
        },
      },
      async ({ confirmationToken, humanApproved }) => {
        if (!humanApproved) {
          pendingGrants.delete(confirmationToken);
          return jsonContent({
            status: 'CANCELLED',
            reason: 'Human operator did not approve bonus grant.',
          });
        }

        const proposal = pendingGrants.get(confirmationToken);
        if (!proposal) {
          throw new Error('Unknown or already-consumed confirmationToken');
        }
        pendingGrants.delete(confirmationToken);

        if (Date.now() > proposal.expiresAtMs) {
          throw new Error('confirmationToken has expired; please prepare a new proposal');
        }

        const issued = await client.issue({
          customerId: proposal.customerId,
          orderId: `MCP_BONUS_${proposal.confirmationToken}`,
          amountVnd: proposal.amountVnd,
          idempotencyKey: proposal.idempotencyKey,
        });

        return jsonContent({
          status: 'EXECUTED',
          reason: proposal.reason,
          result: issued,
        });
      },
    );
  }

  return server;
}
