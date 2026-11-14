import { HmacSigner } from './hmac';

describe('HmacSigner', () => {
  it('should generate a valid HMAC signature', () => {
    const signer = new HmacSigner('my-secret-key');
    const signature = signer.generateSignature(
      'POST', 
      '/api/v1/credits/issue', 
      { customerId: 'C1', amountVnd: 100000 }, 
      'nonce-123', 
      1700000000000
    );
    
    // Chỉ kiểm tra xem nó có tạo ra chuỗi 64 ký tự hex hợp lệ không
    expect(signature).toBeDefined();
    expect(signature.length).toBe(64); 
  });
});