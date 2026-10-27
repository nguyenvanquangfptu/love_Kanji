import type { ReactNode } from 'react'
import type { RubySegment } from '@/api/types'
import { usePreferencesStore } from '@/store/preferencesStore'

/**
 * Các ô trống trong câu: ô ngoặc của câu điền từ / chọn ngữ pháp, ＿＿＿ và ＿★＿ của câu sắp xếp (ô có ★ là ô được
 * hỏi).
 */
const BLANKS = /(（　　）|＿★＿|＿＿＿)/

const TARGET_CLASS =
  'rounded-md bg-accent-soft px-1 font-bold underline decoration-accent-dark decoration-[3px] underline-offset-[7px]'

/**
 * Câu ví dụ kiểu đề JLPT: gạch chân phần được hỏi (lần xuất hiện đầu tiên của {@code target}); câu không có phần
 * gạch chân mà có ô trống thì vẽ ô trống. Có {@code segments} (câu đã chia đoạn ở server) thì hiện kèm furigana, trừ
 * khi người học tắt furigana.
 */
export function SentenceWithTarget({
  sentence,
  target,
  segments,
}: {
  sentence: string
  target: string | null
  segments?: RubySegment[] | null
}) {
  const showFurigana = usePreferencesStore((state) => state.showFurigana)
  if (segments && segments.length > 0) {
    return <RubySentence segments={segments} showFurigana={showFurigana} />
  }
  const at = target ? sentence.indexOf(target) : -1
  if (!target || at < 0) {
    return <>{withBlanks(sentence)}</>
  }
  return (
    <>
      {sentence.slice(0, at)}
      <span className={TARGET_CLASS}>{target}</span>
      {sentence.slice(at + target.length)}
    </>
  )
}

/** Các đoạn liền nhau của phần được hỏi gom vào một khung gạch chân. */
function RubySentence({ segments, showFurigana }: { segments: RubySegment[]; showFurigana: boolean }) {
  const parts: ReactNode[] = []
  for (let i = 0; i < segments.length; ) {
    if (!segments[i].target) {
      parts.push(<Segment key={i} segment={segments[i]} showFurigana={showFurigana} />)
      i++
      continue
    }
    const start = i
    const underlined: ReactNode[] = []
    for (; i < segments.length && segments[i].target; i++) {
      underlined.push(<Segment key={i} segment={segments[i]} showFurigana={showFurigana} />)
    }
    parts.push(
      <span key={`target-${start}`} className={TARGET_CLASS}>
        {underlined}
      </span>,
    )
  }
  return <>{parts}</>
}

function Segment({ segment, showFurigana }: { segment: RubySegment; showFurigana: boolean }) {
  if (segment.ruby === null) return <>{withBlanks(segment.text)}</>
  if (!showFurigana) return <>{segment.text}</>
  return (
    <ruby>
      {segment.text}
      <rt>{segment.ruby}</rt>
    </ruby>
  )
}

/** split với nhóm bắt: phần tử ở vị trí lẻ là ô trống. */
function withBlanks(text: string): ReactNode {
  const parts = text.split(BLANKS)
  if (parts.length === 1) return text
  return parts.map((part, i) => (i % 2 === 0 ? part : <Blank key={i} star={part.includes('★')} />))
}

function Blank({ star }: { star: boolean }) {
  return (
    <span
      className="mx-1 inline-block min-w-[3.5em] rounded-md border-b-[3px] border-accent-dark bg-accent-soft text-center align-baseline font-black text-accent-dark"
      aria-label={star ? 'ô ★' : 'chỗ trống'}
    >
      {star ? '★' : ' '}
    </span>
  )
}
