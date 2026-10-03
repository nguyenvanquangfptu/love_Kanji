import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'
import { Textarea } from '@/components/ui/textarea'

/**
 * Ghi lý do khi loại (bắt buộc) hoặc rút khỏi đề (không bắt buộc) một câu thi / đoạn văn. Trang cha đổi key mỗi lần
 * mở để ô lý do bắt đầu trống.
 */
export function ReviewNoteDialog({
  action,
  subject,
  busy,
  onConfirm,
  onCancel,
}: {
  /** null = đóng. */
  action: 'REJECTED' | 'RETIRED' | null
  /** "câu hỏi" / "đoạn văn" */
  subject: string
  busy: boolean
  onConfirm: (note: string) => void
  onCancel: () => void
}) {
  const [note, setNote] = useState('')
  const rejecting = action === 'REJECTED'
  return (
    <Modal
      open={action !== null}
      onClose={onCancel}
      title={rejecting ? `Loại ${subject}` : `Rút ${subject} khỏi đề`}
      description={rejecting ? 'Ghi lý do để lần sau sinh câu tránh lỗi này.' : 'Lý do (không bắt buộc).'}
      footer={
        <>
          <Button variant="outline" onClick={onCancel}>
            Huỷ
          </Button>
          <Button variant="destructive" disabled={busy || (rejecting && !note.trim())} onClick={() => onConfirm(note.trim())}>
            {rejecting ? 'Loại' : 'Rút khỏi đề'}
          </Button>
        </>
      }
    >
      <Textarea
        rows={3}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Ví dụ: đáp án B cũng đúng"
        aria-label="Lý do"
      />
    </Modal>
  )
}
