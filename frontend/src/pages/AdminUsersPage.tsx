import { type FormEvent, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronLeft, ChevronRight, Copy, UserPlus } from 'lucide-react'
import { adminUsersApi } from '@/api/adminUsers'
import { extractErrorMessage } from '@/api/client'
import type { CreatedUser } from '@/api/types'
import { PageHeader } from '@/components/PageHeader'
import { AdminNav } from '@/components/AdminNav'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { PageSpinner } from '@/components/ui/spinner'

const USERS_KEY = ['admin', 'users']

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short' })

/**
 * Cấp tài khoản người học khi tắt tự đăng ký: nhập tên đăng nhập và email, máy chủ sinh mật khẩu tạm - chỉ hiện một lần
 * ở đây để gửi cho người học; người học phải đổi ở lần đăng nhập đầu. Bên dưới là danh sách tài khoản, mới tạo trước.
 */
export function AdminUsersPage() {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [created, setCreated] = useState<CreatedUser | null>(null)
  const [copied, setCopied] = useState(false)

  const users = useQuery({
    queryKey: [...USERS_KEY, page],
    queryFn: () => adminUsersApi.list(page),
    placeholderData: keepPreviousData,
  })

  const create = useMutation({
    mutationFn: adminUsersApi.create,
    onSuccess: (data) => {
      setCreated(data)
      setCopied(false)
      setUsername('')
      setEmail('')
      setPage(0)
      queryClient.invalidateQueries({ queryKey: USERS_KEY })
    },
  })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    create.mutate({ username: username.trim(), email: email.trim() })
  }

  async function copyPassword(password: string) {
    try {
      await navigator.clipboard.writeText(password)
      setCopied(true)
    } catch {
      // Trình duyệt chặn clipboard (không phải HTTPS, chưa cho phép): mật khẩu vẫn hiện để chép tay.
      setCopied(false)
    }
  }

  return (
    <div>
      <AdminNav />
      <PageHeader
        title="Tài khoản"
        subtitle="Cấp tài khoản người học khi tắt tự đăng ký. Tài khoản cấp ở đây luôn là người học."
      />

      <Card className="mb-6 p-4 sm:p-5">
        <h2 className="mb-3 flex items-center gap-2 font-black">
          <UserPlus className="h-5 w-5 text-purple-dark" strokeWidth={2.5} /> Cấp tài khoản mới
        </h2>
        <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="new-username">Tên đăng nhập</Label>
            <Input
              id="new-username"
              autoComplete="off"
              minLength={3}
              maxLength={50}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="new-email">Email</Label>
            <Input
              id="new-email"
              type="email"
              autoComplete="off"
              maxLength={100}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <Button type="submit" disabled={create.isPending}>
            {create.isPending ? 'Đang tạo...' : 'Tạo tài khoản'}
          </Button>
        </form>
        {create.isError && <Alert className="mt-3">{extractErrorMessage(create.error)}</Alert>}

        {created && (
          <div
            role="status"
            className="mt-4 rounded-2xl border-2 border-success/30 bg-success-soft p-4 text-sm font-semibold text-success-dark"
          >
            <p>
              Đã tạo tài khoản <strong>{created.user.username}</strong>. Gửi mật khẩu tạm này cho người học - nó{' '}
              <strong>chỉ hiện một lần</strong>, người học phải đổi ở lần đăng nhập đầu.
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <code className="rounded-xl border-2 border-success/30 bg-card px-3 py-1.5 font-mono text-base text-foreground">
                {created.temporaryPassword}
              </code>
              <Button size="sm" variant="outline" onClick={() => copyPassword(created.temporaryPassword)}>
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                {copied ? 'Đã chép' : 'Chép'}
              </Button>
              <Button size="sm" variant="outline" onClick={() => setCreated(null)}>
                Đã gửi xong, ẩn đi
              </Button>
            </div>
          </div>
        )}
      </Card>

      {users.isLoading && <PageSpinner />}
      {users.isError && <Alert>{extractErrorMessage(users.error)}</Alert>}
      {users.data && (
        <>
          <p className="mb-2 text-sm font-semibold text-muted-foreground">{users.data.totalElements} tài khoản</p>
          <Card className="divide-y-2 divide-border overflow-hidden">
            {users.data.content.map((user) => (
              <div key={user.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-extrabold">{user.username}</span>
                  <span className="block truncate text-sm font-semibold text-muted-foreground">{user.email}</span>
                </span>
                {user.mustChangePassword && <Badge variant="accent">Chưa đổi mật khẩu tạm</Badge>}
                <Badge variant={user.role === 'ROLE_ADMIN' ? 'purple' : 'secondary'}>
                  {user.role === 'ROLE_ADMIN' ? 'Quản trị viên' : 'Người học'}
                </Badge>
                <span className="w-20 text-right text-xs font-bold text-muted-foreground">
                  {dateFormat.format(new Date(user.createdAt))}
                </span>
              </div>
            ))}
          </Card>
          {users.data.totalPages > 1 && (
            <div className="mt-3 flex items-center justify-center gap-3">
              <Button size="sm" variant="outline" disabled={users.data.first} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft className="h-4 w-4" /> Trước
              </Button>
              <span className="text-sm font-bold text-muted-foreground">
                Trang {users.data.number + 1}/{users.data.totalPages}
              </span>
              <Button size="sm" variant="outline" disabled={users.data.last} onClick={() => setPage((p) => p + 1)}>
                Sau <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
