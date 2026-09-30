import { type ReactNode, useEffect } from 'react'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Hộp thoại: dạng bottom-sheet trên điện thoại, dạng khung giữa màn hình trên máy tính. */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  footer,
  className,
}: {
  open: boolean
  onClose: () => void
  title: string
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  className?: string
}) {
  useEffect(() => {
    if (!open) return
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center sm:p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          'pb-safe flex max-h-[92vh] w-full animate-slide-up flex-col rounded-t-3xl bg-card sm:max-w-md sm:animate-pop-in sm:rounded-3xl',
          className,
        )}
      >
        <div className="flex items-start justify-between gap-3 px-5 pt-5 sm:px-6 sm:pt-6">
          <div>
            <h2 className="text-xl font-extrabold">{title}</h2>
            {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className="-mr-1 rounded-xl p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        {children && <div className="overflow-y-auto px-5 py-4 sm:px-6">{children}</div>}
        {footer && <div className="flex flex-col-reverse gap-2 px-5 pb-5 pt-2 sm:flex-row sm:justify-end sm:px-6 sm:pb-6">{footer}</div>}
      </div>
    </div>
  )
}
