import { formatDay } from '@/lib/dates'
import { cn } from '@/lib/utils'

/** Đậm dần theo số lần trả lời so với ngày học nhiều nhất trong 12 tuần. */
const LEVELS = ['bg-muted', 'bg-primary/25', 'bg-primary/50', 'bg-primary/75', 'bg-primary']

/**
 * Lịch nhiệt 12 tuần: mỗi cột một tuần (thứ Hai ở trên), mỗi ô một ngày học, càng đậm là ngày đó trả lời càng nhiều.
 */
export function StudyHeatmap({ days }: { days: { day: string; answers: number }[] }) {
  if (days.length === 0) return null
  const max = Math.max(1, ...days.map((d) => d.answers))
  // Ngày đầu tiên rơi vào thứ mấy (thứ Hai = 0) - đệm ô trống để mỗi hàng là một thứ trong tuần.
  const firstWeekday = (new Date(`${days[0].day}T00:00:00`).getDay() + 6) % 7
  const studied = days.filter((d) => d.answers > 0).length

  return (
    <div className="w-fit max-w-full">
      <div
        className="grid auto-cols-max grid-flow-col grid-rows-7 justify-start gap-1 overflow-x-auto"
        role="img"
        aria-label={`Lịch học 12 tuần: có học ${studied}/${days.length} ngày`}
      >
        {Array.from({ length: firstWeekday }, (_, i) => (
          <span key={`pad-${i}`} />
        ))}
        {days.map(({ day, answers }) => (
          <span
            key={day}
            title={`${formatDay(day)}: ${answers > 0 ? `${answers} lần trả lời` : 'không học'}`}
            className={cn(
              'h-3.5 w-3.5 rounded-[3px] sm:h-4 sm:w-4',
              LEVELS[answers === 0 ? 0 : Math.ceil((answers / max) * (LEVELS.length - 1))],
            )}
          />
        ))}
      </div>
      <div className="mt-2 flex items-center justify-end gap-1 text-xs font-semibold text-muted-foreground">
        Ít
        {LEVELS.map((level) => (
          <span key={level} className={cn('h-3 w-3 rounded-[3px]', level)} />
        ))}
        Nhiều
      </div>
    </div>
  )
}
