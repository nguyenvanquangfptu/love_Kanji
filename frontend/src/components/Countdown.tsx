import { Clock } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Countdown({ seconds }: { seconds: number }) {
  const clamped = Math.max(0, seconds)
  const mm = Math.floor(clamped / 60)
    .toString()
    .padStart(2, '0')
  const ss = Math.floor(clamped % 60)
    .toString()
    .padStart(2, '0')
  const urgent = clamped <= 60

  return (
    <div
      role="timer"
      aria-label={`Còn ${mm} phút ${ss} giây`}
      className={cn(
        'flex shrink-0 items-center gap-1.5 rounded-xl border-2 px-3 py-1.5 text-base font-black tabular-nums',
        urgent ? 'animate-pulse border-destructive bg-destructive-soft text-destructive-dark' : 'border-border bg-card',
      )}
    >
      <Clock className="h-4 w-4" strokeWidth={3} />
      {mm}:{ss}
    </div>
  )
}
