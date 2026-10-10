import { type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BookPlus, Brain, LineChart, RotateCcw, Target } from 'lucide-react'
import { progressApi } from '@/api/progress'
import { extractErrorMessage } from '@/api/client'
import type { ProgressResponse, QuizDirection } from '@/api/types'
import { formatDay } from '@/lib/dates'
import { cn } from '@/lib/utils'
import { ChartLegend, ColumnChart, type ChartColumn, type ColumnSeries } from '@/components/ColumnChart'
import { PageHeader } from '@/components/PageHeader'
import { StatTile } from '@/components/StatTile'
import { EmptyState } from '@/components/EmptyState'
import { Meter } from '@/components/Meter'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

// Đã chạy validator bảng màu trên nền trắng: xanh dương và cam tách nhau rõ cả khi mù màu đỏ-lục; cam dưới 3:1 nên
// biểu đồ hai chuỗi luôn có chú thích chuỗi và bảng số liệu.
const BLUE = 'var(--color-secondary-dark)'
const ORANGE = 'var(--color-orange-dark)'

const RETENTION_SERIES: ColumnSeries[] = [{ name: 'nhớ được', color: BLUE }]
const ACTIVITY_SERIES: ColumnSeries[] = [
  { name: 'Ôn & luyện', color: BLUE },
  { name: 'Từ mới', color: ORANGE },
]

const DIRECTION_LABELS: Record<QuizDirection, string> = {
  KANJI_TO_READING: 'Chọn cách đọc',
  READING_TO_KANJI: 'Chọn cách viết',
  MEANING: 'Chọn nghĩa',
  TYPE_READING: 'Gõ cách đọc',
}

const percent = (part: number, whole: number) => Math.round((part / whole) * 100)
/** 06/12 từ ngày ISO. */
const shortDay = (iso: string) => formatDay(iso).slice(0, 5)

export function ProgressPage() {
  const navigate = useNavigate()
  const { data, isLoading, isError, error } = useQuery({ queryKey: ['progress'], queryFn: progressApi.get })

  return (
    <div>
      <PageHeader
        title="Tiến bộ của tôi"
        subtitle="Bạn nhớ được bao nhiêu, học đều tới đâu, và hay nhầm ở chỗ nào."
        action={
          <Button variant="outline" onClick={() => navigate('/goals')}>
            <Target className="h-5 w-5" /> Mục tiêu học
          </Button>
        }
      />
      {isLoading && <PageSpinner />}
      {isError && <Alert>{extractErrorMessage(error)}</Alert>}
      {data && <ProgressBody progress={data} />}
    </div>
  )
}

