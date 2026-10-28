import { type FormEvent, useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, type Location } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { extractErrorMessage } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { AuthShell, PasswordInput } from '@/components/AuthShell'

export function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const setSession = useAuthStore((s) => s.setSession)
  const clearSession = useAuthStore((s) => s.clearSession)
  const queryClient = useQueryClient()
  // from: trang định vào trước khi bị đưa về đăng nhập; notice: lời nhắn sau khi đổi mật khẩu;
  // signedOut: máy chủ đã thu hồi phiên (đổi mật khẩu), xoá phiên trong trình duyệt khi đã sang trang này.
  const state = location.state as { from?: Location; notice?: string; signedOut?: boolean } | null
  const signedOut = state?.signedOut === true

  useEffect(() => {
    if (signedOut) {
      clearSession()
      queryClient.clear()
    }
  }, [signedOut, clearSession, queryClient])

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      setSession({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        username,
        mustChangePassword: data.mustChangePassword,
      })
      // Còn mật khẩu tạm do quản trị viên cấp thì đặt mật khẩu mới trước đã.
      const from = data.mustChangePassword ? '/account' : (state?.from?.pathname ?? '/study')
      navigate(from, { replace: true })
    },
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    loginMutation.mutate({ username, password })
  }

  return (
    <AuthShell
      title="Chào mừng trở lại!"
      subtitle="Đăng nhập để tiếp tục chinh phục Kanji & JLPT"
      footer={
        <>
          Chưa có tài khoản?{' '}
          <Link to="/register" className="font-extrabold text-secondary hover:underline">
            Đăng ký ngay
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        {state?.notice && !loginMutation.isError && (
          <p role="status" className="rounded-2xl border-2 border-success/30 bg-success-soft px-4 py-3 text-sm font-semibold text-success-dark">
            {state.notice}
          </p>
        )}
        {loginMutation.isError && <Alert>{extractErrorMessage(loginMutation.error)}</Alert>}
        <Label htmlFor="username" className="sr-only">
          Tên đăng nhập
        </Label>
        <Input
          id="username"
          placeholder="Tên đăng nhập"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoFocus
          required
        />
        <Label htmlFor="password" className="sr-only">
          Mật khẩu
        </Label>
        <PasswordInput
          id="password"
          placeholder="Mật khẩu"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        <Button type="submit" size="lg" disabled={loginMutation.isPending} className="mt-2 w-full">
          {loginMutation.isPending ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </Button>
      </form>
    </AuthShell>
  )
}
