import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { characterApi } from '@/api/characters'
import { extractErrorMessage } from '@/api/client'
import type { QualityWordRef } from '@/api/types'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'

/** Số dòng hiện trong mỗi danh sách - danh sách dài chỉ cần xem mẫu, sửa hàng loạt bằng nút. */
const SHOWN = 30

/**
 * Kiểm tra chất lượng kho từ: số nét lệch với bảng số nét từng chữ (sửa hàng loạt, kiểm tra trước), chữ Hán được ghi
 * nhiều âm Hán Việt và từ thiếu Hán Việt (sửa từng từ ngay tại chỗ).
 */
export function VocabularyQualityDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const report = useQuery({ queryKey: ['vocabulary-quality'], queryFn: characterApi.quality, enabled: open })
  const checkStrokes = useMutation({ mutationFn: () => characterApi.fixStrokeCounts(true) })
  const fixStrokes = useMutation({
    mutationFn: () => characterApi.fixStrokeCounts(false),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['vocabulary-quality'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'kanji'] })
    },
  })
  const data = report.data

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Kiểm tra kho từ"
      description="Số nét sai, chữ Hán ghi nhiều âm Hán Việt, từ chưa có Hán Việt hoặc câu ví dụ."
      className="sm:max-w-2xl"
    >
      {report.isError && <Alert className="mb-3">{extractErrorMessage(report.error)}</Alert>}
      {!data ? (
        <p className="text-sm font-semibold text-muted-foreground">Đang kiểm tra...</p>
      ) : (
        <div className="flex flex-col gap-5 text-sm">
          <section className="flex flex-col gap-2">
            <h3 className="font-black">Số nét ({data.strokeMismatches.length} từ lệch với bảng số nét)</h3>
            {data.strokeMismatches.length > 0 && (
              <>
                <ul className="flex flex-wrap gap-2">
                  {data.strokeMismatches.slice(0, SHOWN).map((word) => (
                    <li key={word.id} className="rounded-xl border-2 border-border px-2.5 py-1">
                      <span className="font-jp font-bold">{word.character}</span>{' '}
                      <span className="text-muted-foreground line-through">{word.stored}</span> → {word.computed}
                    </li>
                  ))}
                  {data.strokeMismatches.length > SHOWN && (
                    <li className="px-1 py-1 font-semibold text-muted-foreground">
                      và {data.strokeMismatches.length - SHOWN} từ khác
                    </li>
                  )}
                </ul>
                <div className="flex flex-wrap items-center gap-2">
                  <Button variant="outline" size="sm" disabled={checkStrokes.isPending} onClick={() => checkStrokes.mutate()}>
                    {checkStrokes.isPending ? 'Đang kiểm tra...' : 'Kiểm tra'}
                  </Button>
                  <Button
                    size="sm"
                    disabled={!checkStrokes.data || checkStrokes.data.changed === 0 || fixStrokes.isPending || fixStrokes.isSuccess}
                    onClick={() => fixStrokes.mutate()}
                  >
                    {fixStrokes.isPending ? 'Đang sửa...' : `Sửa ${checkStrokes.data?.changed ?? 0} từ`}
                  </Button>
                  {!checkStrokes.data && (
                    <span className="text-xs font-semibold text-muted-foreground">Bấm "Kiểm tra" trước: chưa ghi gì.</span>
                  )}
                </div>
              </>
            )}
            {fixStrokes.isSuccess && (
              <p className="font-semibold text-primary-dark">Đã sửa số nét của {fixStrokes.data.changed} từ.</p>
            )}
            {(checkStrokes.error ?? fixStrokes.error) && (
              <Alert>{extractErrorMessage(checkStrokes.error ?? fixStrokes.error)}</Alert>
            )}
            {data.unknownCharacters.length > 0 && (
              <p className="font-semibold text-muted-foreground">
                Chưa có số nét của chữ <span className="font-jp text-foreground">{data.unknownCharacters.join(' ')}</span> -
                từ chứa các chữ này giữ số nét nhập tay.
              </p>
            )}
          </section>

          <section className="flex flex-col gap-2">
            <h3 className="font-black">Chữ Hán ghi nhiều âm Hán Việt ({data.hanVietConflicts.length} chữ)</h3>
            <p className="text-xs font-semibold text-muted-foreground">
              Có thể đúng (行 HÀNG / HÀNH) hoặc là ghi chưa thống nhất. Âm ít từ dùng nhất đứng đầu.
            </p>
            {data.hanVietConflicts.slice(0, SHOWN).map((conflict) => (
              <div key={conflict.character} className="rounded-2xl border-2 border-border p-3">
                <p className="mb-2 font-jp text-xl font-black">{conflict.character}</p>
                {conflict.variants.map((variant) => (
                  <div key={variant.hanViet} className="mb-2 flex flex-col gap-1">
                    <p className="text-xs font-black uppercase text-muted-foreground">
                      {variant.hanViet} · {variant.words.length} từ
                    </p>
                    {variant.words.map((word) => (
                      <HanVietEditor key={word.id} word={word} />
                    ))}
                  </div>
                ))}
              </div>
            ))}
          </section>

          <section className="flex flex-col gap-2">
            <h3 className="font-black">Từ chưa có Hán Việt ({data.missingHanViet.length} từ)</h3>
            {data.missingHanViet.slice(0, SHOWN).map((word) => (
              <HanVietEditor key={word.id} word={word} />
            ))}
          </section>

          <section className="flex flex-col gap-2">
            <h3 className="font-black">
              Từ chưa có câu ví dụ ({data.missingSentences.reduce((sum, row) => sum + row.missing, 0)} từ trong bài học)
            </h3>
            <p className="text-xs font-semibold text-muted-foreground">
              Job đêm nhờ AI viết dần: từ đang có người học ôn trước, rồi N5 → N2. Câu thi dựng từ câu AI phải chờ duyệt.
            </p>
            {data.missingSentences.length > 0 && (
              <ul className="flex flex-wrap gap-2">
                {data.missingSentences.map((row) => (
                  <li key={row.level} className="rounded-xl border-2 border-border px-2.5 py-1">
                    <span className="font-bold">{row.level}</span>: {row.missing} từ
                    {row.inReview > 0 && (
                      <span className="text-muted-foreground"> ({row.inReview} từ đang có người ôn)</span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </Modal>
  )
}

/** Sửa âm Hán Việt của một từ ngay trong báo cáo; server tách lại từng chữ, không tách được thì báo lỗi. */
function HanVietEditor({ word }: { word: QualityWordRef }) {
  const queryClient = useQueryClient()
  const [value, setValue] = useState(word.hanViet ?? '')
  const save = useMutation({
    mutationFn: () => characterApi.updateHanViet(word.id, value),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['vocabulary-quality'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'kanji'] })
    },
  })
  const unchanged = value.trim().toUpperCase() === (word.hanViet ?? '').toUpperCase()

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <span className="w-28 shrink-0 font-jp text-base font-bold">{word.character}</span>
        <Input
          aria-label={`Hán Việt của ${word.character}`}
          className="h-9 flex-1 uppercase"
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
        <Button size="sm" variant="outline" disabled={unchanged || save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? 'Đang lưu...' : 'Lưu'}
        </Button>
      </div>
      {save.isError && <p className="text-xs font-semibold text-destructive-dark">{extractErrorMessage(save.error)}</p>}
    </div>
  )
}
