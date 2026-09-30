import { type FormEvent, useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { JLPT_LEVELS, type KanjiRequest, type KanjiResponse } from '@/api/types'
import { tagApi } from '@/api/tags'
import { groupTagsByLevel } from '@/lib/levels'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert } from '@/components/ui/alert'
import { Modal } from '@/components/ui/modal'
import { cn } from '@/lib/utils'

const FORM_ID = 'kanji-form'

const emptyForm: KanjiRequest = {
  character: '',
  hanViet: '',
  reading: '',
  strokeCount: 0,
  jlptLevel: 'N5',
  meaning: '',
  exampleSentence: '',
  tagIds: [],
}

export function KanjiFormDialog({
  open,
  editing,
  defaultTagIds,
  defaultLevel,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: {
  open: boolean
  editing: KanjiResponse | null
  /** Tag gán sẵn khi tạo mới (vd. đang ở trong một "bộ") - bỏ qua khi editing. */
  defaultTagIds?: number[]
  defaultLevel?: string
  submitting: boolean
  errorMessage: string | null
  onSubmit: (payload: KanjiRequest) => void
  onCancel: () => void
}) {
  const [form, setForm] = useState<KanjiRequest>(emptyForm)

  const { data: allTags } = useQuery({ queryKey: ['tags'], queryFn: tagApi.list, enabled: open })

  // Mảng defaultTagIds thường được tạo mới mỗi lần trang cha render - so sánh theo nội dung
  // để form không bị reset giữa chừng khi người dùng đang nhập.
  const defaultTagKey = (defaultTagIds ?? []).join(',')

  useEffect(() => {
    if (!open) return
    setForm(
      editing
        ? {
            character: editing.character,
            hanViet: editing.hanViet,
            reading: editing.reading ?? '',
            strokeCount: editing.strokeCount,
            jlptLevel: editing.jlptLevel,
            meaning: editing.meaning,
            exampleSentence: editing.exampleSentence ?? '',
            tagIds: editing.tags.map((t) => t.id),
          }
        : {
            ...emptyForm,
            jlptLevel: defaultLevel ?? emptyForm.jlptLevel,
            tagIds: defaultTagKey ? defaultTagKey.split(',').map(Number) : [],
          },
    )
  }, [open, editing, defaultTagKey, defaultLevel])

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit(form)
  }

  function field<K extends keyof KanjiRequest>(key: K, value: KanjiRequest[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function toggleTag(tagId: number) {
    setForm((prev) => ({
      ...prev,
      tagIds: prev.tagIds.includes(tagId) ? prev.tagIds.filter((id) => id !== tagId) : [...prev.tagIds, tagId],
    }))
  }

  const sentenceMissingWord =
    form.exampleSentence.trim() !== '' && form.character.trim() !== '' && !form.exampleSentence.includes(form.character.trim())

  const tagGroups = groupTagsByLevel(allTags ?? [])
  // Chỉ hiện tag cùng cấp độ với từ (và tag đang được chọn) để danh sách 88 tag không bị dài quá.
  const visibleTags = (allTags ?? []).filter(
    (tag) =>
      form.tagIds.includes(tag.id) ||
      tagGroups[form.jlptLevel as keyof typeof tagGroups]?.some((t) => t.id === tag.id) ||
      tagGroups.OTHER.some((t) => t.id === tag.id),
  )

  return (
    <Modal
      open={open}
      onClose={onCancel}
      title={editing ? `Sửa "${editing.character}"` : 'Thêm từ vựng mới'}
      description="Có thể để trống Hán-Việt/Phiên âm nếu từ không có chữ Hán (vd. すっかり)."
      className="sm:max-w-lg"
      footer={
        <>
          <Button variant="outline" onClick={onCancel}>
            Huỷ
          </Button>
          <Button type="submit" form={FORM_ID} disabled={submitting}>
            {submitting ? 'Đang lưu...' : editing ? 'Lưu thay đổi' : 'Tạo mới'}
          </Button>
        </>
      }
    >
      <form id={FORM_ID} onSubmit={handleSubmit} className="flex flex-col gap-4">
        {errorMessage && <Alert>{errorMessage}</Alert>}

        <div className="grid grid-cols-[1fr_auto] gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="character">Từ / chữ Kanji</Label>
            <Input
              id="character"
              className="font-jp text-lg"
              value={form.character}
              onChange={(e) => field('character', e.target.value)}
              maxLength={20}
              required
              autoFocus
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="strokeCount">Số nét</Label>
            <Input
              id="strokeCount"
              type="number"
              min={0}
              className="w-24"
              value={form.strokeCount}
              onChange={(e) => field('strokeCount', Number(e.target.value))}
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="reading">Phiên âm (hiragana)</Label>
            <Input
              id="reading"
              className="font-jp"
              placeholder="vd. だんせい"
              value={form.reading}
              onChange={(e) => field('reading', e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="hanViet">Hán-Việt</Label>
            <Input id="hanViet" placeholder="vd. NAM TÍNH" value={form.hanViet} onChange={(e) => field('hanViet', e.target.value)} />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="meaning">Nghĩa</Label>
          <textarea
            id="meaning"
            value={form.meaning}
            onChange={(e) => field('meaning', e.target.value)}
            required
            rows={2}
            className="w-full rounded-2xl border-2 border-border bg-muted px-4 py-3 text-base font-semibold transition-colors placeholder:text-muted-foreground focus-visible:border-secondary focus-visible:bg-card focus-visible:outline-none"
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="exampleSentence">Câu ví dụ (dùng trong trắc nghiệm)</Label>
          <textarea
            id="exampleSentence"
            value={form.exampleSentence}
            onChange={(e) => field('exampleSentence', e.target.value)}
            rows={2}
            maxLength={500}
            placeholder="Để trống để AI tự tạo khi làm trắc nghiệm"
            className="w-full rounded-2xl border-2 border-border bg-muted px-4 py-3 font-jp text-base transition-colors placeholder:font-sans placeholder:text-sm placeholder:text-muted-foreground focus-visible:border-secondary focus-visible:bg-card focus-visible:outline-none"
          />
          {sentenceMissingWord ? (
            <p className="text-xs font-bold text-orange-dark">
              Câu chưa chứa nguyên văn từ “{form.character}” nên sẽ không được dùng trong trắc nghiệm.
            </p>
          ) : (
            <p className="text-xs font-semibold text-muted-foreground">
              Câu phải chứa nguyên văn từ ở dạng từ điển để gạch chân được. Xoá trống để AI tạo lại.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label>Cấp độ JLPT</Label>
          <div className="grid grid-cols-5 gap-2">
            {JLPT_LEVELS.map((lv) => (
              <button
                key={lv}
                type="button"
                aria-pressed={form.jlptLevel === lv}
                onClick={() => field('jlptLevel', lv)}
                className={cn(
                  'rounded-xl border-2 py-2 text-sm font-black transition-colors',
                  form.jlptLevel === lv
                    ? 'border-secondary bg-secondary-soft text-secondary-dark'
                    : 'border-border hover:bg-muted',
                )}
              >
                {lv}
              </button>
            ))}
          </div>
        </div>

        {visibleTags.length > 0 && (
          <div className="flex flex-col gap-1.5">
            <Label>Thuộc bài</Label>
            <div className="flex max-h-40 flex-wrap gap-2 overflow-y-auto">
              {visibleTags.map((tag) => {
                const checked = form.tagIds.includes(tag.id)
                return (
                  <button
                    key={tag.id}
                    type="button"
                    aria-pressed={checked}
                    onClick={() => toggleTag(tag.id)}
                    className={cn(
                      'rounded-xl border-2 px-3 py-1 text-sm font-bold transition-colors',
                      checked
                        ? 'border-primary bg-primary-soft text-primary-dark'
                        : 'border-border text-muted-foreground hover:bg-muted',
                    )}
                  >
                    {tag.name}
                  </button>
                )
              })}
            </div>
          </div>
        )}
      </form>
    </Modal>
  )
}
