import { Fragment, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** Gạch chân trong bài đọc và câu hỏi Đọc hiểu: 〔下線〕...〔/下線〕. */
const UNDERLINE = /〔(\/?)下線〕/
/** Một dòng của bảng: | ô | ô |. */
const TABLE_ROW = /^[ \t　]*\|.*\|[ \t　]*$/
/** Dòng kẻ dưới hàng tiêu đề kiểu Markdown: | --- | :---: |. */
const TABLE_RULE = /^[\s|:\-　]+$/

type Block = { kind: 'text'; lines: string[] } | { kind: 'table'; rows: string[][] }

/** Chia bài đọc thành các khối chữ và các bảng (các dòng | ô | ô | liền nhau). */
function blocks(content: string): Block[] {
  const result: Block[] = []
  for (const line of content.split(/\r?\n/)) {
    const last = result[result.length - 1]
    if (TABLE_ROW.test(line)) {
      if (TABLE_RULE.test(line)) continue
      const cells = line.trim().slice(1, -1).split('|').map((cell) => cell.trim())
      if (last?.kind === 'table') last.rows.push(cells)
      else result.push({ kind: 'table', rows: [cells] })
    } else if (last?.kind === 'text') {
      last.lines.push(line)
    } else {
      result.push({ kind: 'text', lines: [line] })
    }
  }
  return result
}

/**
 * Chữ có phần gạch chân 〔下線〕...〔/下線〕 vẽ thành gạch chân thật; mọi thứ khác giữ nguyên là chữ (không render HTML).
 */
export function UnderlinedText({ text }: { text: string }) {
  // split với một nhóm bắt: phần tử lẻ là "" (mở) hoặc "/" (đóng), phần tử chẵn là chữ.
  const parts = text.split(UNDERLINE)
  let underlined = false
  const nodes: ReactNode[] = []
  parts.forEach((part, i) => {
    if (i % 2 === 1) {
      underlined = part === ''
      return
    }
    if (!part) return
    nodes.push(
      underlined ? (
        <u key={i} className="decoration-2 underline-offset-[6px]">
          {part}
        </u>
      ) : (
        <Fragment key={i}>{part}</Fragment>
      ),
    )
  })
  return <>{nodes}</>
}

/** Bài đọc của phần Đọc hiểu: đoạn văn giữ xuống dòng, phần gạch chân, và bảng của bài 情報検索. */
export function ReadingPassage({ content, className }: { content: string; className?: string }) {
  return (
    <div className={cn('flex flex-col gap-3 font-jp text-lg leading-loose', className)}>
      {blocks(content).map((block, i) =>
        block.kind === 'text' ? (
          <p key={i} className="whitespace-pre-line">
            <UnderlinedText text={block.lines.join('\n')} />
          </p>
        ) : (
          <div key={i} className="overflow-x-auto">
            <table className="w-full border-collapse text-base leading-normal">
              <tbody>
                {block.rows.map((row, r) => (
                  <tr key={r} className={cn(r === 0 && 'bg-muted font-bold')}>
                    {row.map((cell, c) => (
                      <td key={c} className="border-2 border-border px-2 py-1.5 align-top">
                        <UnderlinedText text={cell} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ),
      )}
    </div>
  )
}
