'use client';

import { useState } from 'react';

export default function RuleConfigPage() {
  // Mặc định: 1000 VND = 1 Điểm (Đặt mặc định là 1000 thay vì 0)
  const [earnRate, setEarnRate] = useState<number>(1000);
  const [redeemRate, setRedeemRate] = useState<number>(10);
  const [isSaved, setIsSaved] = useState(false);

  const [testAmount, setTestAmount] = useState<number>(100000);
  
  // Tính điểm, chặn số âm
  const calculatedPoints = earnRate > 0 && testAmount > 0 ? Math.floor(testAmount / earnRate) : 0;

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    console.log('Lưu cấu hình:', { earnRate, redeemRate });
    setIsSaved(true);
    setTimeout(() => setIsSaved(false), 3000);
  };

  // Hàm chặn nhập số âm
  const handlePositiveChange = (setter: React.Dispatch<React.SetStateAction<number>>, value: string) => {
    const num = Number(value);
    if (num >= 0) setter(num); // Chỉ cho phép số >= 0
  };

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-800">Cấu hình Rule Tích & Đổi điểm</h1>
        <p className="text-gray-500 mt-2">
          Thiết lập tỷ lệ quy đổi điểm thưởng cho khách hàng của bạn. Các thay đổi sẽ chỉ áp dụng cho các giao dịch phát sinh sau thời điểm lưu.
        </p>
      </div>

      {isSaved && (
        <div className="mb-6 p-4 bg-green-50 border border-green-200 text-green-700 rounded-md flex items-center gap-2">
          <span>✅</span> Đã lưu cấu hình thành công!
        </div>
      )}

      <form onSubmit={handleSave} className="space-y-8 bg-white p-6 rounded-lg border shadow-sm">
        {/* Phần 1: Tích điểm */}
        <div className="border-b pb-6">
          <h2 className="text-lg font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <span className="text-blue-500">⬆️</span> Quy tắc Tích điểm (Earn)
          </h2>
          
          <div className="flex items-center gap-4 mb-4">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Số tiền chi tiêu (VND) để nhận 1 điểm
              </label>
              <input 
                type="number" 
                value={earnRate}
                onChange={(e) => handlePositiveChange(setEarnRate, e.target.value)}
                className="w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                required
                min="1"
              />
            </div>
            <div className="pt-6 font-bold text-gray-500">=</div>
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Số điểm được cộng
              </label>
              <input 
                type="text" 
                value="1 Điểm" 
                disabled 
                className="w-full p-2 border border-gray-200 bg-gray-50 rounded-md text-gray-500"
              />
            </div>
          </div>

          {/* CÔNG CỤ TÍNH TOÁN TRỰC QUAN */}
          <div className="mt-4 p-4 bg-blue-50 rounded-md border border-blue-100">
            <p className="text-sm font-medium text-blue-800 mb-2">🧮 Thử nghiệm quy đổi:</p>
            <div className="flex items-center gap-2">
              <input 
                type="number" 
                value={testAmount}
                onChange={(e) => handlePositiveChange(setTestAmount, e.target.value)}
                className="w-32 p-2 border border-blue-300 rounded-md text-sm"
                placeholder="Nhập số tiền..."
              />
              <span className="text-sm text-gray-700">VND</span>
              <span className="text-sm text-gray-700">➡️ Được cộng:</span>
              <span className="font-bold text-blue-700">{calculatedPoints} Điểm</span>
            </div>
          </div>
        </div>

        {/* Phần 2: Đổi điểm */}
        <div>
          <h2 className="text-lg font-semibold text-gray-700 mb-4 flex items-center gap-2">
            <span className="text-green-500">⬇️</span> Quy tắc Đổi điểm (Redeem)
          </h2>
          <div className="flex items-center gap-4">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Số điểm sử dụng
              </label>
              <input 
                type="text" 
                value="1 Điểm" 
                disabled 
                className="w-full p-2 border border-gray-200 bg-gray-50 rounded-md text-gray-500"
              />
            </div>
            <div className="pt-6 font-bold text-gray-500">=</div>
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Giá trị quy đổi (VND)
              </label>
              <input 
                type="number" 
                value={redeemRate}
                onChange={(e) => handlePositiveChange(setRedeemRate, e.target.value)}
                className="w-full p-2 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500"
                required
                min="1"
              />
            </div>
          </div>
          <p className="text-sm text-green-600 font-medium mt-2">
            * Với tỷ lệ này, khách dùng 30 điểm sẽ được giảm {30 * redeemRate} VND.
          </p>
        </div>

        <div className="pt-6 border-t flex justify-end">
          <button 
            type="submit" 
            className="bg-black text-white px-6 py-2 rounded-md hover:bg-gray-800 transition-colors font-medium"
          >
            Lưu Cấu Hình
          </button>
        </div>
      </form>
    </div>
  );
}