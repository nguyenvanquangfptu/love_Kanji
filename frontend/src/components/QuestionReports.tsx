import { Flag, X } from 'lucide-react'
import type { AdminExamQuestion } from '@/api/types'
import { REPORT_REASON_LABEL } from '@/lib/questionReview'
import { Button } from '@/components/ui/button'

/** Các báo lỗi của người học đang chờ xem cho một câu, kèm nút bỏ qua khi câu không sai. */
export function QuestionReports({
  reports,
  busy,
  onDismiss,
}: {
  reports: AdminExamQuestion['reports']
  busy: boolean
  onDismiss: () => void
}) {
  if (reports.length === 0) return null
  return (
    <div className="mt-2 rounded-xl border-2 border-destructive/30 bg-destructive-soft px-3 py-2">
      <div className="flex flex-wrap items-center gap-2">
        <p className="flex items-center gap-1.5 text-sm font-black text-destructive-dark">
          <Flag className="h-4 w-4" /> {reports.length} người học báo lỗi
        </p>
        <Button size="sm" variant="ghost" className="ml-auto" disabled={busy} onClick={onDismiss}>
          <X className="h-4 w-4" /> Bỏ qua báo lỗi
        </Button>
      </div>
      <ul className="mt-1 flex flex-col gap-1 text-sm font-semibold text-foreground/80">
        {reports.map((report, index) => (
          <li key={index}>
            <span className="font-bold">{REPORT_REASON_LABEL[report.reason]}</span>
            {report.note && <span> - {report.note}</span>}
            <span className="text-xs text-muted-foreground"> ({new Date(report.createdAt).toLocaleDateString('vi-VN')})</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
