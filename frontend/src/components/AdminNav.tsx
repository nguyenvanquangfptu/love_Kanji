import { NavLink } from 'react-router-dom'
import { BookOpenText, FileCheck2, Languages } from 'lucide-react'
import { cn } from '@/lib/utils'

const ITEMS = [
  { to: '/admin/kanji', label: 'Từ vựng', icon: Languages },
  { to: '/admin/grammar', label: 'Ngữ pháp', icon: BookOpenText },
  { to: '/admin/questions', label: 'Câu hỏi thi', icon: FileCheck2 },
]

/** Chuyển giữa các trang quản trị. */
export function AdminNav() {
  return (
    <nav aria-label="Quản trị" className="no-scrollbar -mx-4 mb-6 overflow-x-auto px-4 sm:mx-0 sm:px-0">
      <div className="flex gap-2">
        {ITEMS.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              cn(
                'flex shrink-0 items-center gap-2 rounded-2xl border-2 px-4 py-2 text-sm font-extrabold transition-colors',
                isActive ? 'border-purple bg-purple-soft text-purple-dark' : 'border-border bg-card hover:bg-muted',
              )
            }
          >
            <Icon className="h-4 w-4" strokeWidth={2.5} />
            {label}
          </NavLink>
        ))}
      </div>
    </nav>
  )
}
