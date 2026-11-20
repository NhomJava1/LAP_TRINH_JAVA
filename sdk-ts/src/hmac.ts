import * as crypto from 'node:crypto';

export interface SignedHeaders {
  'X-OP-Key': string;
  'X-OP-Timestamp': string;
  'X-OP-Nonce': string;
  'X-OP-Signature': string;
}

export interface SignRequestInput {
  apiKey: string;
  apiSecret: string;
  method: string;
  path: string;
  query?: string | null;
  body?: string | Buffer | Uint8Array | null;
  timestamp?: string;
  nonce?: string;
}

export function sha256Hex(body?: string | Buffer | Uint8Array | null): string {
  const hash = crypto.createHash('sha256');
  if (body !== undefined && body !== null) {
    hash.update(typeof body === 'string' ? Buffer.from(body, 'utf8') : body);
  }
  return hash.digest('hex');
}

function rfc3986Encode(value: string): string {
  return encodeURIComponent(value).replace(/[!'()*]/g, (c) =>
    '%' + c.charCodeAt(0).toString(16).toUpperCase(),
  );
}

export function canonicalizeQuery(rawQuery?: string | null): string {
  if (!rawQuery || rawQuery.trim() === '') {
    return '';
  }
  const normalized = rawQuery.startsWith('?') ? rawQuery.slice(1) : rawQuery;
  const pairs: Array<[string, string]> = [];

  for (const part of normalized.split('&')) {
    if (!part) continue;
    const eq = part.indexOf('=');
    const rawKey = eq >= 0 ? part.slice(0, eq) : part;
    const rawVal = eq >= 0 ? part.slice(eq + 1) : '';
    const encKey = rfc3986Encode(decodeURIComponent(rawKey.replace(/\+/g, ' ')));
    const encVal = rfc3986Encode(decodeURIComponent(rawVal.replace(/\+/g, ' ')));
    pairs.push([encKey, encVal]);
  }

  pairs.sort((a, b) => {
    if (a[0] !== b[0]) return a[0] < b[0] ? -1 : 1;
    if (a[1] !== b[1]) return a[1] < b[1] ? -1 : 1;
    return 0;
  });

  return pairs.map(([k, v]) => `${k}=${v}`).join('&');
}

export function buildStringToSign(
  method: string,
  path: string,
  query: string | null | undefined,
  timestamp: string,
  nonce: string,
  body?: string | Buffer | Uint8Array | null,
): string {
  return [
    method.toUpperCase(),
    path,
    canonicalizeQuery(query),
    timestamp,
    nonce,
    sha256Hex(body),
  ].join('\n');
}

export function hmacSha256Hex(secret: string, stringToSign: string): string {
  return crypto
    .createHmac('sha256', Buffer.from(secret, 'utf8'))
    .update(Buffer.from(stringToSign, 'utf8'))
    .digest('hex');
}

export function signRequest(input: SignRequestInput): SignedHeaders {
  const timestamp = input.timestamp ?? String(Math.floor(Date.now() / 1000));
  const nonce = input.nonce ?? crypto.randomUUID();
  const stringToSign = buildStringToSign(
    input.method,
    input.path,
    input.query,
    timestamp,
    nonce,
    input.body,
  );
  const signature = hmacSha256Hex(input.apiSecret, stringToSign);

  return {
    'X-OP-Key': input.apiKey,
    'X-OP-Timestamp': timestamp,
    'X-OP-Nonce': nonce,
    'X-OP-Signature': signature,
  };
}

export interface VerifyWebhookSignatureInput {
  webhookSecret: string;
  timestamp: string;
  rawBody: string | Buffer;
  signature: string;
  toleranceSeconds?: number;
  nowSeconds?: number;
}

export function verifyWebhookSignature(input: VerifyWebhookSignatureInput): boolean {
  const ts = Number(input.timestamp);
  if (!Number.isFinite(ts)) {
    return false;
  }
  const now = input.nowSeconds ?? Math.floor(Date.now() / 1000);
  const tolerance = input.toleranceSeconds ?? 300;
  if (Math.abs(now - ts) > tolerance) {
    return false;
  }
  const bodyStr = typeof input.rawBody === 'string' ? input.rawBody : input.rawBody.toString('utf8');
  const expected = hmacSha256Hex(input.webhookSecret, `${input.timestamp}.${bodyStr}`);
  const actualBuf = Buffer.from(input.signature, 'utf8');
  const expectedBuf = Buffer.from(expected, 'utf8');
  if (actualBuf.length !== expectedBuf.length) {
    return false;
  }
  return crypto.timingSafeEqual(actualBuf, expectedBuf);
}
