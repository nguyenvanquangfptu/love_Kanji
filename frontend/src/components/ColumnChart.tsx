import { useState } from 'react'
import { cn } from '@/lib/utils'

export interface ColumnSeries {
  name: string
  /** Màu cột - đã kiểm tra bằng validator bảng màu (tương phản, phân biệt khi mù màu). */
  color: string
}

export interface ChartColumn {
  /** Nhãn ngắn trên trục X; rỗng thì bỏ trống (khi cột dày quá). */
  label: string
  /** Tiêu đề chú thích khi rê chuột/focus, vd. ngày đầy đủ. */
  heading: string
  /** Giá trị theo thứ tự {@code series}, chồng từ dưới lên; null = không có dữ liệu. */
  values: (number | null)[]
  /** Các dòng chú thích: giá trị trước, tên sau. */
  details: { value: string; name: string; color?: string }[]
}

/**
 * Biểu đồ cột (chồng được) dựng bằng HTML: cột tối đa 24px, đầu cột bo 4px, khe 2px màu nền giữa các phần chồng,
 * đường lưới mảnh, chú thích khi rê chuột hoặc focus bằng bàn phím. Giá trị vẫn đọc được không cần chú thích
 * (bảng số liệu đi kèm ở nơi dùng).
 */
export function ColumnChart({
  series,
  columns,
  max,
  ticks,
  formatTick,
  ariaLabel,
  markZero = false,
}: {
  series: ColumnSeries[]
  columns: ChartColumn[]
  max: number
  ticks: number[]
  formatTick: (value: number) => string
  ariaLabel: string
  /** Vẽ vạch mảnh ở đáy cho giá trị 0 có thật, để phân biệt với cột không có dữ liệu (null). */
  markZero?: boolean
}) {
  const [active, setActive] = useState<number | null>(null)
  const safeMax = max > 0 ? max : 1

  return (
    <div role="group" aria-label={ariaLabel}>
      <div className="relative ml-10 h-40">
        {ticks.map((tick) => (
          <div
            key={tick}
            className="absolute inset-x-0 border-t border-border"
            style={{ bottom: `${(tick / safeMax) * 100}%` }}
          >
            <span className="absolute -left-10 w-8 -translate-y-1/2 text-right text-[11px] font-semibold tabular-nums text-muted-foreground">
              {formatTick(tick)}
            </span>
          </div>
        ))}

        <div className="absolute inset-0 flex items-end">
          {columns.map((column, index) => {
            const total = column.values.reduce<number>((sum, value) => sum + (value ?? 0), 0)
            const topSegment = column.values.reduce<number>((top, value, i) => ((value ?? 0) > 0 ? i : top), -1)
            const zeroMark = markZero && total === 0 && column.values.some((value) => value !== null)
            return (
              <div
                key={index}
                tabIndex={0}
                aria-label={`${column.heading}: ${column.details.map((d) => `${d.name} ${d.value}`).join(', ')}`}
                onMouseEnter={() => setActive(index)}
                onMouseLeave={() => setActive(null)}
                onFocus={() => setActive(index)}
                onBlur={() => setActive(null)}
                className="flex h-full flex-1 cursor-default flex-col items-center justify-end rounded-md outline-none focus-visible:bg-muted"
              >
                {zeroMark && (
                  <div className="h-0.5 w-full max-w-6 rounded-full" style={{ backgroundColor: series[0].color }} />
                )}
                <div
                  className={cn('flex w-full max-w-6 flex-col-reverse transition-[filter]', active === index && 'brightness-110')}
                  style={{ height: `${Math.min(total / safeMax, 1) * 100}%` }}
                >
                  {column.values.map((value, i) =>
                    value ? (
                      <div
                        key={i}
                        className={cn('w-full', i === topSegment && 'rounded-t-[4px]', i > 0 && 'border-b-2 border-card')}
                        style={{ height: `${(value / total) * 100}%`, backgroundColor: series[i].color }}
                      />
                    ) : null,
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {active !== null && (
          <ChartTooltip column={columns[active]} index={active} count={columns.length} series={series} />
        )}
      </div>

      <div className="ml-10 mt-1.5 flex">
        {columns.map((column, index) => (
          <span key={index} className="flex-1 text-center text-[11px] font-semibold tabular-nums text-muted-foreground">
            {column.label}
          </span>
        ))}
      </div>
    </div>
  )
}

function ChartTooltip({
  column,
  index,
  count,
  series,
}: {
  column: ChartColumn
  index: number
  count: number
  series: ColumnSeries[]
}) {
  // Cột sát mép thì canh chú thích vào trong, khỏi tràn ra ngoài khung.
  const align = index < 2 ? 'translate-x-0' : index > count - 3 ? '-translate-x-full' : '-translate-x-1/2'
  return (
    <div
      className={cn(
        'pointer-events-none absolute top-0 z-10 min-w-36 rounded-xl border-2 border-border bg-card px-3 py-2 shadow-sm',
        align,
      )}
      style={{ left: `${((index + 0.5) / count) * 100}%` }}
    >
      <p className="text-xs font-bold text-muted-foreground">{column.heading}</p>
      {column.details.map((detail, i) => (
        <p key={i} className="flex items-center gap-2 text-sm">
          <span
            className="h-0.5 w-3 shrink-0 rounded-full"
            style={{ backgroundColor: detail.color ?? series[i]?.color ?? 'transparent' }}
          />
          <span className="font-black tabular-nums">{detail.value}</span>
          <span className="font-semibold text-muted-foreground">{detail.name}</span>
        </p>
      ))}
    </div>
  )
}

/** Chú thích chuỗi cho biểu đồ nhiều chuỗi: ô màu giống dạng cột, chữ màu thường. */
export function ChartLegend({ series }: { series: ColumnSeries[] }) {
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-1">
      {series.map((s) => (
        <span key={s.name} className="flex items-center gap-1.5 text-sm font-semibold text-muted-foreground">
          <span className="h-3 w-3 rounded-[3px]" style={{ backgroundColor: s.color }} />
          {s.name}
        </span>
      ))}
    </div>
  )
}
