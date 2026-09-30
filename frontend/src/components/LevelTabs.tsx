import type { JlptLevel } from '@/api/types'
import { LEVEL_META, OTHER_STYLE } from '@/lib/levels'
import { cn } from '@/lib/utils'

export type LevelKey = JlptLevel | 'OTHER'

export function LevelTabs({
  levels,
  active,
  onChange,
  lessonCounts,
}: {
  levels: LevelKey[]
  active: LevelKey
  onChange: (level: LevelKey) => void
  lessonCounts: Record<LevelKey, number>
}) {
  return (
    <div className="no-scrollbar -mx-4 overflow-x-auto px-4 pb-1 sm:mx-0 sm:overflow-visible sm:px-0">
      <div
        role="tablist"
        aria-label="Cấp độ JLPT"
        className={cn('flex gap-2 sm:grid', levels.length === 6 ? 'sm:grid-cols-6' : 'sm:grid-cols-5')}
      >
        {levels.map((key) => {
          const style = key === 'OTHER' ? OTHER_STYLE : LEVEL_META[key].style
          const isActive = key === active
          return (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={isActive}
              onClick={() => onChange(key)}
              className={cn(
                'flex min-w-[76px] shrink-0 flex-col items-center rounded-2xl border-2 border-b-4 px-4 py-2.5 transition-all active:translate-y-[2px] active:border-b-2',
                isActive ? style.solid : 'border-border bg-card text-foreground hover:bg-muted',
              )}
            >
              <span className="text-lg font-black">{key === 'OTHER' ? 'Khác' : key}</span>
              <span className={cn('text-[11px] font-bold', isActive ? 'text-white/90' : 'text-muted-foreground')}>
                {lessonCounts[key]} bài
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
