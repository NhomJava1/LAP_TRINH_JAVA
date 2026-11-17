'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';
import { useAuthStore } from '@/store/autoStore';

interface TenantRule {
  id: string;
  tenantId: string;
  earnVndPerPoint: number;
  redeemValueVndPerPoint: number;
  pointExpiryDays: number;
  silverThresholdPoints: number;
  goldThresholdPoints: number;
  silverMultiplier: number;
  goldMultiplier: number;
  updatedAt: string | null;
}

export default function RuleConfigPage() {
  const { role } = useAuthStore();
  const [earnRate, setEarnRate] = useState<number>(1000);
  const [redeemRate, setRedeemRate] = useState<number>(10);
  const [pointExpiryDays, setPointExpiryDays] = useState<number>(365);
  const [silverThresholdPoints, setSilverThresholdPoints] = useState<number>(500);
  const [goldThresholdPoints, setGoldThresholdPoints] = useState<number>(2000);
  const [silverMultiplier, setSilverMultiplier] = useState<number>(1.2);
  const [goldMultiplier, setGoldMultiplier] = useState<number>(1.5);
  const [testAmount, setTestAmount] = useState<number>(100000);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiFetch<TenantRule>('/api/portal/rules')
      .then((rule) => {
        if (!active) return;
        setEarnRate(rule.earnVndPerPoint);
        setRedeemRate(rule.redeemValueVndPerPoint);
        setPointExpiryDays(rule.pointExpiryDays ?? 365);
        setSilverThresholdPoints(rule.silverThresholdPoints ?? 500);
        setGoldThresholdPoints(rule.goldThresholdPoints ?? 2000);
        setSilverMultiplier(Number(rule.silverMultiplier ?? 1.2));
        setGoldMultiplier(Number(rule.goldMultiplier ?? 1.5));
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải cấu hình rule');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const basePoints = earnRate > 0 && testAmount > 0 ? Math.floor(testAmount / earnRate) : 0;

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
          pointExpiryDays,
          silverThresholdPoints,
          goldThresholdPoints,
          silverMultiplier,
          goldMultiplier,
        }),
      });
      setEarnRate(updated.earnVndPerPoint);
      setRedeemRate(updated.redeemValueVndPerPoint);
      setPointExpiryDays(updated.pointExpiryDays ?? 365);
      setSilverThresholdPoints(updated.silverThresholdPoints ?? 500);
      setGoldThresholdPoints(updated.goldThresholdPoints ?? 2000);
      setSilverMultiplier(Number(updated.silverMultiplier ?? 1.2));
      setGoldMultiplier(Number(updated.goldMultiplier ?? 1.5));
      setSavedMsg('Đã lưu cấu hình rule & hạng thành viên (FR-18) vào PostgreSQL!');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Lưu rule thất bại');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="text-slate-500">Đang tải cấu hình rule...</div>;

  const readOnly = role === 'STAFF';

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Cấu hình Rule, Hạn điểm &amp; Hạng thành viên (FR-18)</h1>
        <p className="text-sm text-slate-500 mt-1">
          Quy định tỷ lệ quy đổi điểm, hạn sử dụng và hệ số nhân cho hạng Silver / Gold.
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
          <h2 className="text-lg font-semibold text-slate-800">1. Quy tắc Tích điểm (Earn) &amp; Hạn điểm</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-end">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Số tiền chi tiêu (VND) để nhận 1 điểm
              </label>
              <input
                type="number"
                min={1}
                disabled={readOnly}
                value={earnRate}
                onChange={(e) => setEarnRate(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Hạn sử dụng điểm (ngày)
              </label>
              <input
                type="number"
                min={1}
                disabled={readOnly}
                value={pointExpiryDays}
                onChange={(e) => setPointExpiryDays(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
          </div>

          <div className="p-4 bg-blue-50 rounded-lg border border-blue-100 text-sm">
            <div className="font-medium text-blue-900 mb-2">Thử nghiệm tính điểm theo hạng:</div>
            <div className="flex flex-wrap items-center gap-3">
              <input
                type="number"
                min={0}
                value={testAmount}
                onChange={(e) => setTestAmount(Math.max(0, Number(e.target.value)))}
                className="w-36 px-3 py-1.5 border border-blue-300 rounded bg-white"
              />
              <span>VND →</span>
              <span className="text-slate-700">Bronze: <strong>{basePoints} điểm</strong></span>
              <span className="text-slate-700">
                • Silver ({silverMultiplier}x): <strong>{Math.max(1, Math.floor(basePoints * silverMultiplier))} điểm</strong>
              </span>
              <span className="text-amber-700">
                • Gold ({goldMultiplier}x): <strong>{Math.max(1, Math.floor(basePoints * goldMultiplier))} điểm</strong>
              </span>
            </div>
          </div>
        </div>

        <div className="border-b border-slate-200 pb-6 space-y-4">
          <h2 className="text-lg font-semibold text-slate-800">2. Hạng thành viên (Silver / Gold Multipliers)</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Ngưỡng Silver (tổng điểm tích lũy)
              </label>
              <input
                type="number"
                min={1}
                disabled={readOnly}
                value={silverThresholdPoints}
                onChange={(e) => setSilverThresholdPoints(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Hệ số nhân Silver (Multiplier)
              </label>
              <input
                type="number"
                step="0.05"
                min={1}
                disabled={readOnly}
                value={silverMultiplier}
                onChange={(e) => setSilverMultiplier(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Ngưỡng Gold (tổng điểm tích lũy)
              </label>
              <input
                type="number"
                min={1}
                disabled={readOnly}
                value={goldThresholdPoints}
                onChange={(e) => setGoldThresholdPoints(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1">
                Hệ số nhân Gold (Multiplier)
              </label>
              <input
                type="number"
                step="0.05"
                min={1}
                disabled={readOnly}
                value={goldMultiplier}
                onChange={(e) => setGoldMultiplier(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
          </div>
        </div>

        <div className="space-y-4">
          <h2 className="text-lg font-semibold text-slate-800">3. Giá trị Quy đổi (Redeem)</h2>
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
                disabled={readOnly}
                value={redeemRate}
                onChange={(e) => setRedeemRate(Math.max(1, Number(e.target.value)))}
                className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                required
              />
            </div>
          </div>
        </div>

        {!readOnly && (
          <div className="flex justify-end pt-2">
            <button
              type="submit"
              disabled={saving}
              className="bg-blue-600 text-white px-6 py-2.5 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-60 transition"
            >
              {saving ? 'Đang lưu...' : 'Lưu cấu hình Rule'}
            </button>
          </div>
        )}
      </form>
    </div>
  );
}
