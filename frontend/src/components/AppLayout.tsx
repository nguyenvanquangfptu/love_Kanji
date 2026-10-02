import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { BookOpen, ClipboardCheck, LogOut, RotateCcw, ShieldCheck, TrendingUp, type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/store/authStore'
import { authApi } from '@/api/auth'
import { Logo } from '@/components/Logo'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  iconColor: string
}

const NAV_ITEMS: NavItem[] = [
  { to: '/study', label: 'Học bài', icon: BookOpen, iconColor: 'text-primary' },
  { to: '/flashcards', label: 'Ôn tập', icon: RotateCcw, iconColor: 'text-secondary' },
  { to: '/exam', label: 'Thi thử', icon: ClipboardCheck, iconColor: 'text-orange' },
  { to: '/progress', label: 'Tiến bộ', icon: TrendingUp, iconColor: 'text-accent-dark' },
]

/** Số cột của thanh tab dưới đáy theo số mục (thêm mục Quản trị cho admin). */
const TAB_COLUMNS: Record<number, string> = { 4: 'grid-cols-4', 5: 'grid-cols-5' }

const ADMIN_ITEM: NavItem = { to: '/admin/kanji', label: 'Quản trị', icon: ShieldCheck, iconColor: 'text-purple' }

function useLogout() {
  const navigate = useNavigate()
  const refreshToken = useAuthStore((s) => s.refreshToken)
  const clearSession = useAuthStore((s) => s.clearSession)

  return async function logout() {
    try {
      if (refreshToken) await authApi.logout(refreshToken)
    } catch {
      // logout best-effort: dù API lỗi vẫn xoá session cục bộ
    } finally {
      clearSession()
      navigate('/login', { replace: true })
    }
  }
}

function Avatar({ name }: { name: string }) {
  return (
    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-secondary bg-secondary-soft text-base font-black uppercase text-secondary-dark">
      {name.charAt(0)}
    </span>
  )
}

export function AppLayout() {
  const username = useAuthStore((s) => s.username) ?? ''
  const isAdmin = useAuthStore((s) => s.isAdmin())
  const logout = useLogout()
  const items = isAdmin ? [...NAV_ITEMS, ADMIN_ITEM] : NAV_ITEMS

  return (
    <div className="min-h-screen bg-background lg:pl-64">
      {/* Máy tính: thanh bên trái */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r-2 border-border bg-card px-4 py-6 lg:flex">
        <Link to="/study" className="px-3">
          <Logo />
        </Link>
        <nav className="mt-8 flex flex-col gap-1.5">
          {items.map(({ to, label, icon: Icon, iconColor }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-4 rounded-2xl border-2 px-4 py-3 text-[15px] font-extrabold transition-colors',
                  isActive
                    ? 'border-secondary/50 bg-secondary-soft text-secondary-dark'
                    : 'border-transparent text-muted-foreground hover:bg-muted hover:text-foreground',
                )
              }
            >
              <Icon className={cn('h-7 w-7', iconColor)} strokeWidth={2.5} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto flex items-center gap-3 rounded-2xl border-2 border-border p-3">
          <Avatar name={username} />
          <span className="flex-1 truncate font-extrabold">{username}</span>
          <button
            type="button"
            onClick={logout}
            aria-label="Đăng xuất"
            title="Đăng xuất"
            className="rounded-xl p-2 text-muted-foreground hover:bg-muted hover:text-destructive"
          >
            <LogOut className="h-5 w-5" />
          </button>
        </div>
      </aside>

      {/* Điện thoại: thanh trên cùng */}
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b-2 border-border bg-card/95 px-4 backdrop-blur lg:hidden">
        <Link to="/study">
          <Logo />
        </Link>
        <div className="flex items-center gap-2">
          <Avatar name={username} />
          <button
            type="button"
            onClick={logout}
            aria-label="Đăng xuất"
            className="rounded-xl p-2 text-muted-foreground hover:bg-muted hover:text-destructive"
          >
            <LogOut className="h-5 w-5" />
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-4xl px-4 pb-32 pt-6 sm:px-6 lg:px-10 lg:pb-12 lg:pt-10">
        <Outlet />
      </main>

      {/* Điện thoại: thanh tab dưới đáy */}
      <nav className="pb-safe fixed inset-x-0 bottom-0 z-30 border-t-2 border-border bg-card lg:hidden">
        <div className={cn('mx-auto grid max-w-md px-2', TAB_COLUMNS[items.length] ?? 'grid-cols-4')}>
          {items.map(({ to, label, icon: Icon, iconColor }) => (
            <NavLink key={to} to={to} className="flex flex-col items-center gap-0.5 py-2">
              {({ isActive }) => (
                <>
                  <span
                    className={cn(
                      'flex h-10 w-14 items-center justify-center rounded-2xl border-2 transition-colors',
                      isActive ? 'border-secondary/50 bg-secondary-soft' : 'border-transparent',
                    )}
                  >
                    <Icon className={cn('h-6 w-6', iconColor)} strokeWidth={2.5} />
                  </span>
                  <span
                    className={cn('text-[11px] font-extrabold', isActive ? 'text-secondary-dark' : 'text-muted-foreground')}
                  >
                    {label}
                  </span>
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}

/** Bố cục cho phiên học/thi: ẩn toàn bộ menu để người học tập trung. */
export function FocusLayout() {
  return (
    <div className="min-h-screen bg-background">
      <Outlet />
    </div>
  )
}
