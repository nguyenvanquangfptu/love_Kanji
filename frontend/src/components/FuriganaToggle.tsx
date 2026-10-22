import type { RubySegment } from '@/api/types'
import { cn } from '@/lib/utils'
import { usePreferencesStore } from '@/store/preferencesStore'

/**
 * Công tắc bật/tắt furigana cho mọi câu tiếng Nhật, nhớ trên trình duyệt này. Không hiện gì nếu các câu đang xem không
 * có chữ nào có furigana.
 */
export function FuriganaToggle({ segments, className }: { segments: RubySegment[] | null; className?: string }) {
  const showFurigana = usePreferencesStore((state) => state.showFurigana)
  const toggleFurigana = usePreferencesStore((state) => state.toggleFurigana)
  if (!segments?.some((segment) => segment.ruby !== null)) return null
  return (
    <button
      type="button"
      role="switch"
      aria-checked={showFurigana}
      aria-label="Furigana (cách đọc trên chữ Hán)"
      onClick={toggleFurigana}
      title={showFurigana ? 'Ẩn cách đọc trên chữ Hán' : 'Hiện cách đọc trên chữ Hán'}
      className={cn(
        'inline-flex shrink-0 items-center gap-1.5 rounded-full border-2 px-3 py-1 text-xs font-extrabold transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40',
        showFurigana
          ? 'border-secondary bg-secondary-soft text-secondary-dark'
          : 'border-border bg-card text-muted-foreground hover:bg-muted',
        className,
      )}
    >
      <span className="font-jp">ふりがな</span>
      {showFurigana ? 'Bật' : 'Tắt'}
    </button>
  )
}
