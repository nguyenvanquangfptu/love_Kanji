import { cn } from '@/lib/utils'

/** Chỗ trống 【n】 trong đoạn văn 文章の文法; câu hỏi điền hai chỗ thì đánh 【na】 và 【nb】. */
const BLANK = /【(\d+)([ab]?)】/

/**
 * Đoạn văn 文章の文法: mỗi chỗ trống 【n】 vẽ thành ô ghi số câu tương ứng trong bài ({@code labelOf}); chỗ trống của
 * câu đang làm được làm nổi, bấm vào ô thì chuyển tới câu đó.
 */
export function PassageText({
  content,
  labelOf,
  currentBlank,
  onSelect,
}: {
  content: string
  /** Số hiện trong ô của chỗ trống n (số câu trong bài); mặc định là chính n. */
  labelOf?: (blankNo: number) => number | string
  currentBlank?: number | null
  onSelect?: (blankNo: number) => void
}) {
  // split với hai nhóm bắt: mỗi chỗ trống thành 3 phần tử liền nhau - chữ đứng trước, số, rồi hậu tố a/b (có thể rỗng).
  const parts = content.split(BLANK)
  return (
    <p className="whitespace-pre-line font-jp text-lg leading-loose">
      {parts.map((part, i) => {
        if (i % 3 === 0) return part
        if (i % 3 === 2) return null
        const blankNo = Number(part)
        const suffix = parts[i + 1] ?? ''
        const current = blankNo === currentBlank
        const label = `${labelOf ? labelOf(blankNo) : blankNo}${suffix}`
        return (
          <button
            key={i}
            type="button"
            disabled={!onSelect}
            onClick={() => onSelect?.(blankNo)}
            aria-label={`Chỗ trống câu ${label}`}
            className={cn(
              'mx-1 inline-flex min-w-[3.5em] items-center justify-center rounded-md border-b-[3px] px-2 align-baseline font-sans text-base font-black',
              current
                ? 'border-secondary-dark bg-secondary text-white'
                : 'border-accent-dark bg-accent-soft text-accent-dark',
              onSelect && 'hover:brightness-95',
            )}
          >
            {label}
          </button>
        )
      })}
    </p>
  )
}
