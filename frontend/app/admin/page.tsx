'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface AdminDashboard {
  totalTenants: number;
  pendingTenants: number;
  approvedTenants: number;
  rejectedTenants: number;
  suspendedTenants: number;
  totalTransactions: number;
  pendingSettlements: number;
  settledSettlements: number;
  failedSettlements: number;
  settlementMode: string;
}

interface SettlementItem {
  id: string;
  tenantId: string | null;
  customerId: string | null;
  transactionId: string;
  eventType: string;
  status: 'PENDING' | 'SETTLED' | 'FAILED';
  settlementMode: string;
  chainSeq: number | null;
  txDigest: string | null;
  retryCount: number;
  lastError: string | null;
  createdAt: string | null;
  settledAt: string | null;
}

export default function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [settlements, setSettlements] = useState<SettlementItem[]>([]);
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const loadData = async (statusFilter = filterStatus) => {
    setLoading(true);
    setError(null);
    try {
      const qs = statusFilter ? `?status=${encodeURIComponent(statusFilter)}` : '';
      const [dash, sett] = await Promise.all([
        apiFetch<AdminDashboard>('/api/admin/dashboard'),
        apiFetch<SettlementItem[]>(`/api/admin/settlements${qs}`),
      ]);
      setDashboard(dash);
      setSettlements(sett);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu Admin');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<AdminDashboard>('/api/admin/dashboard'),
      apiFetch<SettlementItem[]>('/api/admin/settlements'),
    ])
      .then(([dash, sett]) => {
        if (active) {
          setDashboard(dash);
          setSettlements(sett);
        }
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu Admin');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const handleRetry = async (eventId: string) => {
    setRetryingId(eventId);
    setError(null);
    try {
      await apiFetch(`/api/admin/settlements/${eventId}/retry`, { method: 'POST' });
      await loadData(filterStatus);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Retry thất bại');
    } finally {
      setRetryingId(null);
    }
  };

  if (loading && !dashboard) return <div className="text-slate-500">Đang tải Admin Dashboard...</div>;

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Admin Dashboard &amp; Sui Settlement</h1>
          <p className="text-sm text-slate-500 mt-1">
            Giám sát toàn hệ thống OctaPoint, Outbox Settlement Worker và xử lý giao dịch FAILED.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {dashboard && (
            <span className="px-3 py-1 rounded-full text-xs font-semibold bg-slate-200 text-slate-800">
              Mode: {dashboard.settlementMode}
            </span>
          )}
          <button
            onClick={() => loadData(filterStatus)}
            className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-sm hover:bg-slate-50"
          >
            Làm mới
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
          {error}
        </div>
      )}

      {dashboard && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
            <div className="text-xs font-medium text-slate-500 uppercase">Tổng số Tenant</div>
            <div className="text-3xl font-bold text-slate-900 mt-2">{dashboard.totalTenants}</div>
            <div className="text-xs text-amber-700 mt-1">
              {dashboard.pendingTenants} chờ duyệt • {dashboard.approvedTenants} đã duyệt
            </div>
          </div>
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
            <div className="text-xs font-medium text-slate-500 uppercase">Tổng giao dịch Ledger</div>
            <div className="text-3xl font-bold text-blue-600 mt-2">{dashboard.totalTransactions}</div>
          </div>
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
            <div className="text-xs font-medium text-slate-500 uppercase">Đã Settle (Sui)</div>
            <div className="text-3xl font-bold text-emerald-600 mt-2">{dashboard.settledSettlements}</div>
            <div className="text-xs text-slate-500 mt-1">Đang chờ (PENDING): {dashboard.pendingSettlements}</div>
          </div>
          <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
            <div className="text-xs font-medium text-slate-500 uppercase">Settlement FAILED</div>
            <div className="text-3xl font-bold text-red-600 mt-2">{dashboard.failedSettlements}</div>
            <div className="text-xs text-slate-500 mt-1">Hỗ trợ Retry thủ công (FR-33)</div>
          </div>
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200 bg-slate-50 flex flex-wrap items-center justify-between gap-2">
          <span className="font-semibold text-sm text-slate-700">Danh sách Outbox Settlements</span>
          <div className="flex gap-2 text-xs">
            {['', 'PENDING', 'SETTLED', 'FAILED'].map((st) => (
              <button
                key={st || 'ALL'}
                onClick={() => {
                  setFilterStatus(st);
                  loadData(st);
                }}
                className={`px-3 py-1 rounded-md font-medium ${
                  filterStatus === st
                    ? 'bg-slate-900 text-white'
                    : 'bg-white border border-slate-300 text-slate-700'
                }`}
              >
                {st || 'Tất cả'}
              </button>
            ))}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-slate-600">
              <tr>
                <th className="p-4 font-semibold">Event ID</th>
                <th className="p-4 font-semibold">Loại</th>
                <th className="p-4 font-semibold">Khách hàng</th>
                <th className="p-4 font-semibold">Seq</th>
                <th className="p-4 font-semibold">Mode</th>
                <th className="p-4 font-semibold">Trạng thái</th>
                <th className="p-4 font-semibold">Sui Digest / Lỗi</th>
                <th className="p-4 font-semibold text-right">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {settlements.length === 0 ? (
                <tr>
                  <td colSpan={8} className="p-6 text-center text-slate-500">
                    Chưa có sự kiện settlement nào.
                  </td>
                </tr>
              ) : (
                settlements.map((s) => (
                  <tr key={s.id} className="border-b border-slate-100 hover:bg-slate-50">
                    <td className="p-4 font-mono text-xs text-slate-700">{s.id}</td>
                    <td className="p-4 font-medium">{s.eventType}</td>
                    <td className="p-4 font-mono text-xs">{s.customerId || '-'}</td>
                    <td className="p-4 font-mono text-xs">{s.chainSeq ?? '-'}</td>
                    <td className="p-4 text-xs">{s.settlementMode}</td>
                    <td className="p-4">
                      <span
                        className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          s.status === 'SETTLED'
                            ? 'bg-emerald-100 text-emerald-700'
                            : s.status === 'PENDING'
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-red-100 text-red-700'
                        }`}
                      >
                        {s.status}
                      </span>
                    </td>
                    <td className="p-4 text-xs font-mono max-w-xs truncate">
                      {s.txDigest ? (
                        <a
                          href={`https://suiscan.xyz/testnet/tx/${s.txDigest}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-blue-600 hover:underline"
                        >
                          {s.txDigest.slice(0, 16)}...
                        </a>
                      ) : s.lastError ? (
                        <span className="text-red-600" title={s.lastError}>
                          {s.lastError}
                        </span>
                      ) : (
                        <span className="text-slate-400">-</span>
                      )}
                    </td>
                    <td className="p-4 text-right">
                      {s.status === 'FAILED' && (
                        <button
                          onClick={() => handleRetry(s.id)}
                          disabled={retryingId === s.id}
                          className="px-3 py-1 rounded bg-blue-600 text-white text-xs font-medium hover:bg-blue-700 disabled:opacity-60"
                        >
                          {retryingId === s.id ? 'Đang retry...' : 'Retry'}
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
    </div>
  );
}
