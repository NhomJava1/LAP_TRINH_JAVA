import * as crypto from 'node:crypto';
import type { IssueParams, OctaPointClient, RedeemParams } from './client.js';

export type QueuedOperation =
  | {
      idempotencyKey: string;
      type: 'ISSUE';
      params: IssueParams;
      enqueuedAt: string;
    }
  | {
      idempotencyKey: string;
      type: 'REDEEM';
      params: RedeemParams;
      enqueuedAt: string;
    };

export interface FlushResult {
  processed: number;
  remaining: number;
  errors: Array<{ idempotencyKey: string; message: string }>;
}

/**
 * In-memory FIFO offline queue for store-and-forward POS workflows (FR-26).
 * Preserves each item's idempotencyKey so network retries never double-spend.
 */
export class OfflineCreditQueue {
  private readonly items: QueuedOperation[] = [];

  enqueueIssue(params: IssueParams): QueuedOperation {
    const idempotencyKey = params.idempotencyKey ?? crypto.randomUUID();
    const op: QueuedOperation = {
      idempotencyKey,
      type: 'ISSUE',
      params: { ...params, idempotencyKey },
      enqueuedAt: new Date().toISOString(),
    };
    this.items.push(op);
    return op;
  }

  enqueueRedeem(params: RedeemParams): QueuedOperation {
    const idempotencyKey = params.idempotencyKey ?? crypto.randomUUID();
    const op: QueuedOperation = {
      idempotencyKey,
      type: 'REDEEM',
      params: { ...params, idempotencyKey },
      enqueuedAt: new Date().toISOString(),
    };
    this.items.push(op);
    return op;
  }

  size(): number {
    return this.items.length;
  }

  peekAll(): ReadonlyArray<QueuedOperation> {
    return [...this.items];
  }

  async flush(client: Pick<OctaPointClient, 'issue' | 'redeem'>): Promise<FlushResult> {
    let processed = 0;
    const errors: Array<{ idempotencyKey: string; message: string }> = [];
    const retained: QueuedOperation[] = [];

    for (const op of this.items) {
      try {
        if (op.type === 'ISSUE') {
          await client.issue(op.params);
        } else {
          await client.redeem(op.params);
        }
        processed++;
      } catch (err) {
        retained.push(op);
        errors.push({
          idempotencyKey: op.idempotencyKey,
          message: err instanceof Error ? err.message : String(err),
        });
      }
    }

    this.items.splice(0, this.items.length, ...retained);
    return {
      processed,
      remaining: this.items.length,
      errors,
    };
  }
}
