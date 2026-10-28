import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'

export function ProtectedRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated())
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword)
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  // Tài khoản còn mật khẩu tạm do quản trị viên cấp: chỉ vào được trang đổi mật khẩu. Chỉ chặn ở giao diện - API vẫn
  // mở, nhưng người cầm mật khẩu tạm (đã gửi qua tin nhắn) nên được đổi sớm nhất có thể.
  if (mustChangePassword && location.pathname !== '/account') {
    return <Navigate to="/account" replace />
  }

  return <Outlet />
}
