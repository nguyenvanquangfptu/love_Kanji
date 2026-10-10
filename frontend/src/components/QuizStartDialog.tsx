import { Keyboard, ListChecks } from 'lucide-react'
import type { QuizAnswerKind } from '@/api/types'
import { cn } from '@/lib/utils'
import { Modal } from '@/components/ui/modal'

const KINDS: { value: QuizAnswerKind; title: string; description: string; icon: typeof ListChecks }[] = [
  {
    value: 'choice',
    title: 'Chọn đáp án',
    description: '4 lựa chọn: cách đọc, cách viết hoặc nghĩa của từ.',
    icon: ListChecks,
  },
  {
    value: 'typing',
    title: 'Gõ romaji',
    description: 'Hiện chữ Hán, bạn tự gõ cách đọc (vd. shusshin). Chỉ hỏi từ có chữ Hán.',
    icon: Keyboard,
  },
]

/** Hỏi kiểu trắc nghiệm trước khi bắt đầu: chọn đáp án, hay gõ romaji. Bấm một kiểu là bắt đầu luôn. */
export function QuizStartDialog({
  open,
  preselected,
  onChoose,
  onClose,
}: {
  open: boolean
  preselected: QuizAnswerKind
  onChoose: (kind: QuizAnswerKind) => void
  onClose: () => void
}) {
  return (
    <Modal open={open} onClose={onClose} title="Chọn kiểu trắc nghiệm">
      <div className="flex flex-col gap-3 px-5 pb-5 pt-4 sm:px-6 sm:pb-6">
        {KINDS.map(({ value, title, description, icon: Icon }) => (
          <button
            key={value}
            type="button"
            autoFocus={value === preselected}
            onClick={() => onChoose(value)}
            className={cn(
              'flex items-start gap-4 rounded-2xl border-2 border-b-4 p-4 text-left transition-all hover:bg-muted active:translate-y-[2px] active:border-b-2',
              value === preselected ? 'border-secondary bg-secondary-soft' : 'border-border bg-card',
            )}
          >
            <Icon className="mt-0.5 h-7 w-7 shrink-0 text-secondary-dark" strokeWidth={2.5} />
            <span>
              <span className="block font-extrabold">{title}</span>
              <span className="mt-0.5 block text-sm font-semibold text-muted-foreground">{description}</span>
            </span>
          </button>
        ))}
      </div>
    </Modal>
  )
}
