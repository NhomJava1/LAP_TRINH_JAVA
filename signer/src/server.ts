import express from 'express';
import { config } from './config.js';
import {
  anchorHash,
  batchIssue,
  isConfigured,
  issue,
  keypair,
  readOnChainBalance,
  redeem,
  SignerUnconfiguredError,
} from './sui.js';

const app = express();
app.use(express.json({ limit: '64kb' }));

app.use((request, response, next) => {
  if (request.header('X-Service-Token') !== config.serviceToken) {
    response.status(401).json({ error: 'Unauthorized' });
    return;
  }
  next();
});

app.get('/health', (_request, response) => {
  response.json({
    status: 'UP',
    configured: isConfigured,
    address: keypair ? keypair.toSuiAddress() : null,
  });
});

app.get('/internal/balance', async (_request, response) => {
  try {
    response.json(await readOnChainBalance());
  } catch (error) {
    if (error instanceof SignerUnconfiguredError) {
      response.status(503).json({ error: error.message });
      return;
    }
    console.error('Sui readOnChainBalance failed', error instanceof Error ? error.message : 'unknown error');
    response.status(502).json({ error: 'Failed to read Sui on-chain balance' });
  }
});

app.post('/internal/issue', async (request, response) => {
  const amount = request.body?.amount;
  if (!Number.isSafeInteger(amount) || amount <= 0) {
    response.status(400).json({ error: 'amount must be a positive safe integer' });
    return;
  }

  try {
    response.json(await issue(BigInt(amount)));
  } catch (error) {
    if (error instanceof SignerUnconfiguredError) {
      response.status(503).json({ error: error.message });
      return;
    }
    console.error('Sui issue failed', error instanceof Error ? error.message : 'unknown error');
    response.status(502).json({ error: 'Sui transaction failed' });
  }
});

app.post('/internal/batch-issue', async (request, response) => {
  const amounts = request.body?.amounts;
  if (!Array.isArray(amounts) || amounts.length === 0 || amounts.some((a) => !Number.isSafeInteger(a) || a <= 0)) {
    response.status(400).json({ error: 'amounts must be a non-empty array of positive safe integers' });
    return;
  }

  try {
    response.json(await batchIssue(amounts.map((a) => BigInt(a))));
  } catch (error) {
    if (error instanceof SignerUnconfiguredError) {
      response.status(503).json({ error: error.message });
      return;
    }
    console.error('Sui batch-issue failed', error instanceof Error ? error.message : 'unknown error');
    response.status(502).json({ error: 'Sui batch transaction failed' });
  }
});

app.post('/internal/redeem', async (request, response) => {
  const amount = request.body?.amount;
  if (!Number.isSafeInteger(amount) || amount <= 0) {
    response.status(400).json({ error: 'amount must be a positive safe integer' });
    return;
  }

  try {
    response.json(await redeem(BigInt(amount)));
  } catch (error) {
    if (error instanceof SignerUnconfiguredError) {
      response.status(503).json({ error: error.message });
      return;
    }
    console.error('Sui redeem failed', error instanceof Error ? error.message : 'unknown error');
    response.status(502).json({ error: 'Sui transaction failed' });
  }
});

app.post('/internal/anchor', async (request, response) => {
  const batchHash = request.body?.batchHash;
  const entryCount = Number(request.body?.entryCount ?? 0);
  if (typeof batchHash !== 'string' || !/^[0-9a-fA-F]{64}$/.test(batchHash)) {
    response.status(400).json({ error: 'batchHash must be a 64-char SHA-256 hex string' });
    return;
  }

  try {
    response.json(await anchorHash(batchHash, entryCount));
  } catch (error) {
    if (error instanceof SignerUnconfiguredError) {
      response.status(503).json({ error: error.message });
      return;
    }
    console.error('Sui anchor failed', error instanceof Error ? error.message : 'unknown error');
    response.status(502).json({ error: 'Sui anchor transaction failed' });
  }
});

app.use((error: unknown, _request: express.Request, response: express.Response, next: express.NextFunction) => {
  if (error instanceof SyntaxError && 'status' in error && error.status === 400) {
    response.status(400).json({ error: 'request body must be valid JSON' });
    return;
  }
  next(error);
});

app.listen(config.port, () => {
  console.log(`Signer listening on port ${config.port} (configured=${isConfigured})`);
});
