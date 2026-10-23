import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Lối vào trang Tìm từ: ô giả ở thanh bên (máy tính), hoặc nút kính lúp (thanh trên điện thoại). */
export function SearchLauncher({ compact = false, className }: { compact?: boolean; className?: string }) {
  if (compact) {
    return (
      <Link
        to="/search"
        aria-label="Tìm từ"
        className={cn('rounded-xl p-2 text-muted-foreground hover:bg-muted hover:text-foreground', className)}
      >
        <Search className="h-5 w-5" strokeWidth={2.5} />
      </Link>
    )
  }
  return (
    <Link
      to="/search"
      className={cn(
        'flex items-center gap-3 rounded-2xl border-2 border-border bg-muted px-4 py-2.5 text-sm font-bold text-muted-foreground transition-colors hover:border-secondary/50 hover:text-foreground',
        className,
      )}
    >
      <Search className="h-5 w-5 shrink-0" strokeWidth={2.5} />
      <span className="flex-1">Tìm từ…</span>
      <kbd className="rounded-md border-2 border-border bg-card px-1.5 text-xs font-extrabold">/</kbd>
    </Link>
  )
}
