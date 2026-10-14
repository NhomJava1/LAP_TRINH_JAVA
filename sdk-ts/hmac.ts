import * as crypto from 'crypto';

/**
 * Ham tinh toan chu ky HMAC
 * @param data Du lieu can ky (chuoi)
 * @param secretKey Khoa bi mat (secret key)
 * @returns Chuoi chu ky dang hex
 */
export function signHmac(data: string, secretKey: string): string {
    return crypto
        .createHmac('sha256', secretKey)
        .update(data)
        .digest('hex');
}
const dataToSign = "GET/api/v1/tenants1718372400";
const secret = "my_super_secret_key";
const signature = signHmac(dataToSign, secret);

console.log("Signature:", signature);