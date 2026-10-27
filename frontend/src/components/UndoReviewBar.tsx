import { Undo2 } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Lần chấm thẻ vừa rồi và nút hoàn tác (phím tắt Ctrl+Z / ⌘Z) - để sửa khi bấm nhầm mức chấm. */
export function UndoReviewBar({
  word,
  ratingLabel,
  pending,
  onUndo,
  className,
}: {
  word: string
  ratingLabel: string
  pending: boolean
  onUndo: () => void
  className?: string
}) {
  return (
    <div
      className={cn(
        'flex items-center gap-3 rounded-2xl border-2 border-border bg-card px-4 py-2 text-sm font-semibold',
        className,
      )}
    >
      <p className="min-w-0 flex-1 truncate text-muted-foreground">
        Vừa chấm <span className="font-jp font-bold text-foreground">{word}</span>:{' '}
        <span className="font-extrabold text-foreground">{ratingLabel}</span>
      </p>
      <span className="hidden text-xs font-bold text-muted-foreground sm:inline">Ctrl+Z</span>
      <button
        type="button"
        disabled={pending}
        onClick={onUndo}
        className="inline-flex shrink-0 items-center gap-1.5 rounded-xl px-2 py-1 font-extrabold text-secondary-dark transition-colors hover:bg-secondary-soft disabled:opacity-60 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40"
      >
        <Undo2 className="h-4 w-4" strokeWidth={2.5} />
        {pending ? 'Đang hoàn tác...' : 'Hoàn tác'}
      </button>
    </div>
  )
}
