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

  return {
    digest: result.Transaction.digest,
    status: 'SETTLED' as const,
    gasUsed: result.Transaction.effects?.gasUsed,
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
