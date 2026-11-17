import * as crypto from 'crypto';

export class HmacSigner {
  private apiSecret: string;

  constructor(apiSecret: string) {
    this.apiSecret = apiSecret;
  }

  /**
   * Tạo chữ ký HMAC-SHA256 theo spec.
   * @param method HTTP Method (GET, POST...)
   * @param path Đường dẫn API (/api/v1/...)
   * @param body Body của request (dạng string hoặc object)
   * @param nonce Chuỗi ngẫu nhiên (UUID)
   * @param timestamp Thời gian hiện tại (mili-giây)
   * @returns Chữ ký dạng hex string
   */
  generateSignature(
    method: string, 
    path: string, 
    body: any = null, 
    nonce: string, 
    timestamp: number
  ): string {
    // 1. Tính hash của body (nếu có)
    let bodyHash = '';
    if (body) {
      const bodyString = typeof body === 'string' ? body : JSON.stringify(body);
      bodyHash = crypto.createHash('sha256').update(bodyString).digest('hex');
    }

    // 2. Tạo payload chuẩn hóa: METHOD + PATH + TIMESTAMP + NONCE + BODY_HASH
    const payload = `${method.toUpperCase()}${path}${timestamp}${nonce}${bodyHash}`;

    // 3. Tạo chữ ký HMAC-SHA256
    const signature = crypto
      .createHmac('sha256', this.apiSecret)
      .update(payload)
      .digest('hex');

    return signature;
  }
}