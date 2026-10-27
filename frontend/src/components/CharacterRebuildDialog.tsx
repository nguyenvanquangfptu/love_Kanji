import { useMutation, useQueryClient } from '@tanstack/react-query'
import { characterApi } from '@/api/characters'
import { extractErrorMessage } from '@/api/client'
import type { CharacterRebuildReport } from '@/api/types'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'

const REASONS: Record<CharacterRebuildReport['skipped'][number]['reason'], string> = {
  NO_HAN_VIET: 'Chưa có Hán Việt',
  COUNT_MISMATCH: 'Số âm khác số chữ Hán',
  NOT_A_SYLLABLE: 'Có âm không hợp lệ',
}

/**
 * Tách lại Hán Việt từng chữ cho cả kho từ - dùng sau khi nhập từ vựng thẳng vào DB (từ thêm/sửa ở trang quản trị
 * được tách ngay khi lưu). Luôn kiểm tra trước, xem các từ không tách được, rồi mới ghi.
 */
export function CharacterRebuildDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const check = useMutation({ mutationFn: () => characterApi.rebuild(true) })
  const apply = useMutation({
    mutationFn: () => characterApi.rebuild(false),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['characters'] }),
  })
  const report = apply.data ?? check.data
  const error = apply.error ?? check.error

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Hán Việt từng chữ"
      description="Tách âm Hán Việt của mỗi từ ra từng chữ Hán (mỗi chữ một âm) để người học xem các từ cùng chữ."
      footer={
        <>
          <Button variant="outline" size="sm" disabled={check.isPending || apply.isPending} onClick={() => check.mutate()}>
            {check.isPending ? 'Đang kiểm tra...' : 'Kiểm tra'}
          </Button>
          <Button
            size="sm"
            disabled={!check.data || check.data.changed === 0 || apply.isPending || apply.isSuccess}
            onClick={() => apply.mutate()}
          >
            {apply.isPending ? 'Đang ghi...' : `Ghi ${check.data?.changed ?? 0} từ`}
          </Button>
        </>
      }
    >
      {error && <Alert className="mb-3">{extractErrorMessage(error)}</Alert>}
      {!report ? (
        <p className="text-sm font-semibold text-muted-foreground">
          Bấm "Kiểm tra" để xem trước: chưa ghi gì cho tới khi bấm "Ghi".
        </p>
      ) : (
        <div className="flex flex-col gap-3 text-sm">
          <p className="font-bold">
            {report.dryRun ? 'Kiểm tra' : 'Đã ghi'}: {report.split}/{report.words - report.kanaOnly} từ có chữ Hán tách được
            ({report.kanaOnly} từ chỉ có kana); {report.changed} từ {report.dryRun ? 'sẽ' : 'đã'} được cập nhật.
          </p>
          {report.skipped.length > 0 && (
            <>
              <p className="font-bold text-destructive-dark">
                {report.skipped.length} từ không tách được - sửa cột Hán Việt của chúng rồi kiểm tra lại:
              </p>
              <ul className="flex flex-col gap-1">
                {report.skipped.map((word) => (
                  <li key={word.id} className="flex items-center gap-3 rounded-xl border-2 border-border px-3 py-1.5">
                    <span className="font-jp text-base font-bold">{word.character}</span>
                    <span className="flex-1 font-extrabold uppercase text-muted-foreground">
                      {word.hanViet || '(trống)'}
                    </span>
                    <span className="text-xs font-bold text-muted-foreground">{REASONS[word.reason]}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </Modal>
  )
}
