import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { extractErrorMessage } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { AuthShell, PasswordInput } from '@/components/AuthShell'

export function RegisterPage() {
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const navigate = useNavigate()
  const setSession = useAuthStore((s) => s.setSession)

  const registerMutation = useMutation({
    mutationFn: authApi.register,
    onSuccess: (data) => {
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, username })
      navigate('/study', { replace: true })
    },
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    registerMutation.mutate({ username, email, password })
  }

  return (
    <AuthShell
      title="Bắt đầu học Kanji"
      subtitle="Tạo tài khoản để lưu lại tiến độ học của bạn"
      footer={
        <>
          Đã có tài khoản?{' '}
          <Link to="/login" className="font-extrabold text-secondary hover:underline">
            Đăng nhập
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        {registerMutation.isError && <Alert>{extractErrorMessage(registerMutation.error)}</Alert>}
        <Label htmlFor="username" className="sr-only">
          Tên đăng nhập
        </Label>
        <Input
          id="username"
          placeholder="Tên đăng nhập (3-50 ký tự)"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoFocus
          required
          minLength={3}
          maxLength={50}
        />
        <Label htmlFor="email" className="sr-only">
          Email
        </Label>
        <Input
          id="email"
          type="email"
          placeholder="Email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
        <Label htmlFor="password" className="sr-only">
          Mật khẩu
        </Label>
        <PasswordInput
          id="password"
          placeholder="Mật khẩu (tối thiểu 8 ký tự)"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          minLength={8}
        />
        <Button type="submit" size="lg" disabled={registerMutation.isPending} className="mt-2 w-full">
          {registerMutation.isPending ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'}
        </Button>
      </form>
    </AuthShell>
  )
}
