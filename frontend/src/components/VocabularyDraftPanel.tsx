import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Sparkles } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import type { JlptQuestionType } from '@/api/types'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
import { draftSummary } from '@/lib/questionReview'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'

const COUNTS = [3, 5, 8]

/**
 * Nhờ AI viết nháp câu 言い換え類義 / 用法, mỗi câu cho một từ trong bài của cấp độ chưa có câu dạng đó. Câu nháp được
 * máy kiểm tra (cấu trúc, từ vượt cấp, AI giải lại) rồi vào hàng chờ duyệt.
 */
export function VocabularyDraftPanel({
  level,
  type,
  onDrafted,
}: {
  level: string
  type: JlptQuestionType
  onDrafted: () => void
}) {
  const [count, setCount] = useState(5)
  const draftMutation = useMutation({
    mutationFn: () => examAdminApi.draftVocabulary(level, type, count),
    onSuccess: onDrafted,
  })

  return (
    <Card className="mb-4 flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="font-black">
            Nhờ AI viết nháp <span className="font-jp">{QUESTION_TYPE_META[type].jp}</span>
          </p>
          <p className="text-sm font-semibold text-muted-foreground">
            Mỗi câu cho một từ trong bài {level} chưa có câu dạng này. Tốn 2 lượt Gemini: viết nháp và giải lại để
            kiểm tra.
          </p>
        </div>
        <div className="flex gap-1.5" role="group" aria-label="Số câu">
          {COUNTS.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={count === value}
              onClick={() => setCount(value)}
              className={cn(
                'h-10 w-10 rounded-xl border-2 font-black',
                count === value ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border',
              )}
            >
              {value}
            </button>
          ))}
        </div>
        <Button disabled={draftMutation.isPending} onClick={() => draftMutation.mutate()}>
          <Sparkles className="h-4 w-4" /> {draftMutation.isPending ? 'AI đang viết...' : `Sinh ${count} câu`}
        </Button>
      </div>
      {draftMutation.isError && <Alert>{extractErrorMessage(draftMutation.error)}</Alert>}
      {draftMutation.data && (
        <p className="rounded-2xl bg-secondary-soft px-4 py-3 text-sm font-bold text-secondary-dark">
          {draftSummary(draftMutation.data)}
        </p>
      )}
    </Card>
  )
}
