import { useQuery } from '@tanstack/react-query'
import { Quote } from 'lucide-react'
import { FuriganaToggle } from '@/components/FuriganaToggle'
import { SentenceWithTarget } from '@/components/SentenceWithTarget'
import { SpeakButton } from '@/components/SpeakButton'
import { cn } from '@/lib/utils'
import { wordSentencesQuery } from '@/lib/wordSentences'

/**
 * Các câu dùng từ đang học, hiện dưới thẻ khi đã lật: câu ví dụ của từ, rồi câu trong ngân hàng đề. Từ được tô (kể cả
 * khi đã chia), có furigana bật/tắt được và nút đọc cả câu. Không có câu nào thì không hiện gì.
 */
export function WordSentences({ kanjiId, className }: { kanjiId: number; className?: string }) {
  const { data: sentences } = useQuery(wordSentencesQuery(kanjiId))
  if (!sentences || sentences.length === 0) return null

  return (
    <section
      aria-label="Câu ví dụ"
      className={cn('flex flex-col gap-2 rounded-2xl border-2 border-border bg-card p-3 text-left', className)}
    >
      <div className="flex items-center gap-2">
        <Quote className="h-4 w-4 shrink-0 text-secondary" strokeWidth={2.5} />
        <h3 className="flex-1 text-sm font-black">Ví dụ</h3>
        <FuriganaToggle segments={sentences.flatMap((sentence) => sentence.segments ?? [])} />
      </div>
      <ul className="flex flex-col divide-y-2 divide-border">
        {sentences.map((sentence) => (
          <li key={sentence.sentence} className="flex items-start gap-1 py-1.5">
            <p className="flex-1 font-jp text-lg leading-loose">
              <SentenceWithTarget sentence={sentence.sentence} target={null} segments={sentence.segments} />
              {sentence.source === 'EXAM' && (
                <span className="ml-2 align-middle font-sans text-[11px] font-bold text-muted-foreground">câu thi</span>
              )}
            </p>
            <SpeakButton text={sentence.sentence} className="-mr-1" />
          </li>
        ))}
      </ul>
    </section>
  )
}
