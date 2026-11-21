import { useAuthStore } from '@/store/autoStore';

export const API_BASE = (
  process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'
).replace(/\/+$/, '');

export async function apiFetch<T = unknown>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const token = useAuthStore.getState().token;
  const headers = new Headers(init.headers);

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  });

  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }

  if (!response.ok) {
    const errObj =
      data && typeof data === 'object' ? (data as Record<string, unknown>) : null;
    const message =
      (errObj && typeof errObj.message === 'string' && errObj.message) ||
      (typeof data === 'string' && data) ||
      `Request failed with status ${response.status}`;
    throw new Error(message);
  }

  return data as T;
}
