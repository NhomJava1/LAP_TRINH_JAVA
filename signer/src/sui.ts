import { SuiGrpcClient } from '@mysten/sui/grpc';
import { decodeSuiPrivateKey } from '@mysten/sui/cryptography';
import { Ed25519Keypair } from '@mysten/sui/keypairs/ed25519';
import { Transaction } from '@mysten/sui/transactions';
import { config } from './config.js';

export const suiClient = new SuiGrpcClient({
  network: 'testnet',
  baseUrl: config.rpcUrl,
});
const decoded = decodeSuiPrivateKey(config.privateKey);
export const keypair = Ed25519Keypair.fromSecretKey(decoded.secretKey);

export async function issue(amount: bigint) {
  if (amount <= 0n) throw new Error('amount must be positive');

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

  const result = await suiClient.signAndExecuteTransaction({
    signer: keypair,
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
