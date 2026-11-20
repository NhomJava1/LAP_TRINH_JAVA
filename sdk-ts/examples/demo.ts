import * as crypto from 'node:crypto';
import { OctaPointApiError, OctaPointClient } from '../src/index.js';

const BASE_URL = (process.env.OCTAPOINT_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

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
    throw new Error(`POST ${path} failed (${res.status}): ${JSON.stringify(data)}`);
  }
  return data;
}

async function jsonPatch(path: string, body: unknown, token: string) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(`PATCH ${path} failed (${res.status}): ${JSON.stringify(data)}`);
  }
  return data;
}

async function jsonPut(path: string, body: unknown, token: string) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(`PUT ${path} failed (${res.status}): ${JSON.stringify(data)}`);
  }
  return data;
}

async function jsonGet(path: string, token: string) {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(`GET ${path} failed (${res.status}): ${JSON.stringify(data)}`);
  }
  return data;
}

async function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  let apiKey = process.env.API_KEY;
  let apiSecret = process.env.API_SECRET;
  let adminToken: string | undefined;
  let merchantToken: string | undefined;

  if (!apiKey || !apiSecret) {
    const suffix = Date.now();
    const merchantEmail = `sieuthi-a-${suffix}@demo.octapoint.io`;
    const merchantPass = 'Demo@12345';

    console.log('\n[Step 1] Registering Merchant "Siêu thị A"...');
    const reg = await jsonPost('/api/portal/auth/register', {
      businessName: `Siêu thị A (${suffix})`,
      name: 'Chủ Siêu Thị A',
      email: merchantEmail,
      phone: '0909123456',
      password: merchantPass,
    });
    console.log(' -> Registered tenant:', reg);

    console.log('\n[Step 2] Logging in as Admin & approving tenant...');
    const adminLogin = await jsonPost('/api/portal/auth/login', {
      email: process.env.ADMIN_EMAIL ?? 'admin@octapoint.io',
      password: process.env.ADMIN_PASSWORD ?? 'admin123',
    });
    adminToken = adminLogin.token;
    const approved = await jsonPatch(`/api/admin/tenants/${reg.tenantId}/status`, { status: 'APPROVED' }, adminToken!);
    console.log(' -> Tenant status:', approved.status);

    console.log('\n[Step 3] Logging in as Merchant, creating API Key & setting Rule (1000 VND = 1 pt)...');
    const merchantLogin = await jsonPost('/api/portal/auth/login', {
      email: merchantEmail,
      password: merchantPass,
    });
    merchantToken = merchantLogin.token;
    const keyRes = await jsonPost('/api/portal/api-keys', { name: 'POS Demo Key' }, merchantToken!);
    apiKey = keyRes.apiKey;
    apiSecret = keyRes.apiSecret;
    console.log(' -> Created API Key:', apiKey);
    const rule = await jsonPut('/api/portal/rules', { earnVndPerPoint: 1000, redeemValueVndPerPoint: 10 }, merchantToken!);
    console.log(' -> Saved Rule:', rule);
  }

  const client = new OctaPointClient({
    baseUrl: BASE_URL,
    apiKey: apiKey!,
    apiSecret: apiSecret!,
  });

  console.log('\n[Step 4] Issuing 100,000 VND for customer C1...');
  const idemIssue = `demo-issue-${crypto.randomUUID()}`;
  const issueRes = await client.issue({
    customerId: 'C1',
    orderId: 'ORD-DEMO-100K',
    amountVnd: 100000,
    idempotencyKey: idemIssue,
  });
  console.log(' -> Issue result:', issueRes);

  console.log('\n[Step 5a] Retrying same Idempotency-Key with fresh nonce...');
  const idemRetry = await client.issue({
    customerId: 'C1',
    orderId: 'ORD-DEMO-100K',
    amountVnd: 100000,
    idempotencyKey: idemIssue,
  });
  console.log(' -> Idempotent response txId:', idemRetry.transactionId, 'balance:', idemRetry.balance);

  console.log('\n[Step 5b] Testing Replay Attack (reusing exact signed headers / nonce)...');
  const balanceQuery = 'customerId=C1';
  const replayHeaders = client.buildSignedHeaders('GET', '/api/merchant/v1/credits/balance', balanceQuery);
  const firstCall = await client.rawSignedFetch('GET', '/api/merchant/v1/credits/balance', {
    query: balanceQuery,
    headers: replayHeaders,
  });
  const replayCall = await client.rawSignedFetch('GET', '/api/merchant/v1/credits/balance', {
    query: balanceQuery,
    headers: replayHeaders,
  });
  console.log(' -> First call status:', firstCall.status, '| Replay call status:', replayCall.status, replayCall.data?.error);

  console.log('\n[Step 5c] Testing tampered HMAC signature...');
  const badHeaders = {
    ...client.buildSignedHeaders('GET', '/api/merchant/v1/credits/balance', balanceQuery),
    'X-OP-Signature': '0'.repeat(64),
  };
  const badSigCall = await client.rawSignedFetch('GET', '/api/merchant/v1/credits/balance', {
    query: balanceQuery,
    headers: badHeaders,
  });
  console.log(' -> Tampered signature status:', badSigCall.status, badSigCall.data?.error);

  console.log('\n[Step 6] Redeeming 30 points, then attempting 100 points...');
  const redeemOk = await client.redeem({
    customerId: 'C1',
    orderId: 'RED-DEMO-30',
    pointsToRedeem: 30,
  });
  console.log(' -> Redeem 30 ok, remainingBalance:', redeemOk.remainingBalance);

  try {
    await client.redeem({
      customerId: 'C1',
      orderId: 'RED-DEMO-100',
      pointsToRedeem: 100,
    });
  } catch (err) {
    if (err instanceof OctaPointApiError) {
      console.log(' -> Redeem 100 rejected as expected:', err.status, err.errorCode);
    } else {
      throw err;
    }
  }

  console.log('\n[Step 7 & 8] Waiting for Outbox Settlement Worker (~10s) to check settlement & Sui digest...');
  let history: any[] = [];
  for (let attempt = 0; attempt < 8; attempt++) {
    await sleep(2500);
    history = await client.history('C1');
    const allSettled = history.length > 0 && history.every((h) => h.settlementStatus === 'SETTLED');
    if (allSettled) break;
  }
  console.log(' -> C1 History:', JSON.stringify(history, null, 2));

  if (adminToken) {
    console.log('\n[Step 9] Checking Admin Dashboard & Audit Logs...');
    const adminDash = await jsonGet('/api/admin/dashboard', adminToken);
    console.log(' -> Admin Dashboard:', adminDash);
    const logs = await jsonGet('/api/admin/audit-logs', adminToken);
    console.log(` -> Recent Audit Logs count: ${logs.length}, latest action: ${logs[0]?.action}`);
  }

  console.log('\nDemo finished cleanly!');
}

main().catch((err) => {
  console.error('Demo failed:', err);
  process.exit(1);
});
