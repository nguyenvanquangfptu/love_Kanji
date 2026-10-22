import type { WordCharacter } from '@/api/types'
import { cn } from '@/lib/utils'
import { useCharacterSheetStore } from '@/store/characterSheetStore'

/**
 * Âm Hán Việt của một từ tách theo từng chữ Hán (勉 MIỄN · 強 CƯỜNG); bấm một chữ để xem các từ cùng chữ. Từ chưa tách
 * được, hoặc đang tải, thì hiện âm của cả từ như trước.
 */
export function CharacterChips({
  hanViet,
  parts,
  size = 'xs',
  className,
}: {
  hanViet: string
  parts: WordCharacter[] | undefined
  size?: 'xs' | 'sm'
  className?: string
}) {
  const open = useCharacterSheetStore((state) => state.open)
  const text = size === 'sm' ? 'text-sm' : 'text-xs'
  if (!parts || parts.length === 0) {
    if (!hanViet) return null
    return <p className={cn('font-extrabold uppercase tracking-wide', text, className)}>{hanViet}</p>
  }
  return (
    <div className={cn('flex flex-wrap items-center gap-1.5', className)}>
      {parts.map((part, i) => (
        <button
          key={i}
          type="button"
          title={`Các từ có chữ ${part.character}`}
          onClick={(e) => {
            // Chip nằm trên thẻ lật được: bấm chip không lật thẻ.
            e.stopPropagation()
            open(part.character)
          }}
          className={cn(
            'inline-flex items-baseline gap-1 rounded-lg border-2 border-border bg-card px-2 py-0.5 font-extrabold uppercase tracking-wide transition-colors hover:border-secondary hover:bg-secondary-soft focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40',
            text,
          )}
        >
          <span className="font-jp text-[1.15em] normal-case text-foreground">{part.character}</span>
          {part.hanViet}
        </button>
      ))}
    </div>
  )
}
