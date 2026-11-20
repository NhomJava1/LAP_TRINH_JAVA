'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';

export default function RegisterPage() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const router = useRouter();

  const handleRegister = (e: React.FormEvent) => {
    e.preventDefault();
    // Giả lập gọi API Đăng ký
    if (name && email && password) {
      alert('Đăng ký thành công! Vui lòng đăng nhập.');
      router.push('/login');
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <form onSubmit={handleRegister} className="p-8 bg-white border rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-6 text-center text-gray-800">Đăng ký tài khoản</h1>
        
        <input 
          type="text" placeholder="Họ và tên" 
          className="w-full p-2 border mb-3 rounded" 
          onChange={(e) => setName(e.target.value)} required 
        />
        <input 
          type="email" placeholder="Email" 
          className="w-full p-2 border mb-3 rounded" 
          onChange={(e) => setEmail(e.target.value)} required 
        />
        <input 
          type="password" placeholder="Mật khẩu" 
          className="w-full p-2 border mb-3 rounded" 
          onChange={(e) => setPassword(e.target.value)} required 
        />
        
        <button type="submit" className="w-full bg-green-600 text-white p-2 rounded hover:bg-green-700 mb-4">
          Đăng ký
        </button>
        
        <p className="text-center text-sm text-gray-600">
          Đã có tài khoản? <Link href="/login" className="text-blue-600 hover:underline">Đăng nhập ngay</Link>
        </p>
      </form>
    </div>
  );
}