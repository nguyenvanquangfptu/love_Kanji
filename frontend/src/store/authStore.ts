import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/**
 * Đọc claim "role" trực tiếp từ access token (JWT) - không verify chữ ký,
 * chỉ dùng để ẩn/hiện UI. Việc phân quyền thật sự luôn nằm ở Backend
 * (@PreAuthorize), token giả mạo/sửa role vẫn bị Backend từ chối.
 */
function decodeRole(accessToken: string): string | null {
  try {
    const payloadB64 = accessToken.split('.')[1]
    const normalized = payloadB64.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    const payload = JSON.parse(atob(padded)) as { role?: string }
    return payload.role ?? null
  } catch {
    return null
  }
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  username: string | null
  role: string | null
  isAuthenticated: () => boolean
  isAdmin: () => boolean
  setSession: (session: { accessToken: string; refreshToken: string; username: string }) => void
  setTokens: (tokens: { accessToken: string; refreshToken: string }) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      username: null,
      role: null,
      isAuthenticated: () => get().accessToken !== null,
      isAdmin: () => get().role === 'ROLE_ADMIN',
      setSession: ({ accessToken, refreshToken, username }) =>
        set({ accessToken, refreshToken, username, role: decodeRole(accessToken) }),
      setTokens: ({ accessToken, refreshToken }) =>
        set({ accessToken, refreshToken, role: decodeRole(accessToken) }),
      clearSession: () => set({ accessToken: null, refreshToken: null, username: null, role: null }),
    }),
    { name: 'kanji-mastery-auth' },
  ),
)
