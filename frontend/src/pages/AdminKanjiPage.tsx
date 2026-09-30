import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Layers, Pencil, Plus, Search, Tags as TagsIcon, Trash2 } from 'lucide-react'
import { kanjiApi } from '@/api/kanji'
import { tagApi } from '@/api/tags'
import { extractErrorMessage } from '@/api/client'
import type { KanjiRequest, KanjiResponse, TagResponse } from '@/api/types'
import { JLPT_LEVELS } from '@/api/types'
import { LEVEL_META, OTHER_STYLE, groupTagsByLevel, lessonFullTitle, lessonTitle, parseTagName } from '@/lib/levels'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'
import { Badge } from '@/components/ui/badge'
import { Card, CardButton } from '@/components/ui/card'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { KanjiFormDialog } from '@/components/KanjiFormDialog'
import { TagManagerDialog } from '@/components/TagManagerDialog'
import { PageHeader } from '@/components/PageHeader'
import { LevelTabs, type LevelKey } from '@/components/LevelTabs'

const KANJI_LIST_KEY = ['admin', 'kanji']

/** null = đang ở màn hình chọn bài. 'all' = bộ ảo gồm mọi từ vựng (không lọc tag). */
type ActiveBook = TagResponse | 'all' | null

export function AdminKanjiPage() {
  const queryClient = useQueryClient()
  const [activeBook, setActiveBook] = useState<ActiveBook>(null)
  const [gridLevel, setGridLevel] = useState<LevelKey | null>(null)
  const [level, setLevel] = useState<string>('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<KanjiResponse | null>(null)
  const [deleting, setDeleting] = useState<KanjiResponse | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [tagManagerOpen, setTagManagerOpen] = useState(false)

  const { data: tags, isLoading: tagsLoading } = useQuery({ queryKey: ['tags'], queryFn: tagApi.list })

  const groups = useMemo(() => groupTagsByLevel(tags ?? []), [tags])
  const levelKeys: LevelKey[] = groups.OTHER.length > 0 ? [...JLPT_LEVELS, 'OTHER'] : [...JLPT_LEVELS]
  const activeGridLevel: LevelKey =
    gridLevel ??
    levelKeys.find((key) => groups[key].some((tag) => (tag.wordCount ?? 0) > 0)) ??
    levelKeys.find((key) => groups[key].length > 0) ??
    'N5'

  const tagId = activeBook && activeBook !== 'all' ? activeBook.id : undefined
  const bookLevel = activeBook && activeBook !== 'all' ? parseTagName(activeBook.name).level : null

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [...KANJI_LIST_KEY, level, keyword, tagId, page],
    queryFn: () =>
      kanjiApi.search({
        level: level || undefined,
        keyword: keyword || undefined,
        tagId,
        page,
        size: 20,
      }),
    enabled: activeBook !== null,
  })

  function openBook(book: ActiveBook) {
    setActiveBook(book)
    setLevel('')
    setKeyword('')
    setPage(0)
  }

  function invalidateAfterChange() {
    queryClient.invalidateQueries({ queryKey: KANJI_LIST_KEY })
    // Số từ trong mỗi bài hiển thị ở màn hình chọn bài & trang Học bài.
    queryClient.invalidateQueries({ queryKey: ['tags'] })
    queryClient.invalidateQueries({ queryKey: ['study-vocab'] })
  }

  const createMutation = useMutation({
    mutationFn: kanjiApi.create,
    onSuccess: () => {
      invalidateAfterChange()
      setFormOpen(false)
    },
    onError: (err) => setFormError(extractErrorMessage(err)),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: KanjiRequest }) => kanjiApi.update(id, payload),
    onSuccess: () => {
      invalidateAfterChange()
      setFormOpen(false)
    },
    onError: (err) => setFormError(extractErrorMessage(err)),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => kanjiApi.delete(id),
    onSuccess: () => {
      invalidateAfterChange()
      setDeleting(null)
    },
  })

  function openCreate() {
    setEditing(null)
    setFormError(null)
    setFormOpen(true)
  }

  function openEdit(kanji: KanjiResponse) {
    setEditing(kanji)
    setFormError(null)
    setFormOpen(true)
  }

  function handleFormSubmit(payload: KanjiRequest) {
    if (editing) {
      updateMutation.mutate({ id: editing.id, payload })
    } else {
      createMutation.mutate(payload)
    }
  }

  const tagManagerButton = (
    <Button variant="outline" size="sm" onClick={() => setTagManagerOpen(true)}>
      <TagsIcon className="h-4 w-4" /> Quản lý bài
    </Button>
  )

  if (activeBook === null) {
    const lessons = groups[activeGridLevel]
    const style = activeGridLevel === 'OTHER' ? OTHER_STYLE : LEVEL_META[activeGridLevel].style
    return (
      <div>
        <PageHeader title="Quản trị từ vựng" subtitle="Chọn một bài để xem, thêm và chỉnh sửa từ." action={tagManagerButton} />

        <CardButton onClick={() => openBook('all')} className="mb-6 flex w-full items-center gap-4 p-4">
          <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border-b-4 border-secondary-dark bg-secondary text-white">
            <Layers className="h-6 w-6" strokeWidth={2.5} />
          </span>
          <span className="flex-1">
            <span className="block font-extrabold">Tất cả từ vựng</span>
            <span className="block text-sm font-semibold text-muted-foreground">Tìm kiếm & sửa mọi từ, không lọc theo bài</span>
          </span>
          <ChevronRight className="h-5 w-5 text-muted-foreground" />
        </CardButton>

        {tagsLoading ? (
          <PageSpinner />
        ) : (
          <>
            <LevelTabs
              levels={levelKeys}
              active={activeGridLevel}
              onChange={setGridLevel}
              lessonCounts={Object.fromEntries(levelKeys.map((key) => [key, groups[key].length])) as Record<LevelKey, number>}
            />
            {lessons.length === 0 ? (
              <p className="py-10 text-center font-semibold text-muted-foreground">
                Chưa có bài nào cho cấp độ này. Tạo bài mới bằng nút "Quản lý bài".
              </p>
            ) : (
              <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                {lessons.map((tag) => {
                  const count = tag.wordCount ?? 0
                  const { lesson } = parseTagName(tag.name)
                  return (
                    <CardButton key={tag.id} onClick={() => openBook(tag)} className="flex items-center gap-3 p-3">
                      <span
                        className={cn(
                          'flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border-b-4 font-black',
                          count > 0 ? style.solid : 'border-border-strong bg-border text-muted-foreground',
                        )}
                      >
                        {lesson ?? '#'}
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate font-extrabold">{lessonTitle(tag.name)}</span>
                        <span className="block text-xs font-bold text-muted-foreground">
                          {count > 0 ? `${count} từ` : 'Trống'}
                        </span>
                      </span>
                    </CardButton>
                  )
                })}
              </div>
            )}
          </>
        )}

        <TagManagerDialog open={tagManagerOpen} onClose={() => setTagManagerOpen(false)} />
      </div>
    )
  }

  return (
    <div>
      <button
        type="button"
        onClick={() => setActiveBook(null)}
        className="mb-4 inline-flex items-center gap-1 rounded-xl py-1 pr-2 text-sm font-extrabold text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-5 w-5" />
        Chọn bài khác
      </button>

      <PageHeader
        title={activeBook === 'all' ? 'Tất cả từ vựng' : lessonFullTitle(activeBook.name)}
        subtitle={data ? `${data.totalElements} từ vựng` : undefined}
        action={
          <div className="flex gap-2">
            {tagManagerButton}
            <Button size="sm" onClick={openCreate}>
              <Plus className="h-4 w-4" /> Thêm từ
            </Button>
          </div>
        }
      />

      <div className="mb-4 flex flex-col gap-2 sm:flex-row">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Tìm theo chữ, phiên âm, Hán-Việt, nghĩa..."
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value)
              setPage(0)
            }}
            className="pl-11"
          />
        </div>
        <select
          value={level}
          onChange={(e) => {
            setLevel(e.target.value)
            setPage(0)
          }}
          aria-label="Lọc theo cấp độ"
          className="h-12 rounded-2xl border-2 border-border bg-muted px-4 font-bold focus-visible:border-secondary focus-visible:outline-none"
        >
          <option value="">Tất cả cấp độ</option>
          {JLPT_LEVELS.map((lv) => (
            <option key={lv} value={lv}>
              {lv}
            </option>
          ))}
        </select>
      </div>

      {isLoading && <PageSpinner />}
      {isError && <Alert>{extractErrorMessage(error)}</Alert>}

      {data && (
        <Card className="overflow-hidden">
          <div className="divide-y-2 divide-border">
            {data.content.map((kanji) => (
              <div key={kanji.id} className="flex items-center gap-3 px-4 py-3">
                <div className="flex w-20 shrink-0 flex-col items-center sm:w-24">
                  {kanji.reading && (
                    <span className="max-w-full truncate font-jp text-xs font-bold text-secondary-dark">{kanji.reading}</span>
                  )}
                  <span className="max-w-full truncate font-jp text-2xl font-bold">{kanji.character}</span>
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    {kanji.hanViet && (
                      <span className="truncate text-xs font-extrabold uppercase tracking-wide text-muted-foreground">
                        {kanji.hanViet}
                      </span>
                    )}
                    <Badge variant="secondary">{kanji.jlptLevel}</Badge>
                  </div>
                  <p className="line-clamp-2 text-sm font-semibold">{kanji.meaning}</p>
                  {kanji.exampleSentence && (
                    <p className="mt-0.5 line-clamp-1 font-jp text-xs text-muted-foreground" title={kanji.exampleSentence}>
                      {kanji.exampleSentence}
                    </p>
                  )}
                  {kanji.tags.length > 0 && (
                    <div className="mt-1 hidden flex-wrap gap-1 sm:flex">
                      {kanji.tags.map((tag) => (
                        <Badge key={tag.id} variant="outline">
                          {tag.name}
                        </Badge>
                      ))}
                    </div>
                  )}
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button variant="ghost" size="icon" onClick={() => openEdit(kanji)} aria-label={`Sửa ${kanji.character}`}>
                    <Pencil className="h-5 w-5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => setDeleting(kanji)}
                    aria-label={`Xoá ${kanji.character}`}
                    className="hover:bg-destructive-soft hover:text-destructive"
                  >
                    <Trash2 className="h-5 w-5" />
                  </Button>
                </div>
              </div>
            ))}
            {data.content.length === 0 && (
              <p className="px-6 py-12 text-center font-semibold text-muted-foreground">
                Không có từ nào. Bấm "Thêm từ" để thêm vào bài này.
              </p>
            )}
          </div>
        </Card>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-center gap-3">
          <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
            <ChevronLeft className="h-4 w-4" /> Trước
          </Button>
          <span className="text-sm font-extrabold text-muted-foreground">
            Trang {data.number + 1}/{data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
            Sau <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}

      <KanjiFormDialog
        open={formOpen}
        editing={editing}
        defaultTagIds={tagId ? [tagId] : []}
        defaultLevel={bookLevel ?? undefined}
        submitting={createMutation.isPending || updateMutation.isPending}
        errorMessage={formError}
        onSubmit={handleFormSubmit}
        onCancel={() => setFormOpen(false)}
      />

      <ConfirmDialog
        open={deleting !== null}
        title={`Xoá "${deleting?.character}"?`}
        description="Hành động này xoá luôn toàn bộ tiến độ ôn tập (SRS) của MỌI người dùng đã học từ này. Không thể hoàn tác."
        confirmLabel="Xoá"
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
        onCancel={() => setDeleting(null)}
      />

      <TagManagerDialog open={tagManagerOpen} onClose={() => setTagManagerOpen(false)} />
    </div>
  )
}
