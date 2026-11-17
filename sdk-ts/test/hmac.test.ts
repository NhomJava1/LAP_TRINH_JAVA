import test from 'node:test';
import assert from 'node:assert/strict';
import { buildStringToSign, canonicalizeQuery, hmacSha256Hex, sha256Hex, signRequest } from '../src/hmac.js';

test('canonicalizeQuery sorts keys and encodes spaces as %20', () => {
  assert.equal(canonicalizeQuery(''), '');
  assert.equal(canonicalizeQuery(undefined), '');
  assert.equal(
    canonicalizeQuery('b=hello+world&a=2&tilde=~&a=1'),
    'a=1&a=2&b=hello%20world&tilde=~',
  );
});

test('produces identical test vector string-to-sign and signature as Java HmacCodec', () => {
  const secret = 'sec_shared_test_vector_2026';
  const body = '{"customerId":"C1","amountVnd":100000}';
  const expectedBodyHash = sha256Hex(body);
  const expectedStringToSign = [
    'POST',
    '/api/merchant/v1/credits/issue',
    'a=hello%20world&z=2',
    '1700000000',
    'nonce-vector-001',
    expectedBodyHash,
  ].join('\n');

  const actualStringToSign = buildStringToSign(
    'post',
    '/api/merchant/v1/credits/issue',
    'z=2&a=hello%20world',
    '1700000000',
    'nonce-vector-001',
    body,
  );
  assert.equal(actualStringToSign, expectedStringToSign);

  const headers = signRequest({
    apiKey: 'ak_test',
    apiSecret: secret,
    method: 'post',
    path: '/api/merchant/v1/credits/issue',
    query: 'z=2&a=hello%20world',
    body,
    timestamp: '1700000000',
    nonce: 'nonce-vector-001',
  });

  assert.equal(headers['X-OP-Key'], 'ak_test');
  assert.equal(
    headers['X-OP-Signature'],
    '987d1a5ef200d35e8d2d849a7bdc15b1450d0f3c4bf284561a08c8e619d40e1d',
  );
  assert.equal(headers['X-OP-Signature'], hmacSha256Hex(secret, expectedStringToSign));
});
