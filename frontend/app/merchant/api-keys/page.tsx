'use client';
import { useState } from 'react';

export default function ApiKeysPage() {
  const [showSecret, setShowSecret] = useState(false);
  const [secretKey, setSecretKey] = useState('');

  const handleCreateKey = () => {
    // Giả lập tạo key mới
    const newKey = `sk_live_${Math.random().toString(36).substring(2, 15)}`;
    setSecretKey(newKey);
    setShowSecret(true);
  };

  const handleCopy = () => {
    navigator.clipboard.writeText(secretKey);
    alert('Đã copy Secret Key!');
  };

  return (
    <div className="p-8">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">Quản lý API Key</h1>
          <p className="text-gray-500">Tạo và quản lý API Key của bạn để tích hợp dịch vụ.</p>
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
      <div className="bg-white border rounded-lg overflow-hidden">
        <table className="w-full text-left">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="p-4 font-semibold">Tên Key</th>
              <th className="p-4 font-semibold">Ngày tạo</th>
              <th className="p-4 font-semibold">Trạng thái</th>
              <th className="p-4 font-semibold">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr className="border-b hover:bg-gray-50">
              <td className="p-4">Merchant Key 1</td>
              <td className="p-4">27/10/2026</td>
              <td className="p-4"><span className="bg-green-100 text-green-700 px-2 py-1 rounded text-sm">Hoạt động</span></td>
              <td className="p-4 flex gap-2">
                <button className="text-red-600 hover:underline">Thu hồi</button>
                <button className="text-blue-600 hover:underline">Xem</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}