import { create } from 'zustand';

interface AuthState {
  token: string | null;
  role: 'MERCHANT' | 'ADMIN' | null;
  login: (token: string, role: 'MERCHANT' | 'ADMIN') => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  role: null,
  login: (token, role) => set({ token, role }),
  logout: () => set({ token: null, role: null }),
}));