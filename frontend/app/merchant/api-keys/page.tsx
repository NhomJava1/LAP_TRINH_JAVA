'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface ApiKeyItem {
  id: string;
  apiKey: string;
  name: string;
  status: string;
  scopes: string;
  createdAt: string | null;
  revokedAt: string | null;
}

interface CreatedApiKey extends ApiKeyItem {
  apiSecret: string;
}

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([]);
  const [newKeyName, setNewKeyName] = useState('');
  const [createdSecret, setCreatedSecret] = useState<CreatedApiKey | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const loadKeys = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiFetch<ApiKeyItem[]>('/api/portal/api-keys');
      setKeys(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách API Key');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    apiFetch<ApiKeyItem[]>('/api/portal/api-keys')
      .then((data) => {
        if (active) setKeys(data);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải danh sách API Key');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const handleCreateKey = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const created = await apiFetch<CreatedApiKey>('/api/portal/api-keys', {
        method: 'POST',
        body: JSON.stringify({ name: newKeyName || 'Merchant Key' }),
      });
      setCreatedSecret(created);
      setNewKeyName('');
      await loadKeys();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tạo API Key');
    } finally {
      setBusy(false);
    }
  };

  const handleRotate = async (keyId: string) => {
    setBusy(true);
    setError(null);
    try {
      const rotated = await apiFetch<CreatedApiKey>(`/api/portal/api-keys/${keyId}/rotate`, {
        method: 'POST',
      });
      setCreatedSecret(rotated);
      await loadKeys();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể xoay vòng key');
    } finally {
      setBusy(false);
    }
  };

  const handleRevoke = async (keyId: string) => {
    setBusy(true);
    setError(null);
    try {
      await apiFetch(`/api/portal/api-keys/${keyId}/revoke`, { method: 'POST' });
      await loadKeys();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể thu hồi key');
    } finally {
      setBusy(false);
    }
  };

  const copyText = (value: string) => {
    navigator.clipboard.writeText(value);
  };

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Quản lý API Key</h1>
        <p className="text-sm text-slate-500 mt-1">
          Tạo, xoay vòng (rotate) và thu hồi cặp khóa dùng cho SDK / HMAC API (<code className="font-mono">X-OP-Key</code>).
        </p>
      </div>

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <form onSubmit={handleCreateKey} className="bg-white p-4 rounded-xl border border-slate-200 flex gap-3 items-center">
        <input
          type="text"
          value={newKeyName}
          onChange={(e) => setNewKeyName(e.target.value)}
          placeholder="Tên API Key (ví dụ: POS Quầy 1)"
          className="flex-1 px-3 py-2 border border-slate-300 rounded-lg text-sm"
        />
        <button
          type="submit"
          disabled={busy}
          className="bg-slate-900 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-slate-800 disabled:opacity-60"
        >
          + Tạo API Key mới
        </button>
      </form>

      {createdSecret && (
        <div className="bg-amber-50 border border-amber-300 p-5 rounded-xl space-y-3">
          <div className="flex items-start justify-between">
            <div>
              <h2 className="font-bold text-amber-900">
                ⚠️ Hãy sao chép Secret Key ngay bây giờ!
              </h2>
              <p className="text-xs text-amber-800 mt-1">
                Vì lý do bảo mật, Secret chỉ hiển thị duy nhất một lần và không thể xem lại sau khi đóng.
              </p>
            </div>
            <button
              onClick={() => setCreatedSecret(null)}
              className="text-xs px-2.5 py-1 rounded bg-amber-200 text-amber-900 font-medium"
            >
              Đóng
            </button>
          </div>
          <div className="grid grid-cols-1 gap-2 text-sm">
            <div className="flex gap-2 items-center">
              <span className="w-24 font-medium text-amber-900">API Key:</span>
              <input
                type="text"
                readOnly
                value={createdSecret.apiKey}
                className="flex-1 px-3 py-1.5 border rounded bg-white font-mono text-xs"
              />
              <button
                type="button"
                onClick={() => copyText(createdSecret.apiKey)}
                className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-medium"
              >
                Copy Key
              </button>
            </div>
            <div className="flex gap-2 items-center">
              <span className="w-24 font-medium text-amber-900">API Secret:</span>
              <input
                type="text"
                readOnly
                value={createdSecret.apiSecret}
                className="flex-1 px-3 py-1.5 border rounded bg-white font-mono text-xs"
              />
              <button
                type="button"
                onClick={() => copyText(createdSecret.apiSecret)}
                className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-medium"
              >
                Copy Secret
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 border-b border-slate-200">
            <tr>
              <th className="p-4 font-semibold text-slate-600">Tên</th>
              <th className="p-4 font-semibold text-slate-600">Key ID (X-OP-Key)</th>
              <th className="p-4 font-semibold text-slate-600">Quyền</th>
              <th className="p-4 font-semibold text-slate-600">Trạng thái</th>
              <th className="p-4 font-semibold text-slate-600">Ngày tạo</th>
              <th className="p-4 font-semibold text-slate-600 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="p-6 text-center text-slate-500">
                  Đang tải...
                </td>
              </tr>
            ) : keys.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-6 text-center text-slate-500">
                  Chưa có API Key nào.
                </td>
              </tr>
            ) : (
              keys.map((k) => (
                <tr key={k.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="p-4 font-medium text-slate-900">{k.name}</td>
                  <td className="p-4 font-mono text-xs text-slate-700">{k.apiKey}</td>
                  <td className="p-4 text-xs text-slate-600">{k.scopes}</td>
                  <td className="p-4">
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-semibold ${
                        k.status === 'ACTIVE'
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {k.status}
                    </span>
                  </td>
                  <td className="p-4 text-xs text-slate-500">
                    {k.createdAt ? new Date(k.createdAt).toLocaleString('vi-VN') : '-'}
                  </td>
                  <td className="p-4 text-right space-x-2">
                    {k.status === 'ACTIVE' && (
                      <>
                        <button
                          onClick={() => handleRotate(k.id)}
                          disabled={busy}
                          className="text-blue-600 hover:underline text-xs font-medium"
                        >
                          Xoay vòng
                        </button>
                        <button
                          onClick={() => handleRevoke(k.id)}
                          disabled={busy}
                          className="text-red-600 hover:underline text-xs font-medium"
                        >
                          Thu hồi
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
