/** Câu ví dụ kiểu đề JLPT: gạch chân phần được hỏi (lần xuất hiện đầu tiên của {@code target}). */
export function SentenceWithTarget({ sentence, target }: { sentence: string; target: string | null }) {
  const at = target ? sentence.indexOf(target) : -1
  if (!target || at < 0) return <>{sentence}</>
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
