'use client';

import { useState } from 'react';

// Mock data cho lịch sử giao dịch
const mockTransactions = [
  { id: 'TXN-001', customer: 'Nguyễn Văn A', type: 'EARN', points: '+100', date: '2026-11-10 14:30', status: 'SETTLED' },
  { id: 'TXN-002', customer: 'Trần Thị B', type: 'REDEEM', points: '-30', date: '2026-11-10 15:45', status: 'SETTLED' },
  { id: 'TXN-003', customer: 'Lê Văn C', type: 'EARN', points: '+50', date: '2026-11-11 09:15', status: 'PENDING' },
  { id: 'TXN-004', customer: 'Phạm Thị D', type: 'REDEEM', points: '-20', date: '2026-11-11 10:00', status: 'FAILED' },
];

export default function TransactionHistoryPage() {
  const [searchTerm, setSearchTerm] = useState('');

  // Lọc dữ liệu theo ô tìm kiếm
  const filteredData = mockTransactions.filter(txn => 
    txn.customer.toLowerCase().includes(searchTerm.toLowerCase()) || 
    txn.id.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Lịch sử giao dịch</h1>
        <p className="text-gray-500 mt-1">Xem lại toàn bộ các giao dịch tích điểm và đổi điểm của khách hàng.</p>
      </div>

      {/* Bộ lọc */}
      <div className="flex gap-4 mb-6 bg-white p-4 rounded-lg border shadow-sm">
        <div className="flex-1">
          <input 
            type="text" 
            placeholder="Tìm kiếm theo Mã GD hoặc Tên khách hàng..." 
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500 text-sm"
          />
        </div>
        <button className="bg-gray-100 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-200 text-sm font-medium">
          Lọc theo ngày
        </button>
      </div>

      {/* Bảng dữ liệu */}
      <div className="bg-white border rounded-lg overflow-hidden shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              <th className="p-4 font-semibold text-gray-600">Mã GD</th>
              <th className="p-4 font-semibold text-gray-600">Khách hàng</th>
              <th className="p-4 font-semibold text-gray-600">Loại</th>
              <th className="p-4 font-semibold text-gray-600">Số điểm</th>
              <th className="p-4 font-semibold text-gray-600">Thời gian</th>
              <th className="p-4 font-semibold text-gray-600">Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {filteredData.length > 0 ? (
              filteredData.map((txn) => (
                <tr key={txn.id} className="border-b hover:bg-gray-50">
                  <td className="p-4 font-medium text-blue-600">{txn.id}</td>
                  <td className="p-4">{txn.customer}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${txn.type === 'EARN' ? 'bg-blue-100 text-blue-700' : 'bg-purple-100 text-purple-700'}`}>
                      {txn.type === 'EARN' ? 'Tích điểm' : 'Đổi điểm'}
                    </span>
                  </td>
                  <td className={`p-4 font-bold ${txn.type === 'EARN' ? 'text-green-600' : 'text-red-600'}`}>
                    {txn.points}
                  </td>
                  <td className="p-4 text-gray-500">{txn.date}</td>
                  <td className="p-4">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${
                      txn.status === 'SETTLED' ? 'bg-green-100 text-green-700' : 
                      txn.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' : 'bg-red-100 text-red-700'
                    }`}>
                      {txn.status}
                    </span>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={6} className="p-8 text-center text-gray-500">Không tìm thấy giao dịch nào.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
      
      {/* Phân trang đơn giản */}
      <div className="mt-4 flex justify-between items-center text-sm text-gray-500">
        <span>Hiển thị {filteredData.length} giao dịch</span>
        <div className="flex gap-2">
          <button className="px-3 py-1 border rounded hover:bg-gray-50 disabled:opacity-50" disabled>Trước</button>
          <button className="px-3 py-1 border rounded hover:bg-gray-50">Sau</button>
        </div>
      </div>
    </div>
  );
}