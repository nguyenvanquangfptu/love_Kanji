import { useEffect, useRef, useState } from 'react'
import { toHiragana } from 'wanakana'
import { Check, X } from 'lucide-react'
import type { QuizAnswerResponse, QuizQuestionResponse, ReadingMistake } from '@/api/types'
import { cn } from '@/lib/utils'
import { SpeakButton } from '@/components/SpeakButton'
import { Button } from '@/components/ui/button'

/** Gợi ý theo loại lỗi khi gõ gần đúng - để sửa thói quen gõ, không chỉ báo sai. */
const MISTAKE_HINTS: Record<ReadingMistake, string> = {
  LONG_VOWEL: 'Thiếu hoặc thừa trường âm: âm kéo dài gõ hai nguyên âm (tou, sei, obaa) hoặc dấu - cho ー.',
  SOKUON: 'Thiếu hoặc thừa âm ngắt っ: gõ phụ âm đôi, như kitte, shusshin, matcha.',
  DAKUTEN: 'Sai âm đục: か/が, さ/ざ, は/ば/ぱ... là những âm khác nhau.',
  SMALL_KANA: 'Nhầm âm ghép: しゅ gõ shu, きょ gõ kyo - không tách thành shiyu, kiyo.',
  N_BEFORE_VOWEL: "ん đứng trước nguyên âm gõ n' hoặc nn (han'i), không thì thành hàng な (はに).",
}

/**
 * Ô gõ cách đọc: gõ romaji (xem trước kana ngay bên dưới) hoặc gõ thẳng bằng bàn phím tiếng Nhật; Enter để kiểm tra.
 * Xem trước chỉ để người học thấy mình đang gõ gì - server mới là bên chấm.
 */
export function TypedReadingInput({
  pending,
  disabled,
  onSubmit,
  onGiveUp,
}: {
  pending: boolean
  disabled: boolean
  onSubmit: (typed: string) => void
  onGiveUp: () => void
}) {
  const [value, setValue] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)
  const typed = value.trim()
  const preview = typed ? toHiragana(typed) : ''

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  return (
    <form
      className="mt-8"
      onSubmit={(e) => {
        e.preventDefault()
        if (typed && !disabled && !pending) onSubmit(typed)
      }}
    >
      <input
        ref={inputRef}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        disabled={disabled}
        maxLength={100}
        placeholder="Gõ cách đọc, vd. shusshin"
        aria-label="Cách đọc"
        autoComplete="off"
        autoCapitalize="off"
        autoCorrect="off"
        spellCheck={false}
        className="w-full rounded-2xl border-2 border-b-4 border-border bg-card px-4 py-3 text-xl font-bold outline-none focus:border-secondary disabled:opacity-60"
      />
      <p className="mt-2 min-h-8 font-jp text-2xl font-bold text-secondary-dark" aria-live="polite">
        {preview !== typed ? preview : ''}
      </p>
      {!disabled && (
        <div className="mt-4 flex flex-col gap-3 sm:flex-row">
          <Button type="submit" size="lg" className="flex-1" disabled={!typed || pending}>
            {pending ? 'Đang chấm...' : 'Kiểm tra'}
          </Button>
          <Button type="button" variant="outline" size="lg" className="sm:w-44" disabled={pending} onClick={onGiveUp}>
            Không nhớ
          </Button>
        </div>
      )}
    </form>
  )
}

/** Kết quả một câu gõ: kana server hiểu, cách đọc đúng, gợi ý theo loại lỗi, nghĩa. */
export function TypedReadingFeedback({
  question,
  result,
  praise,
  onContinue,
}: {
  question: QuizQuestionResponse
  result: QuizAnswerResponse
  praise: string
  onContinue: () => void
}) {
  const { correct } = result
  return (
    <div
      role="status"
      className={cn(
        'pb-safe fixed inset-x-0 bottom-0 z-30 animate-slide-up border-t-2',
        correct ? 'border-primary/40 bg-primary-soft' : 'border-destructive/40 bg-destructive-soft',
      )}
    >
      <div className="mx-auto flex max-w-2xl flex-col gap-4 px-4 py-5 sm:flex-row sm:items-center">
        <div className="flex flex-1 items-start gap-3">
          <span
            className={cn(
              'flex h-12 w-12 shrink-0 animate-bounce-in items-center justify-center rounded-full bg-white',
              correct ? 'text-primary' : 'text-destructive',
            )}
          >
            {correct ? <Check className="h-7 w-7" strokeWidth={4} /> : <X className="h-7 w-7" strokeWidth={4} />}
          </span>
          <div className="min-w-0 flex-1">
            <p className={cn('text-xl font-black', correct ? 'text-primary-dark' : 'text-destructive-dark')}>
              {correct ? praise : result.typedKana ? 'Chưa đúng rồi!' : 'Cùng nhớ từ này nhé!'}
            </p>
            {!correct && (
              <p className="font-bold text-destructive-dark">
                {result.typedKana && (
                  <>
                    Bạn gõ: <span className="font-jp">{result.typedKana}</span> →{' '}
                  </>
                )}
                Đúng là: <span className="font-jp">{result.correctAnswer}</span>
              </p>
            )}
            {result.mistake && (
              <p className="mt-2 rounded-xl bg-white/70 px-3 py-2 text-sm font-semibold text-foreground/90">
                {MISTAKE_HINTS[result.mistake]}
              </p>
            )}
            <p className="mt-0.5 text-sm font-semibold text-foreground/80">
              <span className="font-jp font-bold">{question.prompt}</span>
              <span className="font-jp">（{result.correctAnswer}）</span> — {result.meaning}
            </p>
          </div>
          <SpeakButton text={result.correctAnswer ?? question.character} />
        </div>
        <Button variant={correct ? 'default' : 'destructive'} size="lg" className="w-full sm:w-44" onClick={onContinue}>
          Tiếp tục
        </Button>
      </div>
    </div>
  )
}
