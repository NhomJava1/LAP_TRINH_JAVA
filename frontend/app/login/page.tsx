'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { apiFetch } from '@/lib/api';
import { useAuthStore } from '@/store/autoStore';

interface LoginResponse {
  token: string;
  role: 'MERCHANT' | 'ADMIN' | 'STAFF';
  email: string;
  name: string;
  tenantId: string | null;
  tenantName: string | null;
  tenantStatus: string | null;
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const data = await apiFetch<LoginResponse>('/api/portal/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });

      login(data.token, data.role, {
        email: data.email,
        name: data.name,
        tenantId: data.tenantId,
        tenantName: data.tenantName,
        tenantStatus: data.tenantStatus,
      });

      if (data.role === 'ADMIN') {
        router.push('/admin');
      } else {
        router.push('/merchant');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng nhập thất bại');
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
          <h1 className="text-2xl font-bold text-slate-900 mt-2">Đăng nhập Portal</h1>
          <p className="text-sm text-slate-500 mt-1">
            Sử dụng tài khoản Merchant hoặc System Admin
          </p>
        </div>

        {error && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@company.com"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Mật khẩu</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full px-3 py-2 border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              required
            />
          </div>
          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-blue-600 text-white py-2.5 rounded-lg font-medium hover:bg-blue-700 disabled:opacity-60 transition"
          >
            {submitting ? 'Đang đăng nhập...' : 'Đăng nhập'}
          </button>
        </form>

        <div className="mt-5 rounded-lg bg-slate-50 border border-slate-200 p-3 text-xs text-slate-600 space-y-1">
          <div className="font-semibold text-slate-700">Tài khoản Admin mặc định (Demo):</div>
          <div>Email: <code className="font-mono text-slate-900">admin@octapoint.io</code></div>
          <div>Mật khẩu: <code className="font-mono text-slate-900">admin123</code></div>
        </div>

        <p className="text-center text-sm text-slate-600 mt-5">
          Chưa có tài khoản Merchant?{' '}
          <Link href="/register" className="text-blue-600 font-medium hover:underline">
            Đăng ký ngay
          </Link>
        </p>
      </div>
    </div>
  );
}
