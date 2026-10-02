import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Lightbulb, NotebookPen, Sparkles } from 'lucide-react'
import { kanjiApi } from '@/api/kanji'
import { srsApi } from '@/api/srs'
import { extractErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * Mẹo nhớ chung (AI sinh dựa trên âm Hán Việt, dùng chung cho mọi người) và cách nhớ riêng người học tự ghi.
 * Chỉ dùng cho từ đã có trong Ôn tập (ghi chú gắn với thẻ ôn). Đặt `key` theo từ để mỗi từ có trạng thái riêng.
 */
export function MemoryAidPanel({
  kanjiId,
  mnemonic: initialMnemonic,
  personalNote: initialNote,
  className,
}: {
  kanjiId: number
  mnemonic: string | null
  personalNote: string | null
  className?: string
}) {
  const [mnemonic, setMnemonic] = useState(initialMnemonic)
  const [note, setNote] = useState(initialNote)
  // null = không sửa ghi chú.
  const [draft, setDraft] = useState<string | null>(null)

  const suggest = useMutation({
    mutationFn: () => kanjiApi.getMnemonic(kanjiId),
    onSuccess: (data) => setMnemonic(data.mnemonic),
  })
  const save = useMutation({
    mutationFn: (text: string) => srsApi.saveNote({ kanjiId, note: text }),
    onSuccess: (_data, text) => {
      setNote(text.trim() || null)
      setDraft(null)
    },
  })

  return (
    <div className={cn('flex flex-col gap-2.5 rounded-2xl border-2 border-accent/50 bg-accent-soft p-3 text-sm', className)}>
      <div className="flex items-start gap-2">
        <Lightbulb className="mt-0.5 h-4 w-4 shrink-0 text-accent-dark" strokeWidth={2.5} />
        {mnemonic ? (
          <p className="font-semibold leading-snug">{mnemonic}</p>
        ) : (
          <div className="flex flex-col items-start gap-1">
            <Button size="sm" variant="outline" disabled={suggest.isPending} onClick={() => suggest.mutate()}>
              <Sparkles className="h-4 w-4" />
              {suggest.isPending ? 'AI đang nghĩ...' : 'Gợi ý mẹo nhớ bằng AI'}
            </Button>
            {suggest.isError && (
              <p className="text-xs font-bold text-destructive-dark">{extractErrorMessage(suggest.error)}</p>
            )}
          </div>
        )}
      </div>

      <div className="flex items-start gap-2">
        <NotebookPen className="mt-0.5 h-4 w-4 shrink-0 text-secondary-dark" strokeWidth={2.5} />
        {draft !== null ? (
          <div className="flex flex-1 flex-col gap-2">
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              rows={2}
              maxLength={1000}
              autoFocus
              placeholder="Cách bạn nhớ từ này..."
              className="w-full rounded-xl border-2 border-border bg-card px-3 py-2 text-sm font-semibold transition-colors placeholder:font-normal placeholder:text-muted-foreground focus-visible:border-secondary focus-visible:outline-none"
            />
            <div className="flex gap-2">
              <Button size="sm" disabled={save.isPending} onClick={() => save.mutate(draft)}>
                {save.isPending ? 'Đang lưu...' : 'Lưu'}
              </Button>
              <Button size="sm" variant="ghost" onClick={() => setDraft(null)}>
                Huỷ
              </Button>
            </div>
            {save.isError && <p className="text-xs font-bold text-destructive-dark">{extractErrorMessage(save.error)}</p>}
          </div>
        ) : note ? (
          <p className="flex-1 font-semibold leading-snug">
            {note}{' '}
            <button
              type="button"
              onClick={() => setDraft(note)}
              className="text-xs font-extrabold text-secondary hover:underline"
            >
              Sửa
            </button>
          </p>
        ) : (
          <button
            type="button"
            onClick={() => setDraft('')}
            className="text-left font-bold text-secondary hover:underline"
          >
            Ghi cách nhớ của riêng bạn
          </button>
        )}
      </div>
    </div>
  )
}
