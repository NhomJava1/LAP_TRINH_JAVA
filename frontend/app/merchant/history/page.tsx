'use client';

import { useState, useEffect } from 'react';
import { useAuthStore } from '@/store/autoStore';

interface Transaction {
  id: string;
  customer: string;
  type: string;
  points: number;
  date: string;
  status: string;
}

export default function TransactionHistoryPage() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const token = useAuthStore((state) => state.token);

  useEffect(() => {
    const fetchHistory = async () => {
      try {
        // Giả sử Backend có API này, nếu chưa có thì nó sẽ trả về lỗi và hiển thị mảng rỗng
        const response = await fetch('http://localhost:8080/api/v1/credits/history', {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (response.ok) {
          const data = await response.json();
          setTransactions(data || []);
        }
      } catch (err) {
        console.error("Lỗi lấy lịch sử:", err);
        setTransactions([]);
      } finally {
        setIsLoading(false);
      }
    };
    fetchHistory();
  }, [token]);

  // Lọc dữ liệu theo ô tìm kiếm
  const filteredData = transactions.filter(txn => 
    txn.customer?.toLowerCase().includes(searchTerm.toLowerCase()) || 
    txn.id?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-800">Lịch sử giao dịch</h1>
        <p className="text-gray-500 mt-1">Xem lại toàn bộ các giao dịch tích điểm và đổi điểm của khách hàng.</p>
      </div>

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
      </div>

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
            {isLoading ? (
              <tr><td colSpan={6} className="p-8 text-center text-gray-500">Đang tải dữ liệu...</td></tr>
            ) : filteredData.length > 0 ? (
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
                    {txn.type === 'EARN' ? '+' : '-'}{txn.points}
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
              <tr><td colSpan={6} className="p-8 text-center text-gray-500">Chưa có giao dịch nào.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}