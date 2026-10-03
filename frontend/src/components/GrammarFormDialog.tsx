import { type FormEvent, useState } from 'react'
import { JLPT_LEVELS, type GrammarPoint, type GrammarPointRequest } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { Modal } from '@/components/ui/modal'
import { cn } from '@/lib/utils'

const FORM_ID = 'grammar-form'

/** Thêm hoặc sửa một điểm ngữ pháp. */
export function GrammarFormDialog({
  open,
  editing,
  defaultLevel,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: {
  open: boolean
  editing: GrammarPoint | null
  defaultLevel: string
  submitting: boolean
  errorMessage: string | null
  onSubmit: (payload: GrammarPointRequest) => void
  onCancel: () => void
}) {
  // Trang cha đổi key mỗi lần mở hộp thoại nên form luôn bắt đầu từ dữ liệu mới.
  const [form, setForm] = useState<GrammarPointRequest>(() => ({
    jlptLevel: editing?.jlptLevel ?? defaultLevel,
    lesson: editing?.lesson ?? '',
    pattern: editing?.pattern ?? '',
    connection: editing?.connection ?? '',
    meaningVi: editing?.meaningVi ?? '',
    explanationVi: editing?.explanationVi ?? '',
  }))

  function field<K extends keyof GrammarPointRequest>(key: K, value: GrammarPointRequest[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit(form)
  }

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={editing ? `Sửa "${editing.pattern}"` : 'Thêm điểm ngữ pháp'}
      className="sm:max-w-lg"
      footer={
        <>
          <Button variant="outline" onClick={onCancel}>
            Huỷ
          </Button>
          <Button type="submit" form={FORM_ID} disabled={submitting}>
            {submitting ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Thêm'}
          </Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-4">
        {errorMessage && <Alert>{errorMessage}</Alert>}

        <div className="flex flex-col gap-1.5">
          <Label>Cấp độ</Label>
          <div className="grid grid-cols-5 gap-2">
            {JLPT_LEVELS.map((lv) => (
              <button
                key={lv}
                type="button"
                aria-pressed={form.jlptLevel === lv}
                onClick={() => field('jlptLevel', lv)}
                className={cn(
                  'rounded-xl border-2 py-2 font-black',
                  form.jlptLevel === lv ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border',
                )}
              >
                {lv}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-[2fr_1fr] gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="pattern">Mẫu ngữ pháp</Label>
            <Input
              id="pattern"
              className="font-jp text-lg"
              value={form.pattern}
              onChange={(e) => field('pattern', e.target.value)}
              placeholder="〜てから"
              maxLength={100}
              required
              autoFocus
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="lesson">Bài</Label>
            <Input
              id="lesson"
              value={form.lesson}
              onChange={(e) => field('lesson', e.target.value)}
              placeholder="N4-26"
              maxLength={20}
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="connection">Cách nối</Label>
          <Input
            id="connection"
            className="font-jp"
            value={form.connection}
            onChange={(e) => field('connection', e.target.value)}
            placeholder="Vて + から"
            maxLength={200}
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="meaningVi">Nghĩa tiếng Việt</Label>
          <Input id="meaningVi" value={form.meaningVi} onChange={(e) => field('meaningVi', e.target.value)} required />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="explanationVi">Giải thích thêm</Label>
          <Textarea
            id="explanationVi"
            rows={3}
            value={form.explanationVi}
            onChange={(e) => field('explanationVi', e.target.value)}
          />
        </div>
      </form>
    </Modal>
  )
}
