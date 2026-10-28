import { type FormEvent, type ReactNode, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Check, Circle, KeyRound, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { authApi } from '@/api/auth'
import { extractErrorMessage } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { PageHeader } from '@/components/PageHeader'
import { PasswordInput } from '@/components/AuthShell'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'

const MIN_PASSWORD_LENGTH = 8

/** Lỗi nhập liệu thấy được ngay trên trình duyệt; giới hạn 72 byte của BCrypt để máy chủ báo. */
function validate(oldPassword: string, newPassword: string, confirm: string): string | null {
  if (newPassword.length < MIN_PASSWORD_LENGTH) return `Mật khẩu mới phải có ít nhất ${MIN_PASSWORD_LENGTH} ký tự.`
  if (newPassword === oldPassword) return 'Mật khẩu mới phải khác mật khẩu hiện tại.'
  if (newPassword !== confirm) return 'Hai lần nhập mật khẩu mới không khớp.'
  return null
}

/** Từng yêu cầu hiện ngay khi gõ: xanh khi đạt, đỏ khi đã gõ mà chưa đạt, xám khi chưa gõ gì. */
function Requirement({ met, touched, children }: { met: boolean; touched: boolean; children: ReactNode }) {
  const Icon = met ? Check : touched ? X : Circle
  return (
    <li
      className={cn(
        'flex items-center gap-2',
        met ? 'text-success-dark' : touched ? 'text-destructive' : 'text-muted-foreground',
      )}
    >
      <Icon className="h-4 w-4 shrink-0" strokeWidth={3} />
      {children}
    </li>
  )
}

/**
 * Tài khoản: tên đăng nhập và đổi mật khẩu. Đổi xong máy chủ đăng xuất mọi phiên (kể cả phiên này) nên quay về trang
 * đăng nhập. Tài khoản quản trị viên cấp còn mật khẩu tạm thì chỉ vào được trang này cho tới khi đổi xong.
 */
export function AccountPage() {
  const navigate = useNavigate()
  const username = useAuthStore((s) => s.username)
  const isAdmin = useAuthStore((s) => s.isAdmin())
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword)

  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const change = useMutation({
    mutationFn: authApi.changePassword,
    onSuccess: () => {
      // Không xoá phiên ở đây: React Router chuyển trang ở mức ưu tiên thấp, nên xoá phiên trước thì ProtectedRoute
      // tự đưa về /login và ghi đè mất lời nhắn. signedOut báo trang đăng nhập xoá phiên (máy chủ đã thu hồi token).
      navigate('/login', {
        replace: true,
        state: { notice: 'Đã đổi mật khẩu. Hãy đăng nhập lại bằng mật khẩu mới.', signedOut: true },
      })
    },
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const error = validate(oldPassword, newPassword, confirm)
    setFormError(error)
    if (!error) change.mutate({ oldPassword, newPassword })
  }

  return (
    <div className="mx-auto max-w-lg">
      <PageHeader title="Tài khoản" />

      <Card className="mb-5 flex items-center gap-3 p-4">
        <span className="min-w-0 flex-1">
          <span className="block text-xs font-extrabold uppercase tracking-wider text-muted-foreground">
            Tên đăng nhập
          </span>
          <span className="block truncate text-lg font-black">{username}</span>
        </span>
        <Badge variant={isAdmin ? 'purple' : 'secondary'}>{isAdmin ? 'Quản trị viên' : 'Người học'}</Badge>
      </Card>

      <Card className="p-4 sm:p-5">
        <h2 className="mb-1 flex items-center gap-2 text-lg font-black">
          <KeyRound className="h-5 w-5 text-secondary" strokeWidth={2.5} /> Đổi mật khẩu
        </h2>
        <p className="mb-4 text-sm font-semibold text-muted-foreground">
          Đổi xong bạn sẽ được đăng xuất khỏi mọi thiết bị và cần đăng nhập lại bằng mật khẩu mới.
        </p>

        {mustChangePassword && (
          <Alert className="mb-4">
            Tài khoản này được cấp với mật khẩu tạm. Hãy đặt mật khẩu của riêng bạn để bắt đầu học.
          </Alert>
        )}

        <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
          {(formError ?? change.isError) && <Alert>{formError ?? extractErrorMessage(change.error)}</Alert>}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="old-password">{mustChangePassword ? 'Mật khẩu tạm' : 'Mật khẩu hiện tại'}</Label>
            <PasswordInput
              id="old-password"
              autoComplete="current-password"
              value={oldPassword}
              onChange={(e) => setOldPassword(e.target.value)}
              required
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="new-password">Mật khẩu mới (ít nhất {MIN_PASSWORD_LENGTH} ký tự)</Label>
            <PasswordInput
              id="new-password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="confirm-password">Nhập lại mật khẩu mới</Label>
            <PasswordInput
              id="confirm-password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required
            />
          </div>

          <ul aria-live="polite" className="flex flex-col gap-1 text-sm font-bold">
            <Requirement met={newPassword.length >= MIN_PASSWORD_LENGTH} touched={newPassword.length > 0}>
              Ít nhất {MIN_PASSWORD_LENGTH} ký tự ({newPassword.length}/{MIN_PASSWORD_LENGTH})
            </Requirement>
            <Requirement
              met={newPassword.length > 0 && newPassword !== oldPassword}
              touched={newPassword.length > 0}
            >
              Khác mật khẩu hiện tại
            </Requirement>
            <Requirement met={confirm.length > 0 && confirm === newPassword} touched={confirm.length > 0}>
              Hai lần nhập mật khẩu mới khớp nhau
            </Requirement>
          </ul>

          <Button type="submit" disabled={change.isPending || !oldPassword} className="mt-2 w-full">
            {change.isPending ? 'Đang đổi mật khẩu...' : 'Đổi mật khẩu'}
          </Button>
        </form>
      </Card>
    </div>
  )
}
