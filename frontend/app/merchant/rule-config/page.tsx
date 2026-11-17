'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface TenantRule {
  id: string;
  tenantId: string;
  earnVndPerPoint: number;
  redeemValueVndPerPoint: number;
  updatedAt: string | null;
}

export default function RuleConfigPage() {
  const [earnRate, setEarnRate] = useState<number>(1000);
  const [redeemRate, setRedeemRate] = useState<number>(10);
  const [testAmount, setTestAmount] = useState<number>(100000);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadRules = async () => {
      try {
        const rule = await apiFetch<TenantRule>('/api/portal/rules');
        setEarnRate(rule.earnVndPerPoint);
        setRedeemRate(rule.redeemValueVndPerPoint);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Không thể tải cấu hình rule');
      } finally {
        setLoading(false);
      }
    };
    loadRules();
  }, []);

  const calculatedPoints =
    earnRate > 0 && testAmount > 0 ? Math.floor(testAmount / earnRate) : 0;

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSavedMsg(null);
    try {
      const updated = await apiFetch<TenantRule>('/api/portal/rules', {
        method: 'PUT',
        body: JSON.stringify({
          earnVndPerPoint: earnRate,
          redeemValueVndPerPoint: redeemRate,
        }),
      });
      setEarnRate(updated.earnVndPerPoint);
      setRedeemRate(updated.redeemValueVndPerPoint);
      setSavedMsg('Đã lưu cấu hình rule thành công vào PostgreSQL!');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lưu rule thất bại');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="text-slate-500">Đang tải cấu hình rule...</div>;

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Cấu hình Rule Tích &amp; Đổi điểm</h1>
        <p className="text-sm text-slate-500 mt-1">
          Quy định tỷ lệ quy đổi điểm cho Merchant. Chỉ áp dụng cho các giao dịch phát sinh sau khi lưu.
        </p>
      </div>

      {savedMsg && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-800 rounded-lg text-sm">
          {savedMsg}
        </div>
      )}
      {error && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSave} className="space-y-6 bg-white p-6 rounded-xl border border-slate-200 shadow-sm">
        <div className="border-b border-slate-200 pb-6 space-y-4">
          <h2 className="text-lg font-semibold text-slate-800">1. Quy tắc Tích điểm (Earn)</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-end">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Số tiền chi tiêu (VND) để nhận 1 điểm
              </label>
              <input
                type="number"
                min={1}
                value={earnRate}
                onChange={(e) => setEarnRate(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Số điểm cộng
              </label>
              <input
                type="text"
                value="1 Điểm"
                disabled
                className="w-full px-3 py-2 border border-slate-200 bg-slate-50 rounded-lg text-slate-500"
              />
            </div>
          </div>

          <div className="p-4 bg-blue-50 rounded-lg border border-blue-100 text-sm">
            <div className="font-medium text-blue-900 mb-2">Thử nghiệm tính điểm:</div>
            <div className="flex flex-wrap items-center gap-2">
              <input
                type="number"
                min={0}
                value={testAmount}
                onChange={(e) => setTestAmount(Math.max(0, Number(e.target.value)))}
                className="w-36 px-3 py-1.5 border border-blue-300 rounded bg-white"
              />
              <span>VND → nhận được</span>
              <strong className="text-blue-700">{calculatedPoints} Điểm</strong>
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-slate-800">2. Giá trị Quy đổi (Redeem)</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-end">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Số điểm sử dụng
              </label>
              <input
                type="text"
                value="1 Điểm"
                disabled
                className="w-full px-3 py-2 border border-slate-200 bg-slate-50 rounded-lg text-slate-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Giá trị quy đổi tương ứng (VND)
              </label>
              <input
                type="number"
                min={1}
                value={redeemRate}
                onChange={(e) => setRedeemRate(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <button
            type="submit"
            disabled={saving}
            className="bg-blue-600 text-white px-6 py-2.5 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-60 transition"
          >
            {saving ? 'Đang lưu...' : 'Lưu cấu hình Rule'}
          </button>
        </div>
      </form>
    </div>
  );
}
