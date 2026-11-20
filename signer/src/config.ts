import 'dotenv/config';

export const config = {
  port: Number(process.env.PORT ?? 3001),
  rpcUrl: process.env.SUI_RPC_URL ?? 'https://fullnode.testnet.sui.io:443',
  privateKey: process.env.SUI_PRIVATE_KEY ?? '',
  packageId: process.env.SUI_PACKAGE_ID ?? '',
  moduleName: process.env.SUI_MODULE_NAME ?? 'octapoint',
  adminCapId: process.env.SUI_ADMIN_CAP_ID ?? '',
  balanceId: process.env.SUI_BALANCE_ID ?? '',
  redeemEnabled: process.env.SUI_REDEEM_ENABLED === 'true',
  serviceToken: process.env.SERVICE_TOKEN ?? 'replace-with-random-internal-token',
};