function ProgressBody({ progress }: { progress: ProgressResponse }) {
  const navigate = useNavigate()
  const recentWeeks = progress.weeks.slice(-4)
  const reviews4w = recentWeeks.reduce((sum, w) => sum + w.reviews, 0)
  const remembered4w = recentWeeks.reduce((sum, w) => sum + w.remembered, 0)
  const reviews14d = progress.days.reduce((sum, d) => sum + d.reviews, 0)
  const newWords14d = progress.days.reduce((sum, d) => sum + d.newWords, 0)
  const nothingYet =
    reviews14d + newWords14d === 0 && progress.weeks.every((w) => w.reviews === 0) && progress.directions.length === 0

  if (nothingYet) {
    return (
      <EmptyState
        icon={LineChart}
        title="Chưa có gì để thống kê"
        description="Ôn tập và làm trắc nghiệm vài ngày, tiến bộ của bạn sẽ hiện ở đây."
        action={
          <Button size="lg" onClick={() => navigate('/flashcards')}>
            <RotateCcw className="h-5 w-5" /> Ôn tập
          </Button>
        }
      />
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <div>
        <div className="grid grid-cols-3 gap-3">
          <StatTile
            label="Nhớ được"
            value={reviews4w > 0 ? `${percent(remembered4w, reviews4w)}%` : '–'}
            tone="secondary"
            icon={Brain}
          />
          <StatTile label="Lượt ôn" value={reviews14d} tone="purple" icon={RotateCcw} />
          <StatTile label="Từ mới" value={newWords14d} tone="orange" icon={BookPlus} />
        </div>
        <p className="mt-2 text-xs font-semibold text-muted-foreground">
          Tỉ lệ nhớ tính trên 4 tuần gần nhất; lượt ôn và từ mới trên 14 ngày gần nhất.
        </p>
      </div>

      <RetentionCard weeks={progress.weeks} />
      {progress.calibration && <CalibrationCard calibration={progress.calibration} />}
      <ActivityCard days={progress.days} />
      {progress.directions.length > 0 && <DirectionCard directions={progress.directions} />}
      {progress.confusions.length > 0 && <ConfusionCard confusions={progress.confusions} />}
    </div>
  )
}

function ChartCard({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <Card className="p-4 sm:p-5">
      <h2 className="text-lg font-black">{title}</h2>
      <p className="mb-4 text-sm font-semibold text-muted-foreground">{subtitle}</p>
      {children}
    </Card>
  )
}

/** Bảng số liệu đi kèm biểu đồ - đọc được mọi giá trị không cần rê chuột. */
function DataTable({ head, rows }: { head: string[]; rows: (string | number)[][] }) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer font-bold text-secondary">Xem số liệu</summary>
      <table className="mt-2 w-full text-left">
        <thead>
          <tr className="border-b-2 border-border text-muted-foreground">
            {head.map((cell) => (
              <th key={cell} className="py-1 pr-3 font-bold">
                {cell}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="border-b border-border last:border-0">
              {row.map((cell, j) => (
                <td key={j} className={cn('py-1 pr-3 font-semibold', j > 0 && 'tabular-nums')}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}

function RetentionCard({ weeks }: { weeks: ProgressResponse['weeks'] }) {
  const columns: ChartColumn[] = weeks.map((week) => {
    const rate = week.reviews > 0 ? percent(week.remembered, week.reviews) : null
    return {
      label: shortDay(week.weekStart),
      heading: `Tuần từ ${formatDay(week.weekStart)}`,
      values: [rate],
      details:
        rate === null
          ? [{ value: '–', name: 'chưa có lần ôn đến hạn' }]
          : [{ value: `${rate}%`, name: `nhớ được (${week.remembered}/${week.reviews} lần)` }],
    }
  })
  return (
    <ChartCard
      title="Tỉ lệ nhớ theo tuần"
      subtitle="Trong các lần ôn đúng hạn từ đã thuộc, bạn nhớ ra được bao nhiêu phần trăm. Khoảng 85-90% là nhịp ôn vừa phải."
    >
      <ColumnChart
        series={RETENTION_SERIES}
        columns={columns}
        max={100}
        ticks={[0, 50, 100]}
        formatTick={(tick) => `${tick}%`}
        ariaLabel="Tỉ lệ nhớ theo tuần, 8 tuần gần nhất"
        markZero
      />
      <DataTable
        head={['Tuần từ', 'Nhớ được', 'Số lần ôn']}
        rows={weeks.map((week) => [
          formatDay(week.weekStart),
          week.reviews > 0 ? `${percent(week.remembered, week.reviews)}%` : '–',
          week.reviews,
        ])}
      />
    </ChartCard>
  )
}

/** Mốc trục Y gọn: 5, 10, 20, 50, 100... không nhỏ hơn giá trị lớn nhất. */
function niceMax(value: number) {
  if (value <= 5) return 5
  const magnitude = 10 ** Math.floor(Math.log10(value))
  const step = [1, 2, 5, 10].find((m) => m * magnitude >= value) ?? 10
  return step * magnitude
}

function ActivityCard({ days }: { days: ProgressResponse['days'] }) {
  const max = niceMax(Math.max(...days.map((d) => d.reviews + d.newWords)))
  const columns: ChartColumn[] = days.map((day, index) => ({
    // 14 cột: ghi nhãn cách một, luôn có ngày cuối.
    label: (days.length - 1 - index) % 2 === 0 ? shortDay(day.day) : '',
    heading: formatDay(day.day),
    values: [day.reviews, day.newWords],
    details: [
      { value: String(day.reviews), name: 'lượt ôn & luyện' },
      { value: String(day.newWords), name: 'từ mới' },
    ],
  }))
  return (
    <ChartCard title="14 ngày gần đây" subtitle="Số lần trả lời mỗi ngày: ôn thẻ và luyện trắc nghiệm, cùng số từ mới học lần đầu.">
      <div className="mb-3">
        <ChartLegend series={ACTIVITY_SERIES} />
      </div>
      <ColumnChart
        series={ACTIVITY_SERIES}
        columns={columns}
        max={max}
        ticks={[0, max / 2, max]}
        formatTick={(tick) => String(tick)}
        ariaLabel="Số lượt ôn và từ mới mỗi ngày, 14 ngày gần nhất"
      />
      <DataTable
        head={['Ngày', 'Ôn & luyện', 'Từ mới']}
        rows={days.map((day) => [formatDay(day.day), day.reviews, day.newWords])}
      />
    </ChartCard>
  )
}

/**
 * FSRS đoán trí nhớ sát tới đâu. Chênh lệch nhỏ hơn hai lần sai số chuẩn của tỉ lệ nhớ thật thì coi là khớp: ít lượt
 * ôn thì tỉ lệ thật dao động nhiều, chênh vài phần trăm chưa nói lên gì.
 */
function CalibrationCard({ calibration }: { calibration: NonNullable<ProgressResponse['calibration']> }) {
  const { reviews, predicted, actual } = calibration
  const margin = 2 * Math.sqrt((predicted * (1 - predicted)) / reviews)
  const verdict =
    actual < predicted - margin
      ? 'Bạn quên nhanh hơn FSRS nghĩ. Nếu đang xếp lịch bằng FSRS, chọn tỉ lệ nhớ mong muốn cao hơn ở trang Mục tiêu học để được ôn sớm hơn.'
      : actual > predicted + margin
        ? 'Bạn nhớ tốt hơn FSRS nghĩ. Nếu đang xếp lịch bằng FSRS, có thể hạ tỉ lệ nhớ mong muốn để ôn ít hơn mà vẫn nhớ đủ.'
        : 'FSRS đoán sát trí nhớ của bạn: chênh lệch nằm trong mức dao động bình thường của chừng này lượt ôn.'

  return (
    <ChartCard
      title="FSRS đoán trí nhớ của bạn"
      subtitle={`${reviews} lượt ôn trong 30 ngày gần nhất: lúc đến lượt ôn, FSRS đoán bạn còn nhớ bao nhiêu phần trăm số từ, và bạn nhớ được thật bao nhiêu.`}
    >
      <div className="flex flex-col gap-4">
        <Meter label="FSRS đoán" value={Math.round(predicted * 100)} />
        <Meter label="Bạn nhớ được" value={Math.round(actual * 100)} />
        <p className="text-sm font-semibold text-muted-foreground">{verdict}</p>
      </div>
    </ChartCard>
  )
}

function DirectionCard({ directions }: { directions: ProgressResponse['directions'] }) {
  return (
    <ChartCard title="Trắc nghiệm theo kiểu câu hỏi" subtitle="30 ngày gần nhất - kiểu nào thấp nhất là chỗ nên luyện thêm.">
      <div className="flex flex-col gap-4">
        {directions.map((direction) => (
          <Meter
            key={direction.direction}
            label={DIRECTION_LABELS[direction.direction]}
            value={percent(direction.correct, direction.answers)}
            detail={`${direction.correct}/${direction.answers} câu`}
          />
        ))}
      </div>
    </ChartCard>
  )
}

function ConfusionCard({ confusions }: { confusions: ProgressResponse['confusions'] }) {
  return (
    <ChartCard title="Hay nhầm nhất" subtitle="Đáp án sai bạn chọn nhiều lần nhất trong trắc nghiệm, 90 ngày gần nhất.">
      <div className="flex flex-col divide-y-2 divide-border">
        {confusions.map((confusion) => (
          <div key={`${confusion.kanjiId}-${confusion.direction}-${confusion.chosenAnswer}`} className="flex items-center gap-3 py-2">
            <div className="flex min-w-[4.5rem] shrink-0 flex-col items-center">
              {confusion.reading && (
                <span className="font-jp text-xs font-bold text-secondary-dark">{confusion.reading}</span>
              )}
              <span className="font-jp text-xl font-bold">{confusion.character}</span>
            </div>
            <div className="min-w-0 flex-1 text-sm">
              <p className="font-semibold text-muted-foreground">{DIRECTION_LABELS[confusion.direction]} · bạn chọn</p>
              <p className={cn('font-bold', confusion.direction !== 'MEANING' && 'font-jp text-base')}>
                {confusion.chosenAnswer}
              </p>
            </div>
            <span className="shrink-0 text-sm font-black tabular-nums">{confusion.times} lần</span>
          </div>
        ))}
      </div>
    </ChartCard>
  )
}
