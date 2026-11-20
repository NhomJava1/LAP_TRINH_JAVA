'use client';

import { useState } from 'react';
import Link from 'next/link';
import { apiFetch } from '@/lib/api';

interface RegisterResponse {
  merchantId: string;
  tenantId: string;
  businessName: string;
  email: string;
  status: string;
}

export default function RegisterPage() {
  const [businessName, setBusinessName] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<RegisterResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const res = await apiFetch<RegisterResponse>('/api/portal/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          businessName,
          name: name || businessName,
          email,
          phone,
          password,
        }),
      });
      setCreated(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng ký thất bại');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 p-4">
      <div className="w-full max-w-md bg-white border border-slate-200 rounded-xl shadow-sm p-8">
        <div className="mb-6 text-center">
          <Link href="/" className="text-sm font-semibold text-blue-600 hover:underline">
            ← Về trang chủ OctaPoint
          </Link>
          <h1 className="text-2xl font-bold text-slate-900 mt-2">Đăng ký Merchant</h1>
          <p className="text-sm text-slate-500 mt-1">
            Tạo tài khoản doanh nghiệp mới trên OctaPoint
          </p>
        </div>

        {created ? (
          <div className="space-y-4">
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900 space-y-2">
              <div className="font-semibold">Đăng ký thành công!</div>
              <p>
                Doanh nghiệp <strong>{created.businessName}</strong> (Mã:{' '}
                <code className="font-mono">{created.tenantId}</code>) đã được tạo với trạng thái{' '}
                <span className="inline-block px-2 py-0.5 rounded bg-amber-100 text-amber-800 font-semibold text-xs">
                  {created.status}
                </span>
                .
              </p>
              <p className="text-xs text-emerald-800">
                Vui lòng chờ Admin duyệt trước khi tạo API Key và thực hiện giao dịch điểm.
              </p>
            </div>
            <Link
              href="/login"
              className="block w-full text-center bg-blue-600 text-white py-2.5 rounded-lg font-medium hover:bg-blue-700 transition"
            >
              Chuyển đến trang Đăng nhập
            </Link>
          </div>
        ) : (
          <>
            {error && (
              <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {error}
              </div>
            )}

            <form onSubmit={handleRegister} className="space-y-3.5">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Tên doanh nghiệp / Cửa hàng
                </label>
                <input
                  type="text"
                  value={businessName}
                  onChange={(e) => setBusinessName(e.target.value)}
                  placeholder="Siêu thị A"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Họ và tên người đại diện
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Nguyễn Văn A"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="merchant@sieuthia.vn"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                  required
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Số điện thoại
                </label>
                <input
                  type="text"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="0901234567"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Mật khẩu</label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg"
                  required
                />
              </div>
              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-emerald-600 text-white py-2.5 rounded-lg font-medium hover:bg-emerald-700 disabled:opacity-60 transition"
              >
                {submitting ? 'Đang tạo tài khoản...' : 'Đăng ký Merchant'}
              </button>
            </form>

            <p className="text-center text-sm text-slate-600 mt-5">
              Đã có tài khoản?{' '}
              <Link href="/login" className="text-blue-600 font-medium hover:underline">
                Đăng nhập ngay
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
}
