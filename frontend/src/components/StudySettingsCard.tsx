import { Repeat2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { usePreferencesStore } from '@/store/preferencesStore'
import { REVERSE_MIN_INTERVAL_DAYS } from '@/lib/reverseCard'
import { Card } from '@/components/ui/card'

/** Tuỳ chọn cách ôn, lưu trên trình duyệt này: hỏi ngược thẻ đã thuộc. */
export function StudySettingsCard({ className }: { className?: string }) {
  const reverseCards = usePreferencesStore((state) => state.reverseCards)
  const toggleReverseCards = usePreferencesStore((state) => state.toggleReverseCards)

  return (
    <Card className={cn('flex items-start gap-3 p-4 sm:p-5', className)}>
      <Repeat2 className="mt-0.5 h-5 w-5 shrink-0 text-purple" strokeWidth={2.5} />
      <div className="min-w-0 flex-1">
        <h2 className="text-lg font-black">Hỏi ngược thẻ đã thuộc</h2>
        <p className="text-sm font-semibold text-muted-foreground">
          Thẻ có khoảng ôn từ {REVERSE_MIN_INTERVAL_DAYS} ngày trở lên thỉnh thoảng hiện nghĩa trước để bạn tự nhớ ra từ
          tiếng Nhật. Tự nhớ ra khó hơn nhận ra nên nhớ lâu hơn; điểm vẫn tính vào lịch ôn của từ đó.
        </p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={reverseCards}
        aria-label="Hỏi ngược thẻ đã thuộc"
        onClick={toggleReverseCards}
        className={cn(
          'relative mt-1 h-7 w-12 shrink-0 rounded-full border-2 transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40',
          reverseCards ? 'border-purple bg-purple' : 'border-border bg-muted',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 h-5 w-5 rounded-full bg-card shadow transition-all',
            reverseCards ? 'left-[22px]' : 'left-0.5',
          )}
        />
      </button>
    </Card>
  )
}
