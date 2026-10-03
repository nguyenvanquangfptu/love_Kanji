import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, FileUp, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { grammarApi } from '@/api/grammar'
import { extractErrorMessage } from '@/api/client'
import { JLPT_LEVELS, type GrammarPoint, type GrammarPointRequest, type JlptLevel } from '@/api/types'
import { LEVEL_META, lessonTitle } from '@/lib/levels'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { PageSpinner } from '@/components/ui/spinner'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'
import { PageHeader } from '@/components/PageHeader'
import { AdminNav } from '@/components/AdminNav'
import { GrammarFormDialog } from '@/components/GrammarFormDialog'
import { GrammarImportDialog } from '@/components/GrammarImportDialog'

const GRAMMAR_KEY = ['admin', 'grammar']

/** Danh sách điểm ngữ pháp theo cấp độ, nhóm theo bài; nhập/xuất CSV, thêm, sửa, xoá. */
export function AdminGrammarPage() {
  const queryClient = useQueryClient()
  const [level, setLevel] = useState<JlptLevel>('N5')
  const [keyword, setKeyword] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<GrammarPoint | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<GrammarPoint | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  // Đổi mỗi lần mở hộp thoại để form/nội dung CSV bắt đầu lại từ đầu.
  const [dialogKey, setDialogKey] = useState(0)

  const { data, isLoading, isError, error } = useQuery({ queryKey: GRAMMAR_KEY, queryFn: () => grammarApi.list() })
  const refresh = () => queryClient.invalidateQueries({ queryKey: GRAMMAR_KEY })

  const saveMutation = useMutation({
    mutationFn: (payload: GrammarPointRequest) =>
      editing ? grammarApi.update(editing.id, payload) : grammarApi.create(payload),
    onSuccess: () => {
      setFormOpen(false)
      refresh()
    },
    onError: (err) => setFormError(extractErrorMessage(err)),
  })
  const deleteMutation = useMutation({
    mutationFn: (id: number) => grammarApi.delete(id),
    onSuccess: () => {
      setDeleting(null)
      refresh()
    },
  })
  const exportMutation = useMutation({
    mutationFn: () => grammarApi.exportCsv(level),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `ngu-phap-${level.toLowerCase()}.csv`
      link.click()
      URL.revokeObjectURL(url)
    },
  })

  const countByLevel = useMemo(() => {
    const counts = Object.fromEntries(JLPT_LEVELS.map((lv) => [lv, 0])) as Record<JlptLevel, number>
    for (const point of data ?? []) {
      if (point.jlptLevel in counts) counts[point.jlptLevel as JlptLevel]++
    }
    return counts
  }, [data])

  const lessons = useMemo(() => {
    const term = keyword.trim().toLowerCase()
    const groups = new Map<string, GrammarPoint[]>()
    for (const point of data ?? []) {
      if (point.jlptLevel !== level) continue
      const text = `${point.pattern} ${point.meaningVi} ${point.connection ?? ''}`.toLowerCase()
      if (term && !text.includes(term)) continue
      const key = point.lesson ?? ''
      groups.set(key, [...(groups.get(key) ?? []), point])
    }
    return [...groups.entries()]
  }, [data, level, keyword])

  if (isLoading) return <PageSpinner label="Đang tải ngữ pháp..." />
  if (isError || !data) return <Alert>{extractErrorMessage(error)}</Alert>

  const shown = lessons.flatMap(([, points]) => points)
  const approved = shown.reduce((sum, point) => sum + point.approvedQuestions, 0)
  const drafts = shown.reduce((sum, point) => sum + point.draftQuestions, 0)

  function openForm(point: GrammarPoint | null) {
    setEditing(point)
    setFormError(null)
    setDialogKey((key) => key + 1)
    setFormOpen(true)
  }

  return (
    <div>
      <AdminNav />
      <PageHeader
        title="Quản trị ngữ pháp"
        subtitle="Điểm ngữ pháp theo cấp độ - câu thi ngữ pháp được sinh và duyệt theo từng điểm."
        action={
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => {
                setDialogKey((key) => key + 1)
                setImportOpen(true)
              }}>
              <FileUp className="h-4 w-4" /> Nhập CSV
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={countByLevel[level] === 0 || exportMutation.isPending}
              onClick={() => exportMutation.mutate()}
            >
              <Download className="h-4 w-4" /> Xuất CSV {level}
            </Button>
            <Button size="sm" onClick={() => openForm(null)}>
              <Plus className="h-4 w-4" /> Thêm
            </Button>
          </div>
        }
      />

      <div className="mb-4 grid grid-cols-5 gap-2" role="tablist" aria-label="Cấp độ">
        {JLPT_LEVELS.map((lv) => (
          <button
            key={lv}
            type="button"
            role="tab"
            aria-selected={level === lv}
            onClick={() => setLevel(lv)}
            className={cn(
              'flex flex-col items-center rounded-2xl border-2 border-b-4 py-2 transition-all active:translate-y-[2px] active:border-b-2',
              level === lv ? LEVEL_META[lv].style.solid : 'border-border bg-card hover:bg-muted',
            )}
          >
            <span className="text-lg font-black">{lv}</span>
            <span className={cn('text-[11px] font-bold', level === lv ? 'text-white/90' : 'text-muted-foreground')}>
              {countByLevel[lv]} mẫu
            </span>
          </button>
        ))}
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <div className="relative min-w-60 flex-1">
          <Search className="absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-10"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Tìm mẫu, nghĩa..."
            aria-label="Tìm điểm ngữ pháp"
          />
        </div>
        <p className="text-sm font-bold text-muted-foreground">
          {shown.length} mẫu · {approved} câu đã duyệt · {drafts} câu chờ duyệt
        </p>
      </div>

      {lessons.length === 0 ? (
        <Card className="p-6 text-center font-semibold text-muted-foreground">
          {countByLevel[level] === 0 ? `Chưa có điểm ngữ pháp ${level} - nhập từ file CSV hoặc thêm từng mẫu.` : 'Không có mẫu nào khớp.'}
        </Card>
      ) : (
        <div className="flex flex-col gap-4">
          {lessons.map(([lesson, points]) => (
            <Card key={lesson || 'none'} className="p-4 sm:p-5">
              <h2 className="mb-3 font-black">{lesson ? lessonTitle(lesson) : 'Không theo bài'}</h2>
              <ul className="flex flex-col divide-y-2 divide-border">
                {points.map((point) => (
                  <li key={point.id} className="flex flex-wrap items-start gap-x-4 gap-y-1 py-2.5">
                    <div className="min-w-0 flex-1">
                      <p className="font-jp text-lg font-bold">{point.pattern}</p>
                      {point.connection && (
                        <p className="font-jp text-sm font-semibold text-muted-foreground">{point.connection}</p>
                      )}
                      <p className="text-sm font-semibold">{point.meaningVi}</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant={point.approvedQuestions > 0 ? 'success' : 'outline'}>
                        {point.approvedQuestions} đã duyệt
                      </Badge>
                      {point.draftQuestions > 0 && <Badge variant="orange">{point.draftQuestions} chờ duyệt</Badge>}
                      <Button variant="ghost" size="icon" aria-label={`Sửa ${point.pattern}`} onClick={() => openForm(point)}>
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button variant="ghost" size="icon" aria-label={`Xoá ${point.pattern}`} onClick={() => setDeleting(point)}>
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>
      )}

      <GrammarFormDialog
        key={`form-${dialogKey}`}
        open={formOpen}
        editing={editing}
        defaultLevel={level}
        submitting={saveMutation.isPending}
        errorMessage={formError}
        onSubmit={(payload) => saveMutation.mutate(payload)}
        onCancel={() => setFormOpen(false)}
      />
      <GrammarImportDialog key={`import-${dialogKey}`} open={importOpen} onClose={() => setImportOpen(false)} onImported={refresh} />
      <ConfirmDialog
        open={deleting !== null}
        title={`Xoá "${deleting?.pattern}"?`}
        description="Câu thi đã gắn với mẫu này vẫn giữ, chỉ mất liên kết với mẫu."
        confirmLabel="Xoá"
        onConfirm={() => deleting && deleteMutation.mutate(deleting.id)}
        onCancel={() => setDeleting(null)}
      />
    </div>
  )
}
