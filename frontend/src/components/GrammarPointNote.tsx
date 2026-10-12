import { useId, useState } from 'react'
import { ChevronDown } from 'lucide-react'
import type { GrammarPointBrief } from '@/api/types'
import { cn } from '@/lib/utils'

const EXAMPLE_PREFIX = 'Ví dụ: '

/** Nghĩa, cách nối và giải thích của một điểm ngữ pháp; dòng "Ví dụ: ..." của giải thích hiện riêng, chữ Nhật. */
export function GrammarPointDetails({ point, id }: { point: GrammarPointBrief; id?: string }) {
  const lines = (point.explanationVi ?? '').split('\n').map((line) => line.trim()).filter(Boolean)
  const examples = lines.filter((line) => line.startsWith(EXAMPLE_PREFIX))
  const notes = lines.filter((line) => !line.startsWith(EXAMPLE_PREFIX))
  return (
    <div id={id} className="rounded-xl border-2 border-purple-soft bg-card px-3 py-2 text-left text-sm">
      <p className="font-bold">
        <span className="font-jp font-black text-purple-dark">{point.pattern}</span>
        <span className="text-muted-foreground"> - </span>
        {point.meaningVi}
      </p>
      {point.connection && (
        <p className="mt-1 font-semibold text-muted-foreground">
          Cách nối: <span className="font-jp text-foreground">{point.connection}</span>
        </p>
      )}
      {notes.map((note, i) => (
        <p key={i} className="mt-1.5 font-semibold text-foreground/80">
          {note}
        </p>
      ))}
      {examples.length > 0 && (
        <ul className="mt-1.5 flex flex-col gap-1">
          {examples.map((example, i) => (
            <li key={i} className="break-words border-l-[3px] border-purple pl-2 font-jp font-semibold">
              {example.slice(EXAMPLE_PREFIX.length)}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Các điểm ngữ pháp của một câu: mỗi mẫu là một nút, bấm để mở / đóng phần giải thích ngay bên dưới.
 * {@code defaultOpen}: mở sẵn tất cả (vd. khi người học vừa trả lời sai).
 */
export function GrammarPointList({
  points,
  label,
  defaultOpen = false,
  className,
}: {
  points: GrammarPointBrief[]
  label?: string
  defaultOpen?: boolean
  className?: string
}) {
  const baseId = useId()
  const [open, setOpen] = useState<Set<number>>(() => new Set(defaultOpen ? points.map((point) => point.id) : []))
  if (points.length === 0) return null

  const toggle = (pointId: number) =>
    setOpen((current) => {
      const next = new Set(current)
      if (next.has(pointId)) next.delete(pointId)
      else next.add(pointId)
      return next
    })

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex flex-wrap items-center gap-1.5">
        {label && <span className="text-xs font-extrabold uppercase text-muted-foreground">{label}</span>}
        {points.map((point) => (
          <button
            key={point.id}
            type="button"
            aria-expanded={open.has(point.id)}
            aria-controls={open.has(point.id) ? `${baseId}-${point.id}` : undefined}
            title={point.meaningVi}
            onClick={() => toggle(point.id)}
            className="inline-flex items-center gap-1 rounded-lg bg-purple-soft px-2 py-0.5 font-jp text-xs font-extrabold text-purple-dark transition-colors hover:bg-purple hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-purple-dark"
          >
            {point.pattern}
            <ChevronDown className={cn('h-3.5 w-3.5 transition-transform', open.has(point.id) && 'rotate-180')} />
          </button>
        ))}
      </div>
      {points
        .filter((point) => open.has(point.id))
        .map((point) => (
          <GrammarPointDetails key={point.id} point={point} id={`${baseId}-${point.id}`} />
        ))}
    </div>
  )
}
