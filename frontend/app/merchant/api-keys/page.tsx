'use client';

import { useState, useEffect } from 'react';
import { useAuthStore } from '@/store/autoStore';

interface ApiKey {
  id: string;
  name: string;
  createdAt: string;
  status: string;
}

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showSecret, setShowSecret] = useState(false);
  const [secretKey, setSecretKey] = useState('');
  
  // Lấy token từ Zustand để gửi kèm request
  const token = useAuthStore((state) => state.token);

  // Hàm gọi API lấy danh sách Key
  useEffect(() => {
    const fetchKeys = async () => {
      try {
        const response = await fetch('http://localhost:8080/api/v1/merchants/api-keys', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token || 'test-token'}` 
          }
        });
        
        if (!response.ok) throw new Error('Không thể lấy danh sách API Key');
        
        const data = await response.json();
        setKeys(data);
      } catch (err: any) {
        // Nếu Backend chưa có dữ liệu, hiển thị mảng rỗng
        setKeys([]); 
      } finally {
        setIsLoading(false);
      }
    };

    fetchKeys();
  }, [token]);

  const handleCreateKey = async () => {
    const newKey = `sk_live_${Math.random().toString(36).substring(2, 15)}`;
    setSecretKey(newKey);
    setShowSecret(true);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(secretKey);
    alert('Đã copy Secret Key!');
  };

  if (isLoading) return <div className="p-8">Đang tải dữ liệu...</div>;

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">Quản lý API Key</h1>
          <p className="text-gray-500 mt-1">Tạo và quản lý API Key của bạn để tích hợp dịch vụ.</p>
        </div>
        <button onClick={handleCreateKey} className="bg-black text-white px-4 py-2 rounded-md hover:bg-gray-800">
          + Tạo API Key
        </button>
      </div>

      {/* Popup hiển thị Secret 1 lần */}
      {showSecret && (
        <div className="bg-yellow-50 border border-yellow-200 p-4 rounded-md mb-6">
          <h3 className="font-bold text-yellow-800 mb-2">⚠️ Cảnh báo: Hãy lưu Secret Key này ngay bây giờ!</h3>
          <p className="text-sm text-yellow-700 mb-3">Bạn sẽ không thể xem lại Secret Key này sau khi đóng cửa sổ.</p>
          <div className="flex gap-2">
            <input type="text" value={secretKey} readOnly className="flex-1 p-2 border rounded bg-white" />
            <button onClick={handleCopy} className="bg-blue-600 text-white px-4 py-2 rounded">Sao chép</button>
            <button onClick={() => setShowSecret(false)} className="bg-gray-300 text-gray-800 px-4 py-2 rounded">Đóng</button>
          </div>
        </div>
      )}

      {/* Bảng danh sách Key */}
      <div className="bg-white border rounded-lg overflow-hidden shadow-sm">
        <table className="w-full text-left">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="p-4 font-semibold text-gray-600">Tên Key</th>
              <th className="p-4 font-semibold text-gray-600">Ngày tạo</th>
              <th className="p-4 font-semibold text-gray-600">Trạng thái</th>
              <th className="p-4 font-semibold text-gray-600">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {keys.length > 0 ? keys.map((key) => (
              <tr key={key.id} className="border-b hover:bg-gray-50">
                <td className="p-4">{key.name}</td>
                <td className="p-4">{key.createdAt}</td>
                <td className="p-4">
                  <span className={`px-2 py-1 rounded text-sm ${key.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                    {key.status}
                  </span>
                </td>
                <td className="p-4 flex gap-2">
                  <button className="text-red-600 hover:underline">Thu hồi</button>
                  <button className="text-blue-600 hover:underline">Xem</button>
                </td>
              </tr>
            )) : (
              <tr>
                <td colSpan={4} className="p-8 text-center text-gray-500">Chưa có API Key nào.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}