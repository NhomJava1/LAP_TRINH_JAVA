'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { apiFetch } from '@/lib/api';
import { useAuthStore } from '@/store/autoStore';

interface DashboardData {
  tenant: {
    id: string;
    name: string;
    email: string;
    status: string;
  };
  rule: {
    earnVndPerPoint: number;
    redeemValueVndPerPoint: number;
  };
  stats: {
    circulatingPoints: number;
    totalCustomers: number;
    totalIssuedPoints: number;
    totalRedeemedPoints: number;
    pendingSettlements: number;
    settledSettlements: number;
    failedSettlements: number;
    settlementMode: string;
  };
  activeApiKeys: number;
}

export default function MerchantDashboardPage() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const updateTenantStatus = useAuthStore((state) => state.updateTenantStatus);

  const loadDashboard = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch<DashboardData>('/api/portal/dashboard');
      setData(res);
      updateTenantStatus(res.tenant.status);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải dashboard');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    apiFetch<DashboardData>('/api/portal/dashboard')
      .then((res) => {
        if (active) {
          setData(res);
          updateTenantStatus(res.tenant.status);
        }
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải dashboard');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <div className="text-slate-500">Đang tải tổng quan Merchant...</div>;
  if (error) return <div className="p-4 rounded-lg bg-red-50 text-red-700 border border-red-200">{error}</div>;
  if (!data) return null;

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">
            Xin chào, {data.tenant.name}
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Mã Tenant: <code className="font-mono text-slate-700">{data.tenant.id}</code>
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="px-3 py-1 rounded-full text-xs font-semibold bg-slate-200 text-slate-800">
            Settlement Mode: {data.stats.settlementMode}
          </span>
          <button
            onClick={loadDashboard}
            className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-sm hover:bg-slate-50"
          >
            Làm mới
          </button>
        </div>
      </div>

      {data.tenant.status !== 'APPROVED' && (
        <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900">
          <strong>Trạng thái Tenant: {data.tenant.status}.</strong> Tenant chưa được Admin duyệt nên tạm thời chưa thể tạo API Key hoặc thực hiện giao dịch Issue/Redeem.
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-medium text-slate-500 uppercase">Điểm đang lưu hành</div>
          <div className="text-3xl font-bold text-blue-600 mt-2">
            {data.stats.circulatingPoints.toLocaleString()}
          </div>
          <div className="text-xs text-slate-400 mt-1">{data.stats.totalCustomers} khách hàng</div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-medium text-slate-500 uppercase">Tổng điểm đã cấp (Issue)</div>
          <div className="text-3xl font-bold text-emerald-600 mt-2">
            +{data.stats.totalIssuedPoints.toLocaleString()}
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-medium text-slate-500 uppercase">Tổng điểm đã đổi (Redeem)</div>
          <div className="text-3xl font-bold text-purple-600 mt-2">
            -{data.stats.totalRedeemedPoints.toLocaleString()}
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
          <div className="text-xs font-medium text-slate-500 uppercase">API Key đang hoạt động</div>
          <div className="text-3xl font-bold text-slate-900 mt-2">{data.activeApiKeys}</div>
          <Link href="/merchant/api-keys" className="text-xs text-blue-600 hover:underline mt-1 inline-block">
            Quản lý API Key →
          </Link>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-slate-900">Rule Tích &amp; Đổi điểm hiện tại</h2>
            <Link href="/merchant/rule-config" className="text-xs text-blue-600 hover:underline">
              Chỉnh sửa
            </Link>
          </div>
          <div className="text-sm text-slate-700 space-y-1">
            <div>
              Tích điểm: <strong>{data.rule.earnVndPerPoint.toLocaleString()} VND = 1 điểm</strong>
            </div>
            <div>
              Quy đổi: <strong>1 điểm = {data.rule.redeemValueVndPerPoint.toLocaleString()} VND</strong>
            </div>
          </div>
        </div>

        <div className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="font-semibold text-slate-900">Trạng thái Sui Settlement</h2>
            <Link href="/merchant/history" className="text-xs text-blue-600 hover:underline">
              Xem chi tiết
            </Link>
          </div>
          <div className="flex gap-4 text-sm">
            <span className="px-3 py-1 rounded bg-emerald-50 text-emerald-700 font-medium">
              SETTLED: {data.stats.settledSettlements}
            </span>
            <span className="px-3 py-1 rounded bg-amber-50 text-amber-800 font-medium">
              PENDING: {data.stats.pendingSettlements}
            </span>
            <span className="px-3 py-1 rounded bg-red-50 text-red-700 font-medium">
              FAILED: {data.stats.failedSettlements}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
