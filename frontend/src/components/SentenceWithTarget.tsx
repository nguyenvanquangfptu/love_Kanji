/** Ô trống của câu 文脈規定 / 文法形式の判断. */
const BLANK = '（　　）'

/**
 * Câu ví dụ kiểu đề JLPT: gạch chân phần được hỏi (lần xuất hiện đầu tiên của {@code target}); câu không có phần
 * gạch chân mà có ô trống ({@link BLANK}) thì làm nổi ô trống.
 */
export function SentenceWithTarget({ sentence, target }: { sentence: string; target: string | null }) {
  const at = target ? sentence.indexOf(target) : -1
  if (!target || at < 0) {
    const blank = sentence.indexOf(BLANK)
    if (blank < 0) return <>{sentence}</>
    return (
      <>
        {sentence.slice(0, blank)}
        <span
          className="mx-1 inline-block min-w-[3.5em] rounded-md border-b-[3px] border-accent-dark bg-accent-soft align-baseline"
          aria-label="chỗ trống"
        >
          &nbsp;
        </span>
        {sentence.slice(blank + BLANK.length)}
      </>
    )
  }
  return (
    <>
      {sentence.slice(0, at)}
      <span className="rounded-md bg-accent-soft px-1 font-bold underline decoration-accent-dark decoration-[3px] underline-offset-[7px]">
        {target}
      </span>
      {sentence.slice(at + target.length)}
    </>
  )
}
