'use client';
import Link from 'next/link';
import { useAuthStore } from '@/store/autoStore';

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const role = useAuthStore((state) => state.role);
  
  // Chặn nếu không phải ADMIN
  if (role !== 'ADMIN') {
    return (
      <div className="flex h-screen items-center justify-center text-red-500 text-xl font-semibold">
        Bạn không có quyền truy cập. Vui lòng đăng nhập bằng tài khoản Admin.
      </div>
    );
  }

  return (
    <div className="flex h-screen bg-slate-50">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-900 text-white p-4">
        <h2 className="font-bold text-xl mb-6 text-green-400">Admin Panel</h2>
        <nav className="flex flex-col gap-2">
          <Link href="/admin" className="p-2 hover:bg-slate-800 rounded transition">Dashboard</Link>
          <Link href="/admin/tenants" className="p-2 hover:bg-slate-800 rounded transition">Tenant Management</Link>
        </nav>
      </aside>
      
      {/* Main Content */}
      <main className="flex-1 p-8 text-gray-800">
        {children}
      </main>
    </div>
  );
}