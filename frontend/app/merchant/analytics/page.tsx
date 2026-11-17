'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface DailyStat {
  date: string;
  issuedPoints: number;
  redeemedPoints: number;
  transactionCount: number;
}

interface TopCustomer {
  customerId: string;
  balance: number;
  lifetimeEarnedPoints: number;
}

interface AnalyticsData {
  totalTransactions: number;
  issueTransactions: number;
  redeemTransactions: number;
  totalIssuedPoints: number;
  totalRedeemedPoints: number;
  redemptionRatePercent: number;
  daily: DailyStat[];
  topCustomers: TopCustomer[];
}

interface AnomalyItem {
  id: string;
  customerId: string | null;
  transactionId: string | null;
  ruleCode: string;
  severity: string;
  description: string;
  status: string;
  createdAt: string | null;
}

export default function MerchantAnalyticsPage() {
  const [analytics, setAnalytics] = useState<AnalyticsData | null>(null);
  const [anomalies, setAnomalies] = useState<AnomalyItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<AnalyticsData>('/api/portal/analytics'),
      apiFetch<AnomalyItem[]>('/api/portal/anomalies'),
    ])
      .then(([ana, anom]) => {
        if (!active) return;
        setAnalytics(ana);
        setAnomalies(anom);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải dữ liệu phân tích');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  if (loading) return <div className="text-slate-500">Đang tải Analytics &amp; Anomaly Alerts...</div>;

  const maxDaily = Math.max(
    1,
    ...(analytics?.daily.map((d) => Math.max(d.issuedPoints, d.redeemedPoints)) ?? [1]),
  );

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Phân tích Điểm &amp; Cảnh báo Gian lận (FR-19 &amp; FR-22)</h1>
        <p className="text-sm text-slate-500 mt-1">
          Theo dõi xu hướng phát hành / đổi điểm theo ngày, khách hàng nổi bật và các giao dịch bất thường.
        </p>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
          {error}
        </div>
      )}

      {analytics && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-medium text-slate-500 uppercase">Tổng giao dịch</div>
              <div className="text-3xl font-bold text-slate-900 mt-2">{analytics.totalTransactions}</div>
              <div className="text-xs text-slate-500 mt-1">
                {analytics.issueTransactions} Issue • {analytics.redeemTransactions} Redeem
              </div>
            </div>
            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-medium text-slate-500 uppercase">Tổng điểm đã cấp</div>
              <div className="text-3xl font-bold text-emerald-600 mt-2">
                {analytics.totalIssuedPoints.toLocaleString('vi-VN')}
              </div>
            </div>
            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-medium text-slate-500 uppercase">Tổng điểm đã đổi</div>
              <div className="text-3xl font-bold text-purple-600 mt-2">
                {analytics.totalRedeemedPoints.toLocaleString('vi-VN')}
              </div>
            </div>
            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
              <div className="text-xs font-medium text-slate-500 uppercase">Tỷ lệ Redeem</div>
              <div className="text-3xl font-bold text-blue-600 mt-2">{analytics.redemptionRatePercent}%</div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm space-y-4">
              <h2 className="font-semibold text-slate-800">Lưu lượng điểm theo ngày</h2>
              {analytics.daily.length === 0 ? (
                <p className="text-sm text-slate-500">Chưa có dữ liệu theo ngày.</p>
              ) : (
                <div className="space-y-3">
                  {analytics.daily.map((d) => (
                    <div key={d.date} className="space-y-1 text-xs">
                      <div className="flex justify-between text-slate-600 font-medium">
                        <span>{d.date}</span>
                        <span>
                          +{d.issuedPoints} / -{d.redeemedPoints} ({d.transactionCount} tx)
                        </span>
                      </div>
                      <div className="h-2.5 w-full bg-slate-100 rounded-full overflow-hidden flex gap-0.5">
                        <div
                          className="bg-emerald-500 h-full rounded-l-full"
                          style={{ width: `${Math.min(100, (d.issuedPoints / maxDaily) * 100)}%` }}
                        />
                        <div
                          className="bg-purple-500 h-full rounded-r-full"
                          style={{ width: `${Math.min(100, (d.redeemedPoints / maxDaily) * 100)}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm space-y-4">
              <h2 className="font-semibold text-slate-800">Top Khách hàng theo Số dư</h2>
              {analytics.topCustomers.length === 0 ? (
                <p className="text-sm text-slate-500">Chưa có khách hàng.</p>
              ) : (
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-slate-200 text-slate-600">
                    <tr>
                      <th className="py-2 font-semibold">Customer ID</th>
                      <th className="py-2 font-semibold text-right">Số dư hiện tại</th>
                      <th className="py-2 font-semibold text-right">Tổng tích lũy</th>
                    </tr>
                  </thead>
                  <tbody>
                    {analytics.topCustomers.map((c) => (
                      <tr key={c.customerId} className="border-b border-slate-100">
                        <td className="py-2 font-mono text-xs">{c.customerId}</td>
                        <td className="py-2 text-right font-semibold">{c.balance.toLocaleString('vi-VN')}</td>
                        <td className="py-2 text-right text-slate-500">
                          {c.lifetimeEarnedPoints.toLocaleString('vi-VN')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </>
      )}

      <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
        <div className="px-5 py-3 border-b border-slate-200 bg-slate-50 font-semibold text-sm text-slate-700">
          Cảnh báo Giao dịch Bất thường (FR-22 Anomaly Detection)
        </div>
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-slate-600">
            <tr>
              <th className="p-4 font-semibold">Rule</th>
              <th className="p-4 font-semibold">Mức độ</th>
              <th className="p-4 font-semibold">Khách hàng</th>
              <th className="p-4 font-semibold">Mô tả</th>
              <th className="p-4 font-semibold">Thời gian</th>
            </tr>
          </thead>
          <tbody>
            {anomalies.length === 0 ? (
              <tr>
                <td colSpan={5} className="p-6 text-center text-slate-500">
                  Không có cảnh báo bất thường nào được ghi nhận.
                </td>
              </tr>
            ) : (
              anomalies.map((a) => (
                <tr key={a.id} className="border-b border-slate-100">
                  <td className="p-4 font-mono text-xs font-semibold text-slate-800">{a.ruleCode}</td>
                  <td className="p-4">
                    <span
                      className={`px-2 py-0.5 rounded text-xs font-semibold ${
                        a.severity === 'HIGH'
                          ? 'bg-red-100 text-red-700'
                          : 'bg-amber-100 text-amber-800'
                      }`}
                    >
                      {a.severity}
                    </span>
                  </td>
                  <td className="p-4 font-mono text-xs">{a.customerId || '-'}</td>
                  <td className="p-4 text-slate-700">{a.description}</td>
                  <td className="p-4 text-xs text-slate-500">
                    {a.createdAt ? new Date(a.createdAt).toLocaleString('vi-VN') : '-'}
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
