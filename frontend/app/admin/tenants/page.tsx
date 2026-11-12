'use client';

import { useState, useEffect } from 'react';
import { useAuthStore } from '@/store/autoStore';

interface Tenant {
  id: string;
  name: string;
  email: string;
  date: string;
  status: string;
}

export default function AdminTenantManagementPage() {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const token = useAuthStore((state) => state.token);

  useEffect(() => {
    const fetchTenants = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/v1/admin/tenants', {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (response.ok) {
          const data = await response.json();
          setTenants(data || []);
        }
      } catch (err) {
        console.error("Lỗi lấy danh sách tenant:", err);
        setTenants([]);
      } finally {
        setIsLoading(false);
      }
    };
    fetchTenants();
  }, [token]);

  const handleAction = (id: string, newStatus: string) => {
    setTenants(tenants.map(t => t.id === id ? { ...t, status: newStatus } : t));
    // Gọi API cập nhật trạng thái (nếu có)
  };

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Quản lý Tenant (Merchant)</h1>
      </div>

      <div className="bg-white border rounded-lg overflow-hidden shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="p-4 font-semibold text-gray-600">Mã Tenant</th>
              <th className="p-4 font-semibold text-gray-600">Tên Doanh nghiệp</th>
              <th className="p-4 font-semibold text-gray-600">Email</th>
              <th className="p-4 font-semibold text-gray-600">Ngày đăng ký</th>
              <th className="p-4 font-semibold text-gray-600">Trạng thái</th>
              <th className="p-4 font-semibold text-gray-600 text-center">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={6} className="p-8 text-center text-gray-500">Đang tải dữ liệu...</td></tr>
            ) : tenants.length > 0 ? (
              tenants.map((tenant) => (
                <tr key={tenant.id} className="border-b hover:bg-gray-50">
                  <td className="p-4 font-medium text-blue-600">{tenant.id}</td>
                  <td className="p-4 font-medium text-gray-800">{tenant.name}</td>
                  <td className="p-4 text-gray-500">{tenant.email}</td>
                  <td className="p-4 text-gray-500">{tenant.date}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      tenant.status === 'APPROVED' ? 'bg-green-100 text-green-700' : 
                      tenant.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' : 'bg-red-100 text-red-700'
                    }`}>
                      {tenant.status}
                    </span>
                  </td>
                  <td className="p-4 flex justify-center gap-2">
                    {tenant.status === 'PENDING' && (
                      <>
                        <button onClick={() => handleAction(tenant.id, 'APPROVED')} className="bg-green-600 text-white px-3 py-1 rounded text-xs">Duyệt</button>
                        <button onClick={() => handleAction(tenant.id, 'REJECTED')} className="bg-red-600 text-white px-3 py-1 rounded text-xs">Từ chối</button>
                      </>
                    )}
                  </td>
                </tr>
              ))
            ) : (
              <tr><td colSpan={6} className="p-8 text-center text-gray-500">Chưa có Tenant nào.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}