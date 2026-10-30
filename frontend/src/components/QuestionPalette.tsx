import { cn } from '@/lib/utils'

/**
 * Ô chọn câu; {@code from}/{@code count} để vẽ riêng các câu của một 問題 (số câu vẫn đánh theo cả bài). Câu đầu tiên
 * mang số {@code firstNumber} (phần đánh số tiếp theo phần trước, vd. Đọc hiểu N3 từ câu 24).
 */
export function QuestionPalette({
  total,
  currentIndex,
  isAnswered,
  onSelect,
  from = 0,
  count = total - from,
  firstNumber = 1,
}: {
  total: number
  currentIndex: number
  isAnswered: (index: number) => boolean
  onSelect: (index: number) => void
  from?: number
  count?: number
  firstNumber?: number
}) {
  return (
    <div className="grid grid-cols-6 gap-2 sm:grid-cols-8 lg:grid-cols-5">
      {Array.from({ length: Math.min(count, total - from) }, (_, offset) => {
        const i = from + offset
        const answered = isAnswered(i)
        const current = i === currentIndex
        return (
          <button
            key={i}
            type="button"
            onClick={() => onSelect(i)}
            aria-label={`Câu ${firstNumber + i}${answered ? ' (đã làm)' : ''}`}
            aria-current={current ? 'step' : undefined}
            className={cn(
              'flex h-10 items-center justify-center rounded-xl border-2 border-b-4 text-sm font-black transition-all active:translate-y-[2px] active:border-b-2',
              answered ? 'border-secondary-edge bg-secondary text-white' : 'border-border bg-card text-muted-foreground hover:bg-muted',
              current && 'ring-4 ring-accent',
            )}
          >
            {firstNumber + i}
          </button>
        )
      })}
    </div>
  )
}
