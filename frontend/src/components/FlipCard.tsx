import { type ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function FlipCard({
  flipped,
  front,
  back,
  onClick,
}: {
  flipped: boolean
  front: ReactNode
  back: ReactNode
  onClick: () => void
}) {
  return (
    <div
      className="cursor-pointer select-none [perspective:1500px] focus-visible:outline-none"
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onClick()
      }}
      role="button"
      tabIndex={0}
      aria-label={flipped ? 'Lật về mặt trước' : 'Lật thẻ'}
    >
      <div
        className={cn(
          'relative h-80 w-full rounded-3xl transition-transform duration-500 [transform-style:preserve-3d] sm:h-96',
          flipped && '[transform:rotateY(180deg)]',
        )}
      >
        <div className="absolute inset-0 flex flex-col items-center justify-center rounded-3xl border-2 border-b-[6px] border-border bg-card p-6 [backface-visibility:hidden]">
          {front}
        </div>
        <div className="absolute inset-0 flex flex-col items-center justify-center rounded-3xl border-2 border-b-[6px] border-secondary bg-card p-6 [backface-visibility:hidden] [transform:rotateY(180deg)]">
          {back}
        </div>
      </div>
    </div>
  )
}
