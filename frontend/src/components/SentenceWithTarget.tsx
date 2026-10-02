/**
 * Các ô trống trong câu: ô ngoặc của câu điền từ / chọn ngữ pháp, ＿＿＿ và ＿★＿ của câu sắp xếp (ô có ★ là ô được
 * hỏi).
 */
const BLANKS = /(（　　）|＿★＿|＿＿＿)/

/**
 * Câu ví dụ kiểu đề JLPT: gạch chân phần được hỏi (lần xuất hiện đầu tiên của {@code target}); câu không có phần
 * gạch chân mà có ô trống thì vẽ ô trống.
 */
export function SentenceWithTarget({ sentence, target }: { sentence: string; target: string | null }) {
  const at = target ? sentence.indexOf(target) : -1
  if (!target || at < 0) {
    // split với nhóm bắt: phần tử ở vị trí lẻ là ô trống.
    const parts = sentence.split(BLANKS)
    if (parts.length === 1) return <>{sentence}</>
    return <>{parts.map((part, i) => (i % 2 === 0 ? part : <Blank key={i} star={part.includes('★')} />))}</>
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

function Blank({ star }: { star: boolean }) {
  return (
    <span
      className="mx-1 inline-block min-w-[3.5em] rounded-md border-b-[3px] border-accent-dark bg-accent-soft text-center align-baseline font-black text-accent-dark"
      aria-label={star ? 'ô ★' : 'chỗ trống'}
    >
      {star ? '★' : '\u00a0'}
    </span>
  )
}
