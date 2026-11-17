'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/autoStore';

export default function MerchantLayout({ children }: { children: React.ReactNode }) {
  const { role, hasHydrated, tenantName, tenantStatus, email, logout } = useAuthStore();
  const pathname = usePathname();
  const router = useRouter();

  if (!hasHydrated) {
    return (
      <div className="flex h-screen items-center justify-center text-slate-500">
        Đang kiểm tra phiên đăng nhập...
      </div>
    );
  }

  if (role !== 'MERCHANT') {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-slate-50 p-6 text-center">
        <p className="text-lg font-semibold text-red-600">
          Vui lòng đăng nhập bằng tài khoản Merchant để truy cập cổng này.
        </p>
        <Link
          href="/login"
          className="px-4 py-2 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700"
        >
          Đến trang Đăng nhập
        </Link>
      </div>
    );
  }

  const navItems = [
    { href: '/merchant', label: 'Tổng quan' },
    { href: '/merchant/api-keys', label: 'Quản lý API Key' },
    { href: '/merchant/rule-config', label: 'Cấu hình Rule' },
    { href: '/merchant/history', label: 'Lịch sử & Số dư' },
  ];

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="w-64 bg-white border-r border-slate-200 p-5 flex flex-col justify-between">
        <div>
          <div className="mb-6">
            <div className="font-bold text-xl text-slate-900">OctaPoint Merchant</div>
            <div className="text-xs text-slate-500 mt-1 truncate">{tenantName || email}</div>
            {tenantStatus && (
              <span
                className={`inline-block mt-2 px-2 py-0.5 rounded text-xs font-semibold ${
                  tenantStatus === 'APPROVED'
                    ? 'bg-emerald-100 text-emerald-700'
                    : tenantStatus === 'PENDING'
                      ? 'bg-amber-100 text-amber-800'
                      : 'bg-red-100 text-red-700'
                }`}
              >
                {tenantStatus}
              </span>
            )}
          </div>

          <nav className="flex flex-col gap-1.5">
            {navItems.map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`px-3 py-2 rounded-lg text-sm font-medium transition ${
                    active
                      ? 'bg-blue-50 text-blue-700'
                      : 'text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </div>

        <button
          onClick={handleLogout}
          className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600 hover:bg-slate-100 transition text-left"
        >
          Đăng xuất
        </button>
      </aside>
      <main className="flex-1 p-8 overflow-auto">{children}</main>
    </div>
  );
}
