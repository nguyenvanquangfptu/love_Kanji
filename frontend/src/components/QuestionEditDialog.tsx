import { type FormEvent, useState } from 'react'
import type { AdminExamQuestion, AdminExamQuestionRequest } from '@/api/types'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { Modal } from '@/components/ui/modal'
import { Textarea } from '@/components/ui/textarea'

const FORM_ID = 'question-edit-form'
const LETTERS = ['A', 'B', 'C', 'D'] as const
type Letter = (typeof LETTERS)[number]

/** Dạng câu có phần gạch chân (highlight) trong câu. */
const UNDERLINED = new Set(['KANJI_READING', 'ORTHOGRAPHY', 'PARAPHRASE'])

/** Sửa nội dung một câu thi trên trang duyệt; trang cha đổi key mỗi lần mở. */
export function QuestionEditDialog({
  question,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: {
  question: AdminExamQuestion | null
  submitting: boolean
  errorMessage: string | null
  onSubmit: (payload: AdminExamQuestionRequest) => void
  onCancel: () => void
}) {
  const [form, setForm] = useState<AdminExamQuestionRequest>(() => ({
    questionText: question?.questionText ?? '',
    sentence: question?.sentence ?? '',
    highlight: question?.highlight ?? '',
    optionA: question?.optionA ?? '',
    optionB: question?.optionB ?? '',
    optionC: question?.optionC ?? '',
    optionD: question?.optionD ?? '',
    correctOption: question?.correctOption ?? 'A',
    explanation: question?.explanation ?? '',
  }))

  function field<K extends keyof AdminExamQuestionRequest>(key: K, value: AdminExamQuestionRequest[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit(form)
  }

  const type = question?.questionType

  return (
    <Modal
      open={question !== null}
      onClose={onCancel}
      title={question ? `Sửa câu #${question.id}` : 'Sửa câu'}
      description={type ? `${QUESTION_TYPE_META[type].jp} · ${QUESTION_TYPE_META[type].vi}` : undefined}
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="outline" onClick={onCancel}>
            Huỷ
          </Button>
          <Button type="submit" form={FORM_ID} disabled={submitting}>
            {submitting ? 'Đang lưu...' : 'Lưu'}
          </Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-4">
        {errorMessage && <Alert>{errorMessage}</Alert>}

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="questionText">Câu hỏi</Label>
          <Input
            id="questionText"
            value={form.questionText}
            onChange={(e) => field('questionText', e.target.value)}
            required
          />
        </div>

        {type !== 'USAGE' && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sentence">
              Câu trong đề{' '}
              <span className="font-semibold text-muted-foreground">
                {type === 'SENTENCE_ORDER' ? '(＿＿＿ cho ô trống, ＿★＿ cho ô hỏi)' : '(（　　） cho chỗ trống)'}
              </span>
            </Label>
            <Textarea
              id="sentence"
              className="font-jp"
              rows={2}
              value={form.sentence}
              onChange={(e) => field('sentence', e.target.value)}
            />
          </div>
        )}

        {type && UNDERLINED.has(type) && (
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="highlight">Phần gạch chân</Label>
            <Input
              id="highlight"
              className="font-jp"
              value={form.highlight}
              onChange={(e) => field('highlight', e.target.value)}
              maxLength={100}
            />
          </div>
        )}

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1.5 text-sm font-bold">Lựa chọn (chọn ô tròn ở đáp án đúng)</legend>
          {LETTERS.map((letter) => {
            const key = `option${letter}` as const
            return (
              <div key={letter} className="flex items-center gap-2">
                <label
                  className={cn(
                    'flex h-12 w-12 shrink-0 cursor-pointer items-center justify-center rounded-xl border-2 font-black',
                    form.correctOption === letter ? 'border-primary bg-primary-soft text-primary-dark' : 'border-border',
                  )}
                >
                  <input
                    type="radio"
                    name="correctOption"
                    className="sr-only"
                    checked={form.correctOption === letter}
                    onChange={() => field('correctOption', letter as Letter)}
                  />
                  {letter}
                </label>
                <Input
                  className="font-jp"
                  aria-label={`Lựa chọn ${letter}`}
                  value={form[key]}
                  onChange={(e) => field(key, e.target.value)}
                  maxLength={255}
                  required
                />
              </div>
            )
          })}
        </fieldset>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="explanation">Giải thích</Label>
          <Textarea
            id="explanation"
            rows={3}
            value={form.explanation}
            onChange={(e) => field('explanation', e.target.value)}
          />
        </div>
      </form>
    </Modal>
  )
}
