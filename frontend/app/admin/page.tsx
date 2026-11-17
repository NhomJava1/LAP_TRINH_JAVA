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
  pausedGasSettlements?: number;
  settlementMode: string;
}

interface MetricsSummary {
  creditsIssuedCount: number;
  creditsRedeemedCount: number;
  hmacRejectedCount: number;
  settlementSettledCount: number;
  settlementFailedCount: number;
  anomaliesDetectedCount: number;
}

interface ReconciliationReport {
  id: string;
  dbSettledIssuePoints: number;
  dbSettledRedeemPoints: number;
  chainBalanceValue: number;
  deltaPoints: number;
  status: string;
  details: string;
  createdAt: string | null;
}

interface LedgerAnchor {
  id: string;
  entryCount: number;
  batchSha256Hash: string;
  suiTxDigest: string;
  walrusBlobId: string | null;
  status: string;
  createdAt: string | null;
}

interface SettlementItem {
  id: string;
  tenantId: string | null;
  customerId: string | null;
  transactionId: string;
  eventType: string;
  status: 'PENDING' | 'SETTLED' | 'FAILED' | 'PAUSED_GAS_QUOTA';
  settlementMode: string;
  chainSeq: number | null;
  gasUsedMist?: number | null;
  txDigest: string | null;
  retryCount: number;
  lastError: string | null;
  createdAt: string | null;
  settledAt: string | null;
}

