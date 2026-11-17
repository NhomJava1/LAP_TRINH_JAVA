'use client';

import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export interface AuthProfile {
  email?: string | null;
  name?: string | null;
  tenantId?: string | null;
  tenantName?: string | null;
  tenantStatus?: string | null;
}

interface AuthState {
  token: string | null;
  role: 'MERCHANT' | 'ADMIN' | 'STAFF' | null;
  email: string | null;
  name: string | null;
  tenantId: string | null;
  tenantName: string | null;
  tenantStatus: string | null;
  hasHydrated: boolean;
  setHasHydrated: (value: boolean) => void;
  login: (token: string, role: 'MERCHANT' | 'ADMIN' | 'STAFF', profile?: AuthProfile) => void;
  updateTenantStatus: (status: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      role: null,
      email: null,
      name: null,
      tenantId: null,
      tenantName: null,
      tenantStatus: null,
      hasHydrated: false,
      setHasHydrated: (value) => set({ hasHydrated: value }),
      login: (token, role, profile) =>
        set({
          token,
          role,
          email: profile?.email ?? null,
          name: profile?.name ?? null,
          tenantId: profile?.tenantId ?? null,
          tenantName: profile?.tenantName ?? null,
          tenantStatus: profile?.tenantStatus ?? null,
        }),
      updateTenantStatus: (tenantStatus) => set({ tenantStatus }),
      logout: () =>
        set({
          token: null,
          role: null,
          email: null,
          name: null,
          tenantId: null,
          tenantName: null,
          tenantStatus: null,
        }),
    }),
    {
      name: 'octapoint-auth',
      storage: createJSONStorage(() => localStorage),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    },
  ),
);
