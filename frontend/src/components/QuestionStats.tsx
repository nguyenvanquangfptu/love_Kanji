import { BarChart3 } from 'lucide-react'
import type { AdminExamQuestion } from '@/api/types'
import { cn } from '@/lib/utils'

/**
 * Thống kê một câu từ kết quả thi thật (lần phân tích gần nhất): số lượt làm từ lần duyệt gần nhất, tỉ lệ đúng, độ
 * phân biệt - âm là người làm tốt các câu khác lại hay sai câu này.
 */
export function QuestionStats({ stats }: { stats: AdminExamQuestion['stats'] }) {
  if (!stats) return null
  const negative = stats.discrimination !== null && stats.discrimination < 0
  return (
    <p className="mt-2 flex flex-wrap items-center gap-1.5 text-xs font-bold text-muted-foreground">
      <BarChart3 className="h-3.5 w-3.5" />
      {stats.responses} lượt làm · đúng {Math.round(stats.correctRate * 100)}%
      {stats.discrimination !== null && (
        <span className={cn(negative && 'text-destructive-dark')}>
          · độ phân biệt {stats.discrimination.toFixed(2)}
        </span>
      )}
      <span>· phân tích {new Date(stats.computedAt).toLocaleDateString('vi-VN')}</span>
    </p>
  )
}
