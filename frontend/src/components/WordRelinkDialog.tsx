import { useEffect } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'

/**
 * Gắn lại câu từ vựng chưa gắn với từ nào (lúc nhập đề từ chưa có trong kho) sau khi kho có thêm từ: mở ra là chạy
 * thử ngay, bấm "Gắn" mới ghi. Liệt kê các từ vẫn chưa có trong kho - nhập thêm rồi gắn lại.
 */
export function WordRelinkDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const check = useMutation({ mutationFn: () => examAdminApi.relinkWords(true) })
  const link = useMutation({
    mutationFn: () => examAdminApi.relinkWords(false),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'exam-questions'] }),
  })
  const { mutate: runCheck, reset: resetCheck } = check
  const { reset: resetLink } = link

  useEffect(() => {
    if (open) {
      resetLink()
      runCheck()
    } else {
      resetCheck()
    }
  }, [open, runCheck, resetCheck, resetLink])

  const result = link.data ?? check.data
  const error = check.error ?? link.error

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Gắn lại từ vựng"
      description="Câu từ vựng chưa gắn với từ nào được tìm từ lại trong kho, như lúc nhập đề."
      className="sm:max-w-xl"
    >
      {error && <Alert className="mb-3">{extractErrorMessage(error)}</Alert>}
      {!result ? (
        <p className="text-sm font-semibold text-muted-foreground">{check.isPending ? 'Đang kiểm tra...' : ''}</p>
      ) : (
        <div className="flex flex-col gap-4 text-sm">
          {link.isSuccess ? (
            <p className="font-semibold text-primary-dark">
              Đã gắn {result.linked}/{result.checked} câu với từ vựng. Làm sai các câu này thì từ được đưa vào ôn tập.
            </p>
          ) : (
            <>
              <p className="font-semibold">
                {result.checked} câu từ vựng chưa gắn từ; gắn được <span className="font-black">{result.linked}</span> câu.
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <Button size="sm" disabled={result.linked === 0 || link.isPending} onClick={() => link.mutate()}>
                  {link.isPending ? 'Đang gắn...' : `Gắn ${result.linked} câu`}
                </Button>
                <span className="text-xs font-semibold text-muted-foreground">Mới kiểm tra, chưa ghi gì.</span>
              </div>
            </>
          )}

          {result.inferred.length > 0 && (
            <section className="flex flex-col gap-2">
              <h3 className="font-black">Gắn theo dạng suy ra ({result.inferredQuestions} câu)</h3>
              <p className="text-xs font-semibold text-muted-foreground">
                Từ trong câu ở dạng đã chia hoặc viết bằng kana, được đưa về từ trong kho (怒って → 怒る, やさしい → 優しい).
                Xem lại trước khi bấm Gắn: chỗ nào sai thì sửa từ trong câu ở trang duyệt.
              </p>
              <ul className="max-h-64 overflow-y-auto rounded-xl border-2 border-border p-2">
                {result.inferred.map((link) => (
                  <li key={`${link.written}-${link.words}`} className="flex gap-2 py-0.5 font-jp">
                    <span className="font-bold">{link.written}</span>
                    <span className="text-muted-foreground">→</span>
                    <span>{link.words}</span>
                    {link.questions > 1 && <span className="text-muted-foreground">×{link.questions}</span>}
                  </li>
                ))}
              </ul>
            </section>
          )}

          {result.missingWords > 0 && (
            <section className="flex flex-col gap-2">
              <h3 className="font-black">Từ chưa có trong kho ({result.missingWords} từ)</h3>
              <p className="text-xs font-semibold text-muted-foreground">
                Câu hỏi các từ này chưa gắn được. Từ bị chia (許して) cần nhập kèm dạng từ điển; từ thiếu hẳn thì thêm vào
                kho rồi gắn lại. Hỏi nhiều câu nhất trước.
              </p>
              <ul className="flex flex-wrap gap-2">
                {result.missing.map((word) => (
                  <li key={word.written} className="rounded-xl border-2 border-border px-2.5 py-1">
                    <span className="font-jp font-bold">{word.written}</span>
                    {word.questions > 1 && <span className="text-muted-foreground"> ×{word.questions}</span>}
                  </li>
                ))}
                {result.missingWords > result.missing.length && (
                  <li className="px-1 py-1 font-semibold text-muted-foreground">
                    và {result.missingWords - result.missing.length} từ khác
                  </li>
                )}
              </ul>
            </section>
          )}
        </div>
      )}
    </Modal>
  )
}
