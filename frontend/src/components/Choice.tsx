import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** Nút chọn một trong nhiều giá trị (cấp độ, số phút...), sáng lên khi đang được chọn. */
export function Choice({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'h-10 rounded-xl border-2 px-4 text-sm font-extrabold transition-colors',
        active ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border bg-card hover:bg-muted',
      )}
    >
      {children}
    </button>
  )
}