export default function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [metrics, setMetrics] = useState<MetricsSummary | null>(null);
  const [reconcileReports, setReconcileReports] = useState<ReconciliationReport[]>([]);
  const [anchors, setAnchors] = useState<LedgerAnchor[]>([]);
  const [settlements, setSettlements] = useState<SettlementItem[]>([]);
  const [filterStatus, setFilterStatus] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [busyAction, setBusyAction] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<string | null>(null);

  const loadData = async (statusFilter = filterStatus) => {
    setLoading(true);
    setError(null);
    try {
      const qs = statusFilter ? `?status=${encodeURIComponent(statusFilter)}` : '';
      const [dash, met, rec, anc, sett] = await Promise.all([
        apiFetch<AdminDashboard>('/api/admin/dashboard'),
        apiFetch<MetricsSummary>('/api/admin/metrics-summary'),
        apiFetch<ReconciliationReport[]>('/api/admin/reconcile/reports'),
        apiFetch<LedgerAnchor[]>('/api/admin/anchors'),
        apiFetch<SettlementItem[]>(`/api/admin/settlements${qs}`),
      ]);
      setDashboard(dash);
      setMetrics(met);
      setReconcileReports(rec);
      setAnchors(anc);
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
      apiFetch<MetricsSummary>('/api/admin/metrics-summary'),
      apiFetch<ReconciliationReport[]>('/api/admin/reconcile/reports'),
      apiFetch<LedgerAnchor[]>('/api/admin/anchors'),
      apiFetch<SettlementItem[]>('/api/admin/settlements'),
    ])
      .then(([dash, met, rec, anc, sett]) => {
        if (!active) return;
        setDashboard(dash);
        setMetrics(met);
        setReconcileReports(rec);
        setAnchors(anc);
        setSettlements(sett);
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

  const handleRunReconcile = async () => {
    setBusyAction('reconcile');
    setError(null);
    try {
      await apiFetch('/api/admin/reconcile', { method: 'POST' });
      await loadData(filterStatus);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đối soát thất bại');
    } finally {
      setBusyAction(null);
    }
  };

  const handleCreateAnchor = async () => {
    setBusyAction('anchor');
    setError(null);
    try {
      await apiFetch('/api/admin/anchors', { method: 'POST' });
      await loadData(filterStatus);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Neo hash lên Sui thất bại');
    } finally {
      setBusyAction(null);
    }
  };

  if (loading && !dashboard) return <div className="text-slate-500">Đang tải Admin Dashboard...</div>;

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Admin Dashboard &amp; Sui Settlement</h1>
          <p className="text-sm text-slate-500 mt-1">
            Giám sát hệ thống OctaPoint, Outbox Settlement Worker, Đối soát On-chain (FR-29) &amp; Neo Hash (FR-30).
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
            <div className="text-xs font-medium text-slate-500 uppercase">FAILED / PAUSED GAS</div>
            <div className="text-3xl font-bold text-red-600 mt-2">
              {dashboard.failedSettlements} / {dashboard.pausedGasSettlements ?? 0}
            </div>
            <div className="text-xs text-slate-500 mt-1">Hỗ trợ Retry thủ công (FR-33)</div>
          </div>
        </div>
      )}

      {metrics && (
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-800 mb-3">Micrometer Metrics Summary (NFR-06)</h2>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 text-center">
            <div className="p-2.5 bg-slate-50 rounded-lg">
              <div className="text-xs text-slate-500">Issue OK</div>
              <div className="text-lg font-bold text-slate-900">{metrics.creditsIssuedCount}</div>
            </div>
            <div className="p-2.5 bg-slate-50 rounded-lg">
              <div className="text-xs text-slate-500">Redeem OK</div>
              <div className="text-lg font-bold text-slate-900">{metrics.creditsRedeemedCount}</div>
            </div>
            <div className="p-2.5 bg-slate-50 rounded-lg">
              <div className="text-xs text-slate-500">HMAC Rejected</div>
              <div className="text-lg font-bold text-amber-700">{metrics.hmacRejectedCount}</div>
            </div>
            <div className="p-2.5 bg-slate-50 rounded-lg">
              <div className="text-xs text-slate-500">Settled</div>
              <div className="text-lg font-bold text-emerald-700">{metrics.settlementSettledCount}</div>
            </div>
            <div className="p-2.5 bg-slate-50 rounded-lg">
              <div className="text-xs text-slate-500">Settled Fail</div>
              <div className="text-lg font-bold text-red-700">{metrics.settlementFailedCount}</div>
            </div>
            <div className="p-2.5 bg-slate-50 rounded-lg">
              <div className="text-xs text-slate-500">Anomalies</div>
              <div className="text-lg font-bold text-purple-700">{metrics.anomaliesDetectedCount}</div>
            </div>
          </div>
        </div>
      )}

      {/* Reconciliation (FR-29) & Ledger Anchor (FR-30) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
          <div className="px-5 py-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
            <span className="font-semibold text-sm text-slate-700">Đối soát On-Chain vs DB (FR-29)</span>
            <button
              onClick={handleRunReconcile}
              disabled={busyAction !== null}
              className="px-3 py-1 rounded bg-slate-900 text-white text-xs font-medium hover:bg-slate-800 disabled:opacity-50"
            >
              {busyAction === 'reconcile' ? 'Đang đối soát...' : 'Chạy Đối soát'}
            </button>
          </div>
          <div className="divide-y divide-slate-100 text-xs">
            {reconcileReports.length === 0 ? (
              <div className="p-5 text-center text-slate-500">Chưa có báo cáo đối soát nào.</div>
            ) : (
              reconcileReports.slice(0, 5).map((r) => (
                <div key={r.id} className="p-4 space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-slate-600">{r.id}</span>
                    <span className="px-2 py-0.5 rounded font-semibold bg-emerald-100 text-emerald-700">{r.status}</span>
                  </div>
                  <div className="text-slate-700">{r.details}</div>
                  <div className="text-slate-500">
                    DB Issue: {r.dbSettledIssuePoints} • Chain: {r.chainBalanceValue} • Delta: {r.deltaPoints}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
          <div className="px-5 py-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
            <span className="font-semibold text-sm text-slate-700">Neo Hash Ledger lên Sui (FR-30)</span>
            <button
              onClick={handleCreateAnchor}
              disabled={busyAction !== null}
              className="px-3 py-1 rounded bg-blue-600 text-white text-xs font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {busyAction === 'anchor' ? 'Đang neo...' : 'Neo Hash ngay'}
            </button>
          </div>
          <div className="divide-y divide-slate-100 text-xs">
            {anchors.length === 0 ? (
              <div className="p-5 text-center text-slate-500">Chưa có bản ghi neo hash nào.</div>
            ) : (
              anchors.slice(0, 5).map((a) => (
                <div key={a.id} className="p-4 space-y-1">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-slate-700">{a.id} ({a.entryCount} entries)</span>
                    <span className="px-2 py-0.5 rounded font-semibold bg-blue-100 text-blue-700">{a.status}</span>
                  </div>
                  <div className="font-mono text-slate-600 truncate">SHA-256: {a.batchSha256Hash}</div>
                  <div className="font-mono">
                    Sui Tx:{' '}
                    {a.suiTxDigest.startsWith('mock_') || a.suiTxDigest.startsWith('anchor_') ? (
                      <span>{a.suiTxDigest}</span>
                    ) : (
                      <a
                        href={`https://suiscan.xyz/testnet/tx/${a.suiTxDigest}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-blue-600 hover:underline"
                      >
                        {a.suiTxDigest}
                      </a>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200 bg-slate-50 flex flex-wrap items-center justify-between gap-2">
          <span className="font-semibold text-sm text-slate-700">Danh sách Outbox Settlements</span>
          <div className="flex gap-2 text-xs">
            {['', 'PENDING', 'SETTLED', 'FAILED', 'PAUSED_GAS_QUOTA'].map((st) => (
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
                <th className="p-4 font-semibold">Gas (MIST)</th>
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
                    <td className="p-4 font-mono text-xs">{s.gasUsedMist ?? '-'}</td>
                    <td className="p-4">
                      <span
                        className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          s.status === 'SETTLED'
                            ? 'bg-emerald-100 text-emerald-700'
                            : s.status === 'PENDING' || s.status === 'PAUSED_GAS_QUOTA'
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
                      {(s.status === 'FAILED' || s.status === 'PAUSED_GAS_QUOTA') && (
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
