import { cn } from '@/lib/utils'

export function QuestionPalette({
  total,
  currentIndex,
  isAnswered,
  onSelect,
}: {
  total: number
  currentIndex: number
  isAnswered: (index: number) => boolean
  onSelect: (index: number) => void
}) {
  return (
    <div className="grid grid-cols-6 gap-2 sm:grid-cols-8 lg:grid-cols-5">
      {Array.from({ length: total }, (_, i) => {
        const answered = isAnswered(i)
        const current = i === currentIndex
        return (
          <button
            key={i}
            type="button"
            onClick={() => onSelect(i)}
            aria-label={`Câu ${i + 1}${answered ? ' (đã làm)' : ''}`}
            aria-current={current ? 'step' : undefined}
            className={cn(
              'flex h-10 items-center justify-center rounded-xl border-2 border-b-4 text-sm font-black transition-all active:translate-y-[2px] active:border-b-2',
              answered ? 'border-secondary-dark bg-secondary text-white' : 'border-border bg-card text-muted-foreground hover:bg-muted',
              current && 'ring-4 ring-accent',
            )}
          >
            {i + 1}
          </button>
        )
      })}
    </div>
  )
}
