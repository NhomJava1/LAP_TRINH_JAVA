'use client';

import { useState, useEffect } from 'react';
import { useAuthStore } from '@/store/autoStore';

export default function MerchantPage() {
  const [balance, setBalance] = useState<number>(0);
  const [isLoading, setIsLoading] = useState(true);
  
  // Lấy token từ Zustand để gửi kèm request
  const token = useAuthStore((state) => state.token);

  useEffect(() => {
    const fetchBalance = async () => {
      try {
        // Gọi API thật từ Backend
        const response = await fetch('http://localhost:8080/api/v1/credits/balance?customerId=C1', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token || 'test-token'}` 
          }
        });
        
        if (response.ok) {
          const data = await response.json();
          setBalance(data.balance || 0);
        }
      } catch (err) {
        console.error("Lỗi khi lấy số dư:", err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchBalance();
  }, [token]);

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <h1 className="text-2xl font-bold mb-6 text-gray-800">Chào mừng Merchant đến với Dashboard!</h1>
      
      {/* Thẻ hiển thị số dư */}
      <div className="bg-white p-6 rounded-lg shadow-sm border inline-block min-w-[250px]">
        <h2 className="text-sm font-medium text-gray-500 mb-2">Số dư hiện tại</h2>
        {isLoading ? (
          <p className="text-gray-400">Đang tải...</p>
        ) : (
          <p className="text-4xl font-bold text-blue-600">{balance} <span className="text-lg text-gray-500 font-normal">Điểm</span></p>
        )}
      </div>
    </div>
  );
}