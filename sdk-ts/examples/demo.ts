import { OctaPointClient } from '../src/client';

async function runDemo() {
  const client = new OctaPointClient('http://localhost:8080', 'test-key', 'test-secret');
  try {
    const res = await client.balance('tenant-123');
    console.log('Result:', res);
  } catch (err: any) {
    console.error('Caught error (Expected for 401/403/409/422/429):', err.message);
  }
}

runDemo();