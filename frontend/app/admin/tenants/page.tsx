'use client';

import { useState } from 'react';

// Mock data cho danh sách Tenant
const initialTenants = [
  { id: 'TNT-001', name: 'Siêu thị Co.opmart', email: 'contact@coopmart.vn', date: '2026-11-01', status: 'APPROVED' },
  { id: 'TNT-002', name: 'Chuỗi F&B Phúc Long', email: 'admin@phuclong.com', date: '2026-11-05', status: 'PENDING' },
  { id: 'TNT-003', name: 'Siêu thị Mega Market', email: 'info@mega.vn', date: '2026-11-08', status: 'PENDING' },
  { id: 'TNT-004', name: 'Nhà sách Fahasa', email: 'support@fahasa.com', date: '2026-10-20', status: 'LOCKED' },
];

export default function AdminTenantManagementPage() {
  const [tenants, setTenants] = useState(initialTenants);

  // Hàm xử lý thao tác Duyệt / Từ chối / Khóa
  const handleAction = (id: string, newStatus: string) => {
    setTenants(tenants.map(t => 
      t.id === id ? { ...t, status: newStatus } : t
    ));
    // Giả lập gọi API cập nhật trạng thái
    console.log(`Đã cập nhật Tenant ${id} sang trạng thái: ${newStatus}`);
  };

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Quản lý Tenant (Merchant)</h1>
        <p className="text-gray-500 mt-1">Xem danh sách, duyệt, từ chối hoặc khóa các tài khoản doanh nghiệp.</p>
      </div>

      {/* Bảng dữ liệu */}
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
            {tenants.map((tenant) => (
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
                      <button 
                        onClick={() => handleAction(tenant.id, 'APPROVED')}
                        className="bg-green-600 text-white px-3 py-1 rounded text-xs hover:bg-green-700"
                      >
                        Duyệt
                      </button>
                      <button 
                        onClick={() => handleAction(tenant.id, 'REJECTED')}
                        className="bg-red-600 text-white px-3 py-1 rounded text-xs hover:bg-red-700"
                      >
                        Từ chối
                      </button>
                    </>
                  )}
                  {tenant.status === 'APPROVED' && (
                    <button 
                      onClick={() => handleAction(tenant.id, 'LOCKED')}
                      className="bg-gray-600 text-white px-3 py-1 rounded text-xs hover:bg-gray-700"
                    >
                      Khóa
                    </button>
                  )}
                  {tenant.status === 'LOCKED' && (
                    <span className="text-gray-400 text-xs italic">Đã khóa</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}