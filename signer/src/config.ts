import 'dotenv/config';

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

export const config = {
  port: Number(process.env.PORT ?? 3001),
  rpcUrl: process.env.SUI_RPC_URL ?? 'https://fullnode.testnet.sui.io:443',
  privateKey: requireEnv('SUI_PRIVATE_KEY'),
  packageId: requireEnv('SUI_PACKAGE_ID'),
  moduleName: process.env.SUI_MODULE_NAME ?? 'octapoint',
  adminCapId: requireEnv('SUI_ADMIN_CAP_ID'),
  balanceId: requireEnv('SUI_BALANCE_ID'),
  serviceToken: requireEnv('SERVICE_TOKEN'),
};
