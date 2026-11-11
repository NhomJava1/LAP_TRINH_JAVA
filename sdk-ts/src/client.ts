import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';
import { SdkError } from './errors';

export class OctaPointClient {
  constructor(private baseURL: string, private apiKey: string, private apiSecret: string) {}

  // 1. Phat hanh credit
  async issue(data: { tenantId: string; amount: number; idempotencyKey?: string }) {
    const idempotencyKey = data.idempotencyKey || uuidv4();
    return this.request('POST', '/api/v1/credit/issue', { ...data, idempotencyKey });
  }

  // 2. Tieu thu / doi credit
  async redeem(data: { tenantId: string; amount: number; idempotencyKey?: string }) {
    const idempotencyKey = data.idempotencyKey || uuidv4();
    return this.request('POST', '/api/v1/credit/redeem', { ...data, idempotencyKey });
  }

  // 3. Truy van so du
  async balance(tenantId: string) {
    return this.request('GET', `/api/v1/credit/balance?tenantId=${tenantId}`);
  }

  // 4. Lay lich su giao dich
  async history(tenantId: string) {
    return this.request('GET', `/api/v1/credit/history?tenantId=${tenantId}`);
  }

  private async request(method: string, endpoint: string, data?: any) {
    try {
      const response = await axios({
        method,
        url: `${this.baseURL}${endpoint}`,
        data,
        headers: { 'Content-Type': 'application/json' },
      });
      return response.data;
    } catch (error: any) {
      if (error.response && error.response.data) {
        const problem = error.response.data;
        throw new SdkError(error.response.status, problem.title || 'Error', problem.detail);
      }
      throw error;
    }
  }
}