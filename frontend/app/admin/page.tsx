export default function AdminPage() {
  return (
    <div>
      <h1 className="text-3xl font-bold mb-4">Chào mừng Admin!</h1>
      <p className="text-gray-600">Đây là trang quản trị hệ thống OctaPoint.</p>
      <div className="mt-8 grid grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded shadow border">
          <h3 className="font-bold text-gray-500">Tổng số Tenant</h3>
          <p className="text-2xl font-bold mt-2">12</p>
        </div>
        <div className="bg-white p-4 rounded shadow border">
          <h3 className="font-bold text-gray-500">Giao dịch hôm nay</h3>
          <p className="text-2xl font-bold mt-2">1,240</p>
        </div>
        <div className="bg-white p-4 rounded shadow border">
          <h3 className="font-bold text-gray-500">Trạng thái Sui</h3>
          <p className="text-2xl font-bold mt-2 text-green-500">Connected</p>
        </div>
      </div>
    </div>
  );
}