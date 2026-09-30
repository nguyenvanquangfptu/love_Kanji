import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BrainCircuit, CheckCircle2, Plus } from 'lucide-react'
import { srsApi } from '@/api/srs'
import { extractErrorMessage } from '@/api/client'
import { describeAddResult, useAddToReview } from '@/lib/review'
import { cn } from '@/lib/utils'
import { Button, buttonVariants } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'

/** Trạng thái Ôn tập (SRS) của một bài: bao nhiêu từ đã vào lịch ôn, kèm nút đưa cả bài vào. */
export function LessonReviewCard({ tagId, wordIds }: { tagId: number; wordIds: number[] }) {
  const { data: status } = useQuery({
    queryKey: ['srs', 'tag-status', tagId],
    queryFn: () => srsApi.getTagStatus(tagId),
  })
  const addToReview = useAddToReview()

  const total = status?.totalWords ?? 0
  const inReview = status?.inReview ?? 0
  const allInReview = status !== undefined && total > 0 && inReview >= total

  return (
    <Card className="mt-4 p-4 sm:p-5">
      <div className="flex items-center gap-3">
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-secondary-soft text-secondary-dark">
          <BrainCircuit className="h-6 w-6" strokeWidth={2.5} />
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-extrabold">Ôn tập dài hạn</p>
          <p className="text-sm font-semibold text-muted-foreground">
            {!status
              ? 'Đang kiểm tra...'
              : allInReview
                ? `Cả ${total} từ đã nằm trong lịch ôn`
                : `${inReview}/${total} từ đã có trong lịch ôn`}
          </p>
        </div>
      </div>
      <Progress value={total > 0 ? (inReview / total) * 100 : 0} className="mt-3 h-3" barClassName="bg-secondary" />

      {addToReview.isSuccess && (
        <p className="mt-3 flex items-center gap-2 text-sm font-bold text-primary-dark">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          {describeAddResult(addToReview.data)}
        </p>
      )}
      {addToReview.isError && (
        <p className="mt-3 text-sm font-bold text-destructive-dark">{extractErrorMessage(addToReview.error)}</p>
      )}

      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        {!allInReview && (
          <Button
            variant="secondary"
            className="sm:flex-1"
            disabled={!status || wordIds.length === 0 || addToReview.isPending}
            onClick={() => addToReview.mutate(wordIds)}
          >
            <Plus className="h-5 w-5" strokeWidth={3} />
            {addToReview.isPending
              ? 'Đang thêm...'
              : inReview > 0
                ? `Thêm ${total - inReview} từ còn lại vào Ôn tập`
                : 'Thêm bài vào Ôn tập'}
          </Button>
        )}
        {inReview > 0 && (
          <Link to="/flashcards" className={cn(buttonVariants({ variant: 'outline' }), 'sm:flex-1')}>
            Ôn tập ngay
          </Link>
        )}
      </div>
    </Card>
  )
}
