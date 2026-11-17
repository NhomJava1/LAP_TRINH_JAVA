import express from 'express';
import { config } from './config.js';
import { isConfigured, issue, keypair, redeem, SignerUnconfiguredError } from './sui.js';

const app = express();
app.use(express.json({ limit: '32kb' }));

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
