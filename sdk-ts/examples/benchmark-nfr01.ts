import { performance } from 'node:perf_hooks';
import { OctaPointClient } from '../src/index.js';

const BASE_URL = (process.env.OCTAPOINT_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const ADMIN_EMAIL = process.env.OCTAPOINT_ADMIN_EMAIL ?? 'admin@octapoint.io';
const ADMIN_PASSWORD = process.env.OCTAPOINT_ADMIN_PASSWORD ?? 'admin123';

const VUS = 20;
const CUSTOMERS_PER_VU = 10;
const REQUESTS_PER_VU = 50; // 20 VUs * 50 = 1,000 requests per endpoint
const API_KEY_COUNT = 40; // 40 * 120 req/min = 4,800 req/min rate limit headroom

interface EndpointStats {
  endpoint: string;
  count: number;
  ok200: number;
  errors: number;
  durationMs: number;
  throughputRps: number;
  minMs: number;
  avgMs: number;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
  maxMs: number;
}

function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0;
  const idx = Math.ceil((p / 100) * sorted.length) - 1;
  return Number(sorted[Math.max(0, Math.min(sorted.length - 1, idx))].toFixed(2));
}

function summarize(endpoint: string, latencies: number[], ok200: number, errors: number, durationMs: number): EndpointStats {
  const sorted = [...latencies].sort((a, b) => a - b);
  const sum = sorted.reduce((acc, v) => acc + v, 0);
  const count = latencies.length;
  return {
    endpoint,
    count,
    ok200,
    errors,
    durationMs: Number(durationMs.toFixed(2)),
    throughputRps: Number(((count / durationMs) * 1000).toFixed(2)),
    minMs: Number((sorted[0] ?? 0).toFixed(2)),
    avgMs: Number((count > 0 ? sum / count : 0).toFixed(2)),
    p50Ms: percentile(sorted, 50),
    p95Ms: percentile(sorted, 95),
    p99Ms: percentile(sorted, 99),
    maxMs: Number((sorted[sorted.length - 1] ?? 0).toFixed(2)),
  };
}

