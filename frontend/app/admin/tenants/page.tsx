'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface TenantItem {
  id: string;
  name: string;
  email: string | null;
  phone: string | null;
  status: string;
  gasQuotaMist: number | null;
  gasUsedMist: number | null;
  subscriptionPlan: string | null;
  createdAt: string | null;
}

export default function AdminTenantManagementPage() {
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [quotaInputs, setQuotaInputs] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const loadTenants = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiFetch<TenantItem[]>('/api/admin/tenants');
      setTenants(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải danh sách tenant');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    apiFetch<TenantItem[]>('/api/admin/tenants')
      .then((data) => {
        if (active) setTenants(data);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải danh sách tenant');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const updateStatus = async (id: string, status: 'APPROVED' | 'REJECTED' | 'SUSPENDED') => {
    setBusyId(id);
    setError(null);
    try {
      await apiFetch(`/api/admin/tenants/${id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status }),
      });
      await loadTenants();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cập nhật trạng thái thất bại');
    } finally {
      setBusyId(null);
    }
  };

  const updateGasQuota = async (id: string, fallbackQuota: number) => {
    const raw = quotaInputs[id];
    const parsed = raw ? Number(raw) : fallbackQuota;
    if (!Number.isFinite(parsed) || parsed < 0) {
      setError('Gas quota phải >= 0');
      return;
    }
    setBusyId(id);
    setError(null);
    try {
      await apiFetch(`/api/admin/tenants/${id}/gas-quota`, {
        method: 'PATCH',
        body: JSON.stringify({ gasQuotaMist: parsed }),
      });
      await loadTenants();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cập nhật gas quota thất bại');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Quản lý Tenant (Merchant)</h1>
          <p className="text-sm text-slate-500 mt-1">
            Duyệt đăng ký mới, từ chối hoặc tạm khóa Tenant vi phạm.
          </p>
        </div>
        <button
          onClick={loadTenants}
          className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-sm hover:bg-slate-50"
        >
          Làm mới
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
          {error}
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 border-b border-slate-200 text-slate-600">
            <tr>
              <th className="p-4 font-semibold">Mã Tenant</th>
              <th className="p-4 font-semibold">Tên Doanh nghiệp</th>
              <th className="p-4 font-semibold">Email / Gói</th>
              <th className="p-4 font-semibold">Gas Quota (MIST)</th>
              <th className="p-4 font-semibold">Trạng thái</th>
              <th className="p-4 font-semibold text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="p-8 text-center text-slate-500">
                  Đang tải dữ liệu...
                </td>
              </tr>
            ) : tenants.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-8 text-center text-slate-500">
                  Chưa có Tenant nào đăng ký.
                </td>
              </tr>
            ) : (
              tenants.map((t) => (
                <tr key={t.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="p-4 font-mono text-xs text-blue-700">{t.id}</td>
                  <td className="p-4 font-semibold text-slate-900">{t.name}</td>
                  <td className="p-4 text-slate-600">
                    <div>{t.email || '-'}</div>
                    <div className="text-xs text-slate-400">
                      {t.subscriptionPlan || 'STARTER'} • {t.phone || '-'}
                    </div>
                  </td>
                  <td className="p-4 text-xs text-slate-600 space-y-1">
                    <div className="font-mono">
                      {(t.gasUsedMist ?? 0).toLocaleString()} / {(t.gasQuotaMist ?? 0).toLocaleString()}
                    </div>
                    <div className="flex gap-1">
                      <input
                        type="number"
                        min={0}
                        placeholder={String(t.gasQuotaMist ?? 10000000000)}
                        value={quotaInputs[t.id] ?? ''}
                        onChange={(e) => setQuotaInputs((prev) => ({ ...prev, [t.id]: e.target.value }))}
                        className="w-28 px-2 py-1 border border-slate-300 rounded text-xs font-mono"
                      />
                      <button
                        type="button"
                        disabled={busyId === t.id}
                        onClick={() => updateGasQuota(t.id, t.gasQuotaMist ?? 10000000000)}
                        className="px-2 py-1 rounded bg-slate-800 text-white text-xs hover:bg-slate-700"
                      >
                        Set
                      </button>
                    </div>
                  </td>
                  <td className="p-4">
                    <span
                      className={`px-2.5 py-0.5 rounded text-xs font-semibold ${
                        t.status === 'APPROVED'
                          ? 'bg-emerald-100 text-emerald-700'
                          : t.status === 'PENDING'
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {t.status}
                    </span>
                  </td>
                  <td className="p-4 text-right space-x-2">
                    {t.status === 'PENDING' && (
                      <>
                        <button
                          onClick={() => updateStatus(t.id, 'APPROVED')}
                          disabled={busyId === t.id}
                          className="bg-emerald-600 text-white px-3 py-1 rounded text-xs font-medium hover:bg-emerald-700"
                        >
                          Duyệt
                        </button>
                        <button
                          onClick={() => updateStatus(t.id, 'REJECTED')}
                          disabled={busyId === t.id}
                          className="bg-red-600 text-white px-3 py-1 rounded text-xs font-medium hover:bg-red-700"
                        >
                          Từ chối
                        </button>
                      </>
                    )}
                    {t.status === 'APPROVED' && (
                      <button
                        onClick={() => updateStatus(t.id, 'SUSPENDED')}
                        disabled={busyId === t.id}
                        className="bg-amber-600 text-white px-3 py-1 rounded text-xs font-medium hover:bg-amber-700"
                      >
                        Khóa
                      </button>
                    )}
                    {t.status === 'SUSPENDED' && (
                      <button
                        onClick={() => updateStatus(t.id, 'APPROVED')}
                        disabled={busyId === t.id}
                        className="bg-emerald-600 text-white px-3 py-1 rounded text-xs font-medium hover:bg-emerald-700"
                      >
                        Mở khóa
                      </button>
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
