'use client';

import { useCallback, useEffect, useState } from 'react';
import { API_BASE, apiFetch } from '@/lib/api';
import { useAuthStore } from '@/store/autoStore';

interface TransactionItem {
  transactionId: string;
  eventId: string | null;
  customerId: string;
  type: 'ISSUE' | 'REDEEM';
  points: number;
  amountVnd: number | null;
  orderId: string | null;
  appliedTier?: string | null;
  expiresAt?: string | null;
  chainSeq: number | null;
  settlementStatus: 'PENDING' | 'SETTLED' | 'FAILED' | 'PAUSED_GAS_QUOTA';
  settlementMode: string;
  txDigest: string | null;
  lastError: string | null;
  createdAt: string | null;
  settledAt: string | null;
}

interface CustomerBalanceItem {
  customerId: string;
  balance: number;
  chainSeq: number;
  createdAt: string | null;
}

export default function MerchantHistoryPage() {
  const { token } = useAuthStore();
  const [customerFilter, setCustomerFilter] = useState('');
  const [transactions, setTransactions] = useState<TransactionItem[]>([]);
  const [customers, setCustomers] = useState<CustomerBalanceItem[]>([]);
  const [sseConnected, setSseConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async (filter: string) => {
    setLoading(true);
    setError(null);
    try {
      const qs = filter.trim() ? `?customerId=${encodeURIComponent(filter.trim())}` : '';
      const [txData, custData] = await Promise.all([
        apiFetch<TransactionItem[]>(`/api/portal/transactions${qs}`),
        apiFetch<CustomerBalanceItem[]>('/api/portal/customers'),
      ]);
      setTransactions(txData);
      setCustomers(custData);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải lịch sử giao dịch');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<TransactionItem[]>('/api/portal/transactions'),
      apiFetch<CustomerBalanceItem[]>('/api/portal/customers'),
    ])
      .then(([txData, custData]) => {
        if (active) {
          setTransactions(txData);
          setCustomers(custData);
        }
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải lịch sử giao dịch');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    if (!token) {
      return () => {
        active = false;
      };
    }

    const es = new EventSource(`${API_BASE}/api/portal/events/stream?token=${encodeURIComponent(token)}`);
    es.onopen = () => {
      if (active) setSseConnected(true);
    };
    es.onerror = () => {
      if (active) setSseConnected(false);
    };
    es.addEventListener('settlement-update', () => {
      if (!active) return;
      Promise.all([
        apiFetch<TransactionItem[]>('/api/portal/transactions'),
        apiFetch<CustomerBalanceItem[]>('/api/portal/customers'),
      ])
        .then(([txData, custData]) => {
          if (active) {
            setTransactions(txData);
            setCustomers(custData);
          }
        })
        .catch(() => {});
    });

    return () => {
      active = false;
      es.close();
    };
  }, [token]);

  const handleFilterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loadData(customerFilter);
  };

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Lịch sử Giao dịch &amp; Số dư Khách hàng</h1>
          <p className="text-sm text-slate-500 mt-1">
            Theo dõi sổ cái PostgreSQL bất biến và liên kết kiểm chứng Sui Testnet Explorer.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span
            className={`px-2.5 py-1 rounded-full text-xs font-medium ${
              sseConnected
                ? 'bg-emerald-100 text-emerald-700'
                : 'bg-slate-200 text-slate-600'
            }`}
          >
            {sseConnected ? '● Live SSE Connected' : '○ SSE Offline'}
          </span>
          <button
            onClick={() => loadData(customerFilter)}
            className="px-4 py-2 rounded-lg border border-slate-300 bg-white text-sm font-medium hover:bg-slate-50"
          >
            Làm mới trạng thái
          </button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
          {error}
        </div>
      )}

      {/* Customer Balances */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200 bg-slate-50 font-semibold text-sm text-slate-700">
          Số dư theo Khách hàng
        </div>
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-500">
            <tr>
              <th className="p-4 font-semibold">Customer ID</th>
              <th className="p-4 font-semibold">Số dư khả dụng</th>
              <th className="p-4 font-semibold">Chain Seq</th>
            </tr>
          </thead>
          <tbody>
            {customers.length === 0 ? (
              <tr>
                <td colSpan={3} className="p-4 text-center text-slate-400">
                  Chưa có khách hàng phát sinh giao dịch.
                </td>
              </tr>
            ) : (
              customers.map((c) => (
                <tr key={c.customerId} className="border-b border-slate-100">
                  <td className="p-4 font-mono font-medium text-blue-700">{c.customerId}</td>
                  <td className="p-4 font-bold text-slate-900">{c.balance.toLocaleString()} điểm</td>
                  <td className="p-4 font-mono text-slate-600">{c.chainSeq}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Filter */}
      <form onSubmit={handleFilterSubmit} className="flex gap-2">
        <input
          type="text"
          value={customerFilter}
          onChange={(e) => setCustomerFilter(e.target.value)}
          placeholder="Lọc theo Customer ID (ví dụ: C1)..."
          className="w-72 px-3 py-2 border border-slate-300 rounded-lg bg-white text-sm"
        />
        <button
          type="submit"
          className="px-4 py-2 rounded-lg bg-slate-900 text-white text-sm font-medium hover:bg-slate-800"
        >
          Lọc
        </button>
        {customerFilter && (
          <button
            type="button"
            onClick={() => {
              setCustomerFilter('');
              loadData('');
            }}
            className="px-3 py-2 rounded-lg border border-slate-300 bg-white text-sm"
          >
            Xóa lọc
          </button>
        )}
      </form>

      {/* Transactions */}
      <div className="bg-white border border-slate-200 rounded-xl overflow-x-auto shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 border-b border-slate-200 text-slate-600">
            <tr>
              <th className="p-4 font-semibold">Khách hàng</th>
              <th className="p-4 font-semibold">Loại</th>
              <th className="p-4 font-semibold">Điểm</th>
              <th className="p-4 font-semibold">Order ID</th>
              <th className="p-4 font-semibold">Seq</th>
              <th className="p-4 font-semibold">Mode</th>
              <th className="p-4 font-semibold">Settlement</th>
              <th className="p-4 font-semibold">Sui Tx Digest</th>
              <th className="p-4 font-semibold">Thời gian</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={9} className="p-6 text-center text-slate-500">
                  Đang tải lịch sử...
                </td>
              </tr>
            ) : transactions.length === 0 ? (
              <tr>
                <td colSpan={9} className="p-6 text-center text-slate-500">
                  Chưa có giao dịch nào.
                </td>
              </tr>
            ) : (
              transactions.map((tx) => (
                <tr key={tx.transactionId} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="p-4 font-mono font-medium text-slate-900">{tx.customerId}</td>
                  <td className="p-4">
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-semibold ${
                        tx.type === 'ISSUE'
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-purple-100 text-purple-700'
                      }`}
                    >
                      {tx.type}
                    </span>
                  </td>
                  <td className="p-4 font-semibold">
                    {tx.type === 'ISSUE' ? '+' : '-'}
                    {tx.points}
                  </td>
                  <td className="p-4 font-mono text-xs text-slate-600">{tx.orderId || '-'}</td>
                  <td className="p-4 font-mono text-xs text-slate-600">{tx.chainSeq ?? '-'}</td>
                  <td className="p-4">
                    <span className="px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700">
                      {tx.settlementMode}
                    </span>
                  </td>
                  <td className="p-4">
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-semibold ${
                        tx.settlementStatus === 'SETTLED'
                          ? 'bg-emerald-100 text-emerald-700'
                          : tx.settlementStatus === 'PENDING'
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {tx.settlementStatus}
                    </span>
                  </td>
                  <td className="p-4 font-mono text-xs">
                    {tx.txDigest ? (
                      <a
                        href={`https://suiscan.xyz/testnet/tx/${tx.txDigest}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-blue-600 hover:underline"
                      >
                        {tx.txDigest.slice(0, 14)}...
                      </a>
                    ) : (
                      <span className="text-slate-400">-</span>
                    )}
                  </td>
                  <td className="p-4 text-xs text-slate-500">
                    {tx.createdAt ? new Date(tx.createdAt).toLocaleString('vi-VN') : '-'}
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