async function jsonReq<T = any>(method: string, path: string, body?: unknown, token?: string): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} on ${method} ${path}: ${text}`);
  }
  return data as T;
}

async function runConcurrentLoad(
  label: string,
  clients: OctaPointClient[],
  opFn: (client: OctaPointClient, vu: number, iter: number) => Promise<void>,
): Promise<EndpointStats> {
  const latencies: number[] = [];
  let ok200 = 0;
  let errors = 0;
  const errorSamples: string[] = [];

  const tStart = performance.now();
  await Promise.all(
    Array.from({ length: VUS }, async (_, vu) => {
      for (let iter = 0; iter < REQUESTS_PER_VU; iter++) {
        const keyIdx = (vu * REQUESTS_PER_VU + iter) % clients.length;
        const client = clients[keyIdx];
        const reqStart = performance.now();
        try {
          await opFn(client, vu, iter);
          ok200++;
        } catch (err) {
          errors++;
          if (errorSamples.length < 5) {
            errorSamples.push(err instanceof Error ? err.message : String(err));
          }
        } finally {
          latencies.push(performance.now() - reqStart);
        }
      }
    }),
  );
  const totalMs = performance.now() - tStart;
  if (errorSamples.length > 0) {
    console.warn(`[WARN] Sample errors in ${label}:`, errorSamples);
  }
  return summarize(label, latencies, ok200, errors, totalMs);
}

async function main() {
  const runId = Date.now().toString();
  const email = `bench-${runId}@octapoint.io`;
  console.log(`=== OctaPoint NFR-01 Load Benchmark (${BASE_URL}) ===`);
  console.log(`Config: ${VUS} concurrent VUs x ${REQUESTS_PER_VU} requests = ${VUS * REQUESTS_PER_VU} req/endpoint`);

  // 1. Setup dedicated benchmark tenant with gasQuotaMist=0 so Outbox never spends Testnet SUI
  const reg = await jsonReq<{ tenantId: string }>('POST', '/api/portal/auth/register', {
    businessName: `Benchmark Store ${runId}`,
    name: 'Benchmark Runner',
    email,
    phone: '0900000001',
    password: 'BenchPassword123!',
  });
  const adminLogin = await jsonReq<{ token: string }>('POST', '/api/portal/auth/login', {
    email: ADMIN_EMAIL,
    password: ADMIN_PASSWORD,
  });
  await jsonReq('PATCH', `/api/admin/tenants/${reg.tenantId}/status`, { status: 'APPROVED' }, adminLogin.token);
  await jsonReq('PATCH', `/api/admin/tenants/${reg.tenantId}/gas-quota`, { gasQuotaMist: 0 }, adminLogin.token);

  const merchantLogin = await jsonReq<{ token: string }>('POST', '/api/portal/auth/login', {
    email,
    password: 'BenchPassword123!',
  });

  // 2. Create API key pool under single merchant tenant
  const clients: OctaPointClient[] = [];
  for (let i = 0; i < API_KEY_COUNT; i++) {
    const k = await jsonReq<{ apiKey: string; apiSecret: string }>(
      'POST',
      '/api/portal/api-keys',
      { name: `Bench Key ${i + 1}`, scopes: 'ISSUE,REDEEM,READ' },
      merchantLogin.token,
    );
    clients.push(
      new OctaPointClient({
        baseUrl: BASE_URL,
        apiKey: k.apiKey,
        apiSecret: k.apiSecret,
      }),
    );
  }
  console.log(`Created ${clients.length} HMAC API keys for tenant ${reg.tenantId} (gasQuotaMist=0).`);

  // 3. Unmeasured Warm-Up Phase: initialize 200 customers (50 pts each) + warm JVM JIT & pools
  const warmStart = performance.now();
  await Promise.all(
    Array.from({ length: VUS }, async (_, vu) => {
      for (let slot = 0; slot < CUSTOMERS_PER_VU; slot++) {
        const client = clients[(vu * CUSTOMERS_PER_VU + slot) % clients.length];
        const customerId = `BENCH_C_${vu}_${slot}`;
        await client.issue({
          customerId,
          orderId: `WARM_ORD_${runId}_${vu}_${slot}`,
          amountVnd: 50000,
          idempotencyKey: `warm-iss-${runId}-${vu}-${slot}`,
        });
        await client.balance(customerId);
      }
      // Extra warm redeem so /redeem JIT & Hibernate statements are pre-compiled
      const c0 = `BENCH_C_${vu}_0`;
      await clients[vu % clients.length].redeem({
        customerId: c0,
        orderId: `WARM_RED_${runId}_${vu}`,
        pointsToRedeem: 5,
        idempotencyKey: `warm-red-${runId}-${vu}`,
      });
    }),
  );
  console.log(`Warm-up complete (${VUS * CUSTOMERS_PER_VU * 2 + VUS} requests) in ${(performance.now() - warmStart).toFixed(0)} ms.`);

  // 4. Measured Benchmarks (1,000 requests per endpoint at 20 VUs concurrency)
  const issueStats = await runConcurrentLoad('POST /api/merchant/v1/credits/issue', clients, async (client, vu, iter) => {
    const slot = iter % CUSTOMERS_PER_VU;
    await client.issue({
      customerId: `BENCH_C_${vu}_${slot}`,
      orderId: `B_ISS_${runId}_${vu}_${iter}`,
      amountVnd: 20000,
      idempotencyKey: `iss-${runId}-${vu}-${iter}`,
    });
  });

  const redeemStats = await runConcurrentLoad('POST /api/merchant/v1/credits/redeem', clients, async (client, vu, iter) => {
    const slot = iter % CUSTOMERS_PER_VU;
    await client.redeem({
      customerId: `BENCH_C_${vu}_${slot}`,
      orderId: `B_RED_${runId}_${vu}_${iter}`,
      pointsToRedeem: 5,
      idempotencyKey: `red-${runId}-${vu}-${iter}`,
    });
  });

  const balanceStats = await runConcurrentLoad('GET /api/merchant/v1/credits/balance', clients, async (client, vu, iter) => {
    const slot = iter % CUSTOMERS_PER_VU;
    await client.balance(`BENCH_C_${vu}_${slot}`);
  });

  const results = [issueStats, redeemStats, balanceStats];
  console.log('\n=== Benchmark Results (NFR-01 Target: p95 < 100 ms) ===');
  console.table(results);
  console.log('JSON_RESULTS:', JSON.stringify(results));

  const failed = results.some((r) => r.errors > 0 || r.p95Ms >= 100);
  if (failed) {
    console.warn('NOTICE: Some endpoint had errors or p95 >= 100ms.');
    process.exitCode = 1;
  } else {
    console.log('SUCCESS: All 3 endpoints passed 1,000 reqs with 0 errors and p95 < 100ms!');
  }
}

main().catch((err) => {
  console.error('Benchmark failed:', err);
  process.exit(1);
});
