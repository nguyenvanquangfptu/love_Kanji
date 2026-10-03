import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { Sparkles } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import type { GrammarPoint, JlptQuestionType } from '@/api/types'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Modal } from '@/components/ui/modal'

const TYPES: JlptQuestionType[] = ['GRAMMAR_FORM', 'SENTENCE_ORDER']
const COUNTS = [3, 5, 8]

/**
 * Nhờ AI viết nháp câu thi cho một điểm ngữ pháp. Câu nháp được máy kiểm tra (cấu trúc, từ vượt cấp, AI giải lại) rồi
 * vào hàng chờ duyệt; trang cha đổi key mỗi lần mở.
 */
export function QuestionDraftDialog({
  point,
  onClose,
  onDrafted,
}: {
  point: GrammarPoint | null
  onClose: () => void
  onDrafted: () => void
}) {
  const [type, setType] = useState<JlptQuestionType>('GRAMMAR_FORM')
  const [count, setCount] = useState(5)
  const draftMutation = useMutation({
    mutationFn: () => examAdminApi.draft(point!.id, type, count),
    onSuccess: onDrafted,
  })
  const result = draftMutation.data

  return (
    <Modal
      open={point !== null}
      onClose={onClose}
      title="Sinh nháp bằng AI"
      description={point ? `Cho mẫu ${point.pattern} (${point.jlptLevel}). Mỗi lần tốn 2 lượt Gemini: viết nháp và giải lại để kiểm tra.` : undefined}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Đóng
          </Button>
          <Button disabled={draftMutation.isPending} onClick={() => draftMutation.mutate()}>
            <Sparkles className="h-4 w-4" /> {draftMutation.isPending ? 'AI đang viết...' : `Sinh ${count} câu`}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {draftMutation.isError && <Alert>{extractErrorMessage(draftMutation.error)}</Alert>}
        {result && point && (
          <div className="rounded-2xl bg-secondary-soft px-4 py-3 text-sm font-bold text-secondary-dark">
            {result.drafted} câu vào hàng chờ duyệt
            {result.flagged > 0 && ` (${result.flagged} câu có cảnh báo)`}
            {result.rejected > 0 && ` · ${result.rejected} câu sai cấu trúc bị loại`}
            {result.unreadable > 0 && ` · ${result.unreadable} mục không đọc được`}
            {result.drafted > 0 && (
              <Link
                to={`/admin/questions?level=${point.jlptLevel}&grammarPointId=${point.id}&status=DRAFT`}
                className="mt-1 block text-secondary underline"
              >
                Duyệt ngay
              </Link>
            )}
          </div>
        )}
        <div className="flex flex-col gap-2">
          <p className="text-sm font-bold">Dạng câu</p>
          <div className="grid grid-cols-2 gap-2">
            {TYPES.map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={type === value}
                onClick={() => setType(value)}
                className={cn(
                  'rounded-xl border-2 px-3 py-2 text-left',
                  type === value ? 'border-secondary bg-secondary-soft' : 'border-border',
                )}
              >
                <span className="block font-jp font-black">{QUESTION_TYPE_META[value].jp}</span>
                <span className="text-xs font-semibold text-muted-foreground">{QUESTION_TYPE_META[value].vi}</span>
              </button>
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-2">
          <p className="text-sm font-bold">Số câu</p>
          <div className="grid grid-cols-3 gap-2">
            {COUNTS.map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={count === value}
                onClick={() => setCount(value)}
                className={cn(
                  'rounded-xl border-2 py-2 font-black',
                  count === value ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border',
                )}
              >
                {value}
              </button>
            ))}
          </div>
        </div>
      </div>
    </Modal>
  )
}
