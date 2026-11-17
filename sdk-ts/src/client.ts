import * as crypto from 'node:crypto';
import { OctaPointApiError } from './errors.js';
import { SignedHeaders, signRequest } from './hmac.js';

export interface OctaPointClientConfig {
  baseUrl: string;
  apiKey: string;
  apiSecret: string;
}

export interface IssueParams {
  customerId: string;
  orderId?: string;
  amountVnd: number;
  idempotencyKey?: string;
}

export interface RedeemParams {
  customerId: string;
  orderId?: string;
  pointsToRedeem: number;
  idempotencyKey?: string;
}

export class OctaPointClient {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly apiSecret: string;

  constructor(config: OctaPointClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '');
    this.apiKey = config.apiKey;
    this.apiSecret = config.apiSecret;
  }

  async issue(params: IssueParams) {
    const idempotencyKey = params.idempotencyKey ?? crypto.randomUUID();
    const body = JSON.stringify({
      customerId: params.customerId,
      orderId: params.orderId,
      amountVnd: params.amountVnd,
    });
    return this.request('POST', '/api/merchant/v1/credits/issue', undefined, body, {
      'Idempotency-Key': idempotencyKey,
    });
  }

  async redeem(params: RedeemParams) {
    const idempotencyKey = params.idempotencyKey ?? crypto.randomUUID();
    const body = JSON.stringify({
      customerId: params.customerId,
      orderId: params.orderId,
      pointsToRedeem: params.pointsToRedeem,
    });
    return this.request('POST', '/api/merchant/v1/credits/redeem', undefined, body, {
      'Idempotency-Key': idempotencyKey,
    });
  }

  async balance(customerId: string) {
    const query = `customerId=${encodeURIComponent(customerId)}`;
    return this.request('GET', '/api/merchant/v1/credits/balance', query);
  }

  async history(customerId?: string) {
    const query = customerId ? `customerId=${encodeURIComponent(customerId)}` : undefined;
    return this.request('GET', '/api/merchant/v1/credits/history', query);
  }

  buildSignedHeaders(
    method: string,
    path: string,
    query?: string,
    body?: string,
    overrides?: { timestamp?: string; nonce?: string },
  ): SignedHeaders {
    return signRequest({
      apiKey: this.apiKey,
      apiSecret: this.apiSecret,
      method,
      path,
      query,
      body,
      timestamp: overrides?.timestamp,
      nonce: overrides?.nonce,
    });
  }

  async rawSignedFetch(
    method: string,
    path: string,
    options: {
      query?: string;
      body?: string;
      headers: Record<string, string>;
    },
  ): Promise<{ status: number; data: any }> {
    const url = `${this.baseUrl}${path}${options.query ? `?${options.query}` : ''}`;
    const res = await fetch(url, {
      method,
      headers: {
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...options.headers,
      },
      body: options.body,
    });
    const text = await res.text();
    let data: any = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = text;
    }
    return { status: res.status, data };
  }

  private async request(
    method: string,
    path: string,
    query?: string,
    body?: string,
    extraHeaders?: Record<string, string>,
  ) {
    const signed = this.buildSignedHeaders(method, path, query, body);
    const { status, data } = await this.rawSignedFetch(method, path, {
      query,
      body,
      headers: {
        ...signed,
        ...(extraHeaders ?? {}),
      },
    });

    if (status < 200 || status >= 300) {
      throw new OctaPointApiError(
        status,
        data?.error ?? 'API_ERROR',
        data?.message ?? `Request failed with status ${status}`,
        data,
      );
    }
    return data;
  }
}
