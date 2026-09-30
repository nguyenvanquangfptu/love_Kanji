import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate, type Location } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
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

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, username })
      const from = (location.state as { from?: Location } | null)?.from?.pathname ?? '/study'
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
