'use client';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/store/autoStore';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const router = useRouter();
  const login = useAuthStore((state) => state.login);

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    if (email && password) {
      const mockToken = "fake-jwt-token-123";
      const mockRole = email.includes('admin') ? 'ADMIN' : 'MERCHANT';
      login(mockToken, mockRole);
      
      if (mockRole === 'ADMIN') router.push('/admin');
      else router.push('/merchant');
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center">
      <form onSubmit={handleLogin} className="p-8 border rounded-lg shadow-md w-96">
        <h1 className="text-2xl font-bold mb-4 text-center">Đăng nhập</h1>
        <input type="email" placeholder="Email" className="w-full p-2 border mb-3 rounded" onChange={(e) => setEmail(e.target.value)} required />
        <input type="password" placeholder="Mật khẩu" className="w-full p-2 border mb-3 rounded" onChange={(e) => setPassword(e.target.value)} required />
        <button type="submit" className="w-full bg-blue-600 text-white p-2 rounded">Đăng nhập</button>
      </form>
    </div>
  );
}