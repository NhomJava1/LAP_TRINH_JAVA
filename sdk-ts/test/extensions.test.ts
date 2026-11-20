import assert from 'node:assert/strict';
import test from 'node:test';
import {
  hmacSha256Hex,
  OfflineCreditQueue,
  verifyWebhookSignature,
} from '../src/index.js';

test('verifyWebhookSignature validates genuine signatures and rejects tampered/expired payloads', () => {
  const secret = 'whsec_octapoint_2026';
  const ts = '1700000000';
  const body = JSON.stringify({ event: 'settlement.settled', transactionId: 'tx_123' });
  const sig = hmacSha256Hex(secret, `${ts}.${body}`);

  assert.equal(
    verifyWebhookSignature({
      webhookSecret: secret,
      timestamp: ts,
      rawBody: body,
      signature: sig,
      nowSeconds: 1700000050,
    }),
    true,
  );

  assert.equal(
    verifyWebhookSignature({
      webhookSecret: secret,
      timestamp: ts,
      rawBody: body,
      signature: '0'.repeat(64),
      nowSeconds: 1700000050,
    }),
    false,
  );

  assert.equal(
    verifyWebhookSignature({
      webhookSecret: secret,
      timestamp: ts,
      rawBody: body,
      signature: sig,
      nowSeconds: 1700001000,
      toleranceSeconds: 300,
    }),
    false,
  );
});

test('OfflineCreditQueue preserves FIFO order and idempotencyKey across partial flush failures', async () => {
  const queue = new OfflineCreditQueue();
  const op1 = queue.enqueueIssue({ customerId: 'C1', amountVnd: 10000, orderId: 'O1' });
  const op2 = queue.enqueueRedeem({ customerId: 'C1', pointsToRedeem: 5, orderId: 'O2' });
  const op3 = queue.enqueueIssue({ customerId: 'C2', amountVnd: 20000, orderId: 'O3' });

  assert.equal(queue.size(), 3);
  const seenKeys: string[] = [];

  const firstFlush = await queue.flush({
    async issue(params) {
      seenKeys.push(`ISSUE:${params.idempotencyKey}`);
      return { ok: true };
    },
    async redeem(params) {
      seenKeys.push(`REDEEM:${params.idempotencyKey}`);
      throw new Error('Temporary network outage');
    },
  });

  assert.equal(firstFlush.processed, 2);
  assert.equal(firstFlush.remaining, 1);
  assert.equal(firstFlush.errors[0]?.idempotencyKey, op2.idempotencyKey);
  assert.deepEqual(seenKeys, [
    `ISSUE:${op1.idempotencyKey}`,
    `REDEEM:${op2.idempotencyKey}`,
    `ISSUE:${op3.idempotencyKey}`,
  ]);

  const retriedKeys: string[] = [];
  const secondFlush = await queue.flush({
    async issue() {
      return { ok: true };
    },
    async redeem(params) {
      retriedKeys.push(params.idempotencyKey ?? '');
      return { ok: true };
    },
  });

  assert.equal(secondFlush.processed, 1);
  assert.equal(secondFlush.remaining, 0);
  assert.deepEqual(retriedKeys, [op2.idempotencyKey]);
});
