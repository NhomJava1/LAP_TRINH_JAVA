'use client';
import Link from 'next/link';
import { useAuthStore } from '@/store/autoStore';

export default function MerchantLayout({ children }: { children: React.ReactNode }) {
  const role = useAuthStore((state) => state.role);
  if (role !== 'MERCHANT') return <div className="p-10 text-red-500">Vui lòng đăng nhập bằng tài khoản Merchant.</div>;

  return (
    <div className="flex h-screen bg-gray-50">
      <aside className="w-64 bg-white border-r p-4">
        <h2 className="font-bold text-xl mb-6">Merchant Portal</h2>
        <nav className="flex flex-col gap-2">
          <Link href="/merchant" className="p-2 hover:bg-gray-100 rounded">Dashboard</Link>
          <Link href="/merchant/api-keys" className="p-2 hover:bg-gray-100 rounded">Quản lý API Key</Link>
          <Link href="/merchant/rule-config" className="p-2 hover:bg-gray-100 rounded">Cấu hình Rule</Link>
        </nav>
      </aside>
      <main className="flex-1 p-8">{children}</main>
    </div>
  );
} 
