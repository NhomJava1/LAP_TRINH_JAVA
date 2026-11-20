'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/autoStore';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { role, hasHydrated, email, logout } = useAuthStore();
  const pathname = usePathname();
  const router = useRouter();

  if (!hasHydrated) {
    return (
      <div className="flex h-screen items-center justify-center text-slate-500">
        Đang kiểm tra phiên đăng nhập...
      </div>
    );
  }

  if (role !== 'ADMIN') {
    return (
      <div className="flex h-screen flex-col items-center justify-center gap-4 bg-slate-50 p-6 text-center">
        <p className="text-lg font-semibold text-red-600">
          Bạn không có quyền truy cập. Vui lòng đăng nhập bằng tài khoản System Admin.
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
    { href: '/admin', label: 'Dashboard & Settlement' },
    { href: '/admin/tenants', label: 'Quản lý Tenant' },
    { href: '/admin/audit-logs', label: 'Audit Logs' },
  ];

  const handleLogout = () => {
    logout();
    router.push('/login');
  };

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="w-64 bg-slate-900 text-white p-5 flex flex-col justify-between">
        <div>
          <div className="mb-6">
            <h2 className="font-bold text-xl text-emerald-400">OctaPoint Admin</h2>
            <p className="text-xs text-slate-400 mt-1 truncate">{email}</p>
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
                      ? 'bg-slate-800 text-white'
                      : 'text-slate-300 hover:bg-slate-800/70'
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
          className="w-full px-3 py-2 rounded-lg border border-slate-700 text-sm text-slate-300 hover:bg-slate-800 transition text-left"
        >
          Đăng xuất
        </button>
      </aside>

      <main className="flex-1 p-8 overflow-auto text-slate-900">{children}</main>
    </div>
  );
}
