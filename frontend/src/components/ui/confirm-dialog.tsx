import { Button } from '@/components/ui/button'
import { Modal } from '@/components/ui/modal'

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = 'Xác nhận',
  cancelLabel = 'Huỷ',
  onConfirm,
  onCancel,
}: {
  open: boolean
  title: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={title}
      description={description}
      footer={
        <>
          <Button variant="outline" onClick={onCancel}>
            {cancelLabel}
          </Button>
          <Button variant="destructive" onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </>
      }
    />
  )
}
