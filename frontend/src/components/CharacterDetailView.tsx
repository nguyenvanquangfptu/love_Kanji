import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { characterApi } from '@/api/characters'
import { srsApi } from '@/api/srs'
import { extractErrorMessage } from '@/api/client'
import { describeAddResult } from '@/lib/review'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

/** Một chữ Hán và các từ trong kho có chữ đó, gom theo âm Hán Việt; thêm được các từ chưa có vào Ôn tập. */
export function CharacterDetailView({ character }: { character: string }) {
  const queryClient = useQueryClient()
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['characters', 'detail', character],
    queryFn: () => characterApi.detail(character),
  })
  // Không làm mới các query ['srs', ...] như useAddToReview: bảng này mở được giữa phiên ôn, tải lại thẻ hôm nay sẽ
  // xáo hàng đợi đang ôn.
  const addMutation = useMutation({
    mutationFn: srsApi.addCards,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['characters', 'detail', character] }),
  })

  if (isLoading) return <PageSpinner />
  if (isError) return <Alert>{extractErrorMessage(error)}</Alert>
  if (!data) return null

  const words = data.readings.flatMap((reading) => reading.words)
  const notInReview = words.filter((word) => !word.inReview).map((word) => word.id)
  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center gap-4">
        <span className="font-jp text-6xl font-bold leading-none">{data.character}</span>
        <div>
          <p className="text-lg font-black uppercase tracking-wide">
            {data.readings.map((reading) => reading.hanViet).join(' · ')}
          </p>
          <p className="text-sm font-semibold text-muted-foreground">{words.length} từ trong kho có chữ này</p>
        </div>
      </div>

      {data.readings.map((reading) => (
        <section key={reading.hanViet}>
          {data.readings.length > 1 && (
            <h3 className="mb-2 text-sm font-black uppercase tracking-wide text-secondary-dark">
              Đọc là {reading.hanViet}
            </h3>
          )}
          <ul className="flex flex-col gap-2">
            {reading.words.map((word) => (
              <li key={word.id} className="flex items-center gap-3 rounded-2xl border-2 border-border px-3 py-2">
                <div className="flex min-w-[3.5rem] shrink-0 flex-col items-center">
                  {word.reading && (
                    <span className="font-jp text-[11px] font-bold text-secondary-dark">{word.reading}</span>
                  )}
                  <span className="font-jp text-xl font-bold leading-tight">{word.character}</span>
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-extrabold uppercase tracking-wide text-muted-foreground">{word.hanViet}</p>
                  <p className="text-sm font-semibold leading-snug">{word.meaning}</p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  <Badge variant="outline">{word.jlptLevel}</Badge>
                  {word.inReview && <Badge variant="secondary">Đang ôn</Badge>}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}

      {notInReview.length > 0 && (
        <Button
          variant="secondary"
          disabled={addMutation.isPending}
          onClick={() => addMutation.mutate(notInReview)}
        >
          <Plus className="h-5 w-5" strokeWidth={2.5} />
          Thêm {notInReview.length} từ chưa có vào Ôn tập
        </Button>
      )}
      {addMutation.isSuccess && (
        <p className="text-center text-sm font-bold text-primary-dark">{describeAddResult(addMutation.data)}</p>
      )}
      {addMutation.isError && <Alert>{extractErrorMessage(addMutation.error)}</Alert>}
    </div>
  )
}
