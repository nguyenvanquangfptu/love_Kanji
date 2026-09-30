import { cn } from '@/lib/utils'

export function Progress({
  value,
  className,
  barClassName = 'bg-primary',
}: {
  /** 0 - 100 */
  value: number
  className?: string
  barClassName?: string
}) {
  const clamped = Math.min(100, Math.max(0, value))
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(clamped)}
      className={cn('h-4 w-full overflow-hidden rounded-full bg-border', className)}
    >
      <div
        className={cn('flex h-full items-start rounded-full pt-[3px] transition-all duration-500', barClassName)}
        style={{ width: `${clamped}%` }}
      >
        {clamped > 6 && <span className="mx-2 h-1 w-full rounded-full bg-white/35" />}
      </div>
    </div>
  )
}
