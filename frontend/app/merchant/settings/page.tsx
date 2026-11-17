'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface WebhookConfig {
  webhookUrl: string | null;
  hasSecret: boolean;
}

interface WebhookDelivery {
  id: string;
  eventId: string;
  targetUrl: string;
  status: string;
  attemptCount: number;
  lastHttpStatus: number | null;
  lastError: string | null;
  createdAt: string | null;
}

interface StaffItem {
  id: string;
  name: string;
  email: string;
  role: string;
  createdAt: string | null;
}

interface BillingInfo {
  plan: 'STARTER' | 'GROWTH' | 'ENTERPRISE';
  gasQuotaMist: number | null;
  gasUsedMist: number | null;
  monthlyTransactions: number;
  baseFeeVnd: number;
  usageFeeVnd: number;
  totalInvoiceVnd: number;
  invoicePeriod: string;
  billingMode: string;
}

export default function MerchantSettingsPage() {
  const [webhookUrl, setWebhookUrl] = useState('');
  const [webhookSecret, setWebhookSecret] = useState('');
  const [hasSecret, setHasSecret] = useState(false);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);

  const [staff, setStaff] = useState<StaffItem[]>([]);
  const [staffName, setStaffName] = useState('');
  const [staffEmail, setStaffEmail] = useState('');
  const [staffPassword, setStaffPassword] = useState('');

  const [billing, setBilling] = useState<BillingInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refreshAll = async () => {
    const [wh, deliv, stf, bill] = await Promise.all([
      apiFetch<WebhookConfig>('/api/portal/webhook'),
      apiFetch<WebhookDelivery[]>('/api/portal/webhook/deliveries'),
      apiFetch<StaffItem[]>('/api/portal/staff'),
      apiFetch<BillingInfo>('/api/portal/billing'),
    ]);
    setWebhookUrl(wh.webhookUrl ?? '');
    setHasSecret(wh.hasSecret);
    setDeliveries(deliv);
    setStaff(stf);
    setBilling(bill);
  };

  useEffect(() => {
    let active = true;
    Promise.all([
      apiFetch<WebhookConfig>('/api/portal/webhook'),
      apiFetch<WebhookDelivery[]>('/api/portal/webhook/deliveries'),
      apiFetch<StaffItem[]>('/api/portal/staff'),
      apiFetch<BillingInfo>('/api/portal/billing'),
    ])
      .then(([wh, deliv, stf, bill]) => {
        if (!active) return;
        setWebhookUrl(wh.webhookUrl ?? '');
        setHasSecret(wh.hasSecret);
        setDeliveries(deliv);
        setStaff(stf);
        setBilling(bill);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải cấu hình Merchant');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const handleSaveWebhook = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await apiFetch('/api/portal/webhook', {
        method: 'PUT',
        body: JSON.stringify({ webhookUrl, webhookSecret: webhookSecret || undefined }),
      });
      setWebhookSecret('');
      await refreshAll();
      setMessage('Đã lưu cấu hình Webhook HMAC-SHA256!');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lưu webhook thất bại');
    } finally {
      setBusy(false);
    }
  };

  const handleTestWebhook = async () => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const res = await apiFetch<WebhookDelivery>('/api/portal/webhook/test', { method: 'POST' });
      await refreshAll();
      setMessage(`Webhook test status: ${res.status} (HTTP ${res.lastHttpStatus ?? 'N/A'})`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Test webhook thất bại');
    } finally {
      setBusy(false);
    }
  };

  const handleCreateStaff = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await apiFetch('/api/portal/staff', {
        method: 'POST',
        body: JSON.stringify({ name: staffName, email: staffEmail, password: staffPassword }),
      });
      setStaffName('');
      setStaffEmail('');
      setStaffPassword('');
      await refreshAll();
      setMessage('Đã tạo tài khoản STAFF (chỉ đọc)!');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Tạo nhân viên thất bại');
    } finally {
      setBusy(false);
    }
  };

  const handleChangePlan = async (plan: 'STARTER' | 'GROWTH' | 'ENTERPRISE') => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await apiFetch('/api/portal/billing/plan', {
        method: 'PUT',
        body: JSON.stringify({ plan }),
      });
      await refreshAll();
      setMessage(`Đã chuyển sang gói ${plan} (Simulated Billing).`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đổi gói dịch vụ thất bại');
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <div className="text-slate-500">Đang tải cài đặt Merchant...</div>;

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Cài đặt Webhook, Nhân viên (RBAC) &amp; Hóa đơn SaaS</h1>
        <p className="text-sm text-slate-500 mt-1">
          Quản lý Webhook thông báo Settlement (FR-28), tài khoản STAFF chỉ đọc (FR-31) và gói thuê bao (FR-20).
        </p>
      </div>

      {message && (
        <div className="p-4 rounded-lg bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm">
          {message}
        </div>
      )}
      {error && (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
          {error}
        </div>
      )}

      {/* Billing & Gas Quota (FR-20) */}
      {billing && (
        <section className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h2 className="text-lg font-semibold text-slate-800">1. Gói cước SaaS &amp; Hóa đơn Tạm tính ({billing.invoicePeriod})</h2>
              <p className="text-xs text-slate-500">Mode: {billing.billingMode} • Gas đã dùng: {(billing.gasUsedMist ?? 0).toLocaleString()} / {(billing.gasQuotaMist ?? 0).toLocaleString()} MIST</p>
            </div>
            <div className="flex gap-2">
              {(['STARTER', 'GROWTH', 'ENTERPRISE'] as const).map((p) => (
                <button
                  key={p}
                  type="button"
                  disabled={busy}
                  onClick={() => handleChangePlan(p)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold border transition ${
                    billing.plan === p
                      ? 'bg-slate-900 text-white border-slate-900'
                      : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2 text-sm">
            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <div className="text-xs text-slate-500">Phí cố định ({billing.plan})</div>
              <div className="text-lg font-bold text-slate-900 mt-1">{billing.baseFeeVnd.toLocaleString('vi-VN')} VND</div>
            </div>
            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <div className="text-xs text-slate-500">Phí giao dịch ({billing.monthlyTransactions} tx × 500đ)</div>
              <div className="text-lg font-bold text-slate-900 mt-1">{billing.usageFeeVnd.toLocaleString('vi-VN')} VND</div>
            </div>
            <div className="p-3 bg-blue-50 rounded-lg border border-blue-200">
              <div className="text-xs text-blue-700 font-medium">Tổng hóa đơn kỳ {billing.invoicePeriod}</div>
              <div className="text-lg font-bold text-blue-900 mt-1">{billing.totalInvoiceVnd.toLocaleString('vi-VN')} VND</div>
            </div>
          </div>
        </section>
      )}

      {/* Webhook (FR-28) */}
      <section className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
        <h2 className="text-lg font-semibold text-slate-800">2. Webhook Thông báo Settlement (FR-28 HMAC-SHA256)</h2>
        <form onSubmit={handleSaveWebhook} className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
          <div className="md:col-span-2">
            <label className="block text-xs font-medium text-slate-600 mb-1">Webhook Endpoint URL</label>
            <input
              type="url"
              value={webhookUrl}
              onChange={(e) => setWebhookUrl(e.target.value)}
              placeholder="https://merchant.example.com/webhooks/octapoint"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">
              Webhook Secret {hasSecret ? '(đã cấu hình)' : ''}
            </label>
            <input
              type="text"
              value={webhookSecret}
              onChange={(e) => setWebhookSecret(e.target.value)}
              placeholder="whsec_..."
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm font-mono"
            />
          </div>
          <div className="md:col-span-3 flex gap-2 justify-end">
            <button
              type="button"
              disabled={busy || !webhookUrl}
              onClick={handleTestWebhook}
              className="px-4 py-2 rounded-lg border border-slate-300 text-sm font-medium hover:bg-slate-50 disabled:opacity-50"
            >
              Gửi Webhook Test
            </button>
            <button
              type="submit"
              disabled={busy}
              className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              Lưu Webhook
            </button>
          </div>
        </form>

        {deliveries.length > 0 && (
          <div className="overflow-x-auto pt-2">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 border-y border-slate-200 text-slate-600">
                <tr>
                  <th className="p-2.5">Event ID</th>
                  <th className="p-2.5">URL</th>
                  <th className="p-2.5">Trạng thái</th>
                  <th className="p-2.5">HTTP / Attempts</th>
                  <th className="p-2.5">Thời gian</th>
                </tr>
              </thead>
              <tbody>
                {deliveries.slice(0, 8).map((d) => (
                  <tr key={d.id} className="border-b border-slate-100">
                    <td className="p-2.5 font-mono">{d.eventId}</td>
                    <td className="p-2.5 truncate max-w-[200px]">{d.targetUrl}</td>
                    <td className="p-2.5 font-semibold">{d.status}</td>
                    <td className="p-2.5">{d.lastHttpStatus ?? d.lastError ?? '-'} ({d.attemptCount})</td>
                    <td className="p-2.5 text-slate-500">
                      {d.createdAt ? new Date(d.createdAt).toLocaleString('vi-VN') : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Staff RBAC (FR-31) */}
      <section className="bg-white p-6 rounded-xl border border-slate-200 shadow-sm space-y-4">
        <h2 className="text-lg font-semibold text-slate-800">3. Tài khoản Nhân viên / STAFF Read-Only (FR-31)</h2>
        <form onSubmit={handleCreateStaff} className="grid grid-cols-1 md:grid-cols-4 gap-3 items-end">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Họ tên</label>
            <input
              type="text"
              value={staffName}
              onChange={(e) => setStaffName(e.target.value)}
              placeholder="Nhân viên Kho A"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Email</label>
            <input
              type="email"
              required
              value={staffEmail}
              onChange={(e) => setStaffEmail(e.target.value)}
              placeholder="staff@merchant.vn"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1">Mật khẩu</label>
            <input
              type="password"
              required
              value={staffPassword}
              onChange={(e) => setStaffPassword(e.target.value)}
              placeholder="Tối thiểu 8 ký tự"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm"
            />
          </div>
          <button
            type="submit"
            disabled={busy}
            className="px-4 py-2 rounded-lg bg-slate-900 text-white text-sm font-medium hover:bg-slate-800 disabled:opacity-50"
          >
            + Thêm STAFF
          </button>
        </form>

        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 border-y border-slate-200 text-slate-600">
            <tr>
              <th className="p-3 font-semibold">Tên</th>
              <th className="p-3 font-semibold">Email</th>
              <th className="p-3 font-semibold">Vai trò</th>
            </tr>
          </thead>
          <tbody>
            {staff.map((u) => (
              <tr key={u.id} className="border-b border-slate-100">
                <td className="p-3 font-medium text-slate-900">{u.name}</td>
                <td className="p-3 font-mono text-xs">{u.email}</td>
                <td className="p-3">
                  <span className="px-2 py-0.5 rounded text-xs font-semibold bg-slate-100 text-slate-700">
                    {u.role}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
