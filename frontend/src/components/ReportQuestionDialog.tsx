import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Flag } from 'lucide-react'
import { examApi } from '@/api/exam'
import { extractErrorMessage } from '@/api/client'
import type { QuestionReportReason } from '@/api/types'
import { REPORT_REASON_LABEL } from '@/lib/questionReview'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Label } from '@/components/ui/label'
import { Modal } from '@/components/ui/modal'
import { Textarea } from '@/components/ui/textarea'

const REASONS = Object.keys(REPORT_REASON_LABEL) as QuestionReportReason[]

/**
 * Người học báo lỗi một câu đã làm (từ trang xem lại): chọn lý do, ghi chú thêm nếu muốn. Câu bị nhiều người báo sẽ tự
 * rút khỏi đề chờ người duyệt xem lại. Trang cha đổi key mỗi lần mở.
 */
export function ReportQuestionDialog({
  questionId,
  onClose,
  onReported,
}: {
  questionId: number | null
  onClose: () => void
  onReported: () => void
}) {
  const [reason, setReason] = useState<QuestionReportReason>('WRONG_ANSWER')
  const [note, setNote] = useState('')
  const reportMutation = useMutation({
    mutationFn: () => examApi.reportQuestion(questionId!, { reason, note: note.trim() || undefined }),
    onSuccess: onReported,
  })

  return (
    <Modal
      open={questionId !== null}
      onClose={onClose}
      title="Báo lỗi câu hỏi"
      description="Cảm ơn bạn! Câu bị nhiều người báo lỗi sẽ được rút khỏi đề để người soạn đề xem lại."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Huỷ
          </Button>
          <Button disabled={reportMutation.isPending} onClick={() => reportMutation.mutate()}>
            <Flag className="h-4 w-4" /> {reportMutation.isPending ? 'Đang gửi...' : 'Gửi báo lỗi'}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {reportMutation.isError && <Alert>{extractErrorMessage(reportMutation.error)}</Alert>}
        <div className="flex flex-col gap-2" role="radiogroup" aria-label="Lý do">
          {REASONS.map((value) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={reason === value}
              onClick={() => setReason(value)}
              className={cn(
                'rounded-xl border-2 px-3 py-2 text-left text-sm font-bold',
                reason === value ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border',
              )}
            >
              {REPORT_REASON_LABEL[value]}
            </button>
          ))}
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="report-note">Ghi chú (không bắt buộc)</Label>
          <Textarea
            id="report-note"
            value={note}
            maxLength={500}
            placeholder="Ví dụ: đáp án B cũng đúng vì..."
            onChange={(e) => setNote(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  )
}
