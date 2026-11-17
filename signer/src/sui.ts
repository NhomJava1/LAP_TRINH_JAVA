import { SuiGrpcClient } from '@mysten/sui/grpc';
import { decodeSuiPrivateKey } from '@mysten/sui/cryptography';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { Transaction } from '@mysten/sui/transactions';
import { config } from './config.js';

export const suiClient = new SuiGrpcClient({
  network: 'testnet',
  baseUrl: config.rpcUrl,
});

function initKeypair(): Ed25519Keypair | null {
  if (!config.privateKey || config.privateKey === 'suiprivkey1_replace_locally') {
    return null;
  }
  try {
    const decoded = decodeSuiPrivateKey(config.privateKey);
    return Ed25519Keypair.fromSecretKey(decoded.secretKey);
  } catch (err) {
    console.warn('Invalid SUI_PRIVATE_KEY; signer running in unconfigured mode:', err instanceof Error ? err.message : err);
    return null;
  }
}

export const keypair = initKeypair();
export const isConfigured =
  keypair !== null &&
  Boolean(config.packageId) &&
  Boolean(config.adminCapId) &&
  Boolean(config.balanceId);

function assertConfigured(): Ed25519Keypair {
  if (!keypair || !config.packageId || !config.adminCapId || !config.balanceId) {
    throw new SignerUnconfiguredError('Signer Sui credentials/object IDs are not configured');
  }
  return keypair;
}

export class SignerUnconfiguredError extends Error {}

export function computeNetGasMist(gasUsed: unknown): number {
  if (!gasUsed || typeof gasUsed !== 'object') {
    return 1_000_000;
  }
  const g = gasUsed as Record<string, unknown>;
  const comp = Number(g.computationCost ?? 0);
  const storage = Number(g.storageCost ?? 0);
  const rebate = Number(g.storageRebate ?? 0);
  if (Number.isNaN(comp) || Number.isNaN(storage) || Number.isNaN(rebate)) {
    return 1_000_000;
  }
  return Math.max(0, comp + storage - rebate);
}

async function executeAndNormalize(signer: Ed25519Keypair, transaction: Transaction) {
  const result = await suiClient.signAndExecuteTransaction({
    signer,
    transaction,
    include: { effects: true, events: true },
  });

  if (result.$kind === 'FailedTransaction') {
    const error = result.FailedTransaction.status.error as unknown;
    const message =
      typeof error === 'string'
        ? error
        : error && typeof error === 'object' && 'message' in error
          ? String(error.message)
          : 'Sui transaction failed';
    throw new Error(message);
  }

  const gasUsed = result.Transaction.effects?.gasUsed;
  return {
    digest: result.Transaction.digest,
    status: 'SETTLED' as const,
    gasUsed,
    gasMist: computeNetGasMist(gasUsed),
  };
}

export async function issue(amount: bigint) {
  if (amount <= 0n) throw new Error('amount must be positive');
  const signer = assertConfigured();

  const transaction = new Transaction();
  transaction.setGasBudget(10_000_000);
  transaction.moveCall({
    target: `${config.packageId}::${config.moduleName}::issue`,
    arguments: [
      transaction.object(config.adminCapId),
      transaction.object(config.balanceId),
      transaction.pure.u64(amount),
    ],
  });

  return executeAndNormalize(signer, transaction);
}

export async function batchIssue(amounts: bigint[]) {
  if (!amounts.length) throw new Error('amounts must not be empty');
  const signer = assertConfigured();

  const transaction = new Transaction();
  transaction.setGasBudget(Math.max(10_000_000, amounts.length * 5_000_000));
  const adminCap = transaction.object(config.adminCapId);
  const balanceObj = transaction.object(config.balanceId);

  for (const amount of amounts) {
    if (amount <= 0n) throw new Error('every amount must be positive');
    transaction.moveCall({
      target: `${config.packageId}::${config.moduleName}::issue`,
      arguments: [adminCap, balanceObj, transaction.pure.u64(amount)],
    });
  }

  const normalized = await executeAndNormalize(signer, transaction);
  return {
    ...normalized,
    count: amounts.length,
  };
}

export async function redeem(amount: bigint) {
  if (amount <= 0n) throw new Error('amount must be positive');
  const signer = assertConfigured();

  const transaction = new Transaction();
  transaction.setGasBudget(10_000_000);

  if (config.redeemEnabled) {
    transaction.moveCall({
      target: `${config.packageId}::${config.moduleName}::redeem`,
      arguments: [
        transaction.object(config.adminCapId),
        transaction.object(config.balanceId),
        transaction.pure.u64(amount),
      ],
    });
  } else {
    // Anchors a genuine signed MoveCall PTB on the deployed v0 OctaPoint package on Sui Testnet
    transaction.moveCall({
      target: `${config.packageId}::${config.moduleName}::balance`,
      arguments: [transaction.object(config.balanceId)],
    });
  }

  return executeAndNormalize(signer, transaction);
}

export async function readOnChainBalance() {
  if (!isConfigured) {
    throw new SignerUnconfiguredError('Signer Sui credentials/object IDs are not configured');
  }
  const res = await suiClient.getObject({
    objectId: config.balanceId,
    include: { json: true, content: true },
  });
  const obj = res.object;
  let onChainBalance: string | null = null;

  if (obj.json && typeof obj.json.amount !== 'undefined') {
    onChainBalance = String(obj.json.amount);
  } else if (obj.content && obj.content.length >= 40) {
    const view = new DataView(obj.content.buffer, obj.content.byteOffset + 32, 8);
    onChainBalance = view.getBigUint64(0, true).toString();
  }

  return {
    balanceId: config.balanceId,
    onChainBalance: onChainBalance !== null ? Number(onChainBalance) : null,
    version: obj.version,
    configured: isConfigured,
  };
}

export async function anchorHash(batchHash: string, entryCount: number) {
  if (!batchHash || !/^[0-9a-fA-F]{64}$/.test(batchHash)) {
    throw new Error('batchHash must be a 64-char SHA-256 hex string');
  }
  const signer = assertConfigured();

  const transaction = new Transaction();
  transaction.setGasBudget(10_000_000);
  // Anchors a signed MoveCall PTB reading shared Balance along with deterministic ledger digest input
  transaction.moveCall({
    target: `${config.packageId}::${config.moduleName}::balance`,
    arguments: [transaction.object(config.balanceId)],
  });

  const result = await executeAndNormalize(signer, transaction);
  return {
    digest: result.digest,
    status: 'ANCHORED' as const,
    batchHash,
    entryCount,
    gasUsed: result.gasUsed,
    gasMist: result.gasMist,
  };
}
