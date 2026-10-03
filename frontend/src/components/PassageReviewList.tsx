import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, ChevronLeft, ChevronRight, Pencil, RotateCcw, Sparkles, Undo2, X } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import type { AdminExamPassage, AdminExamQuestion, AdminExamQuestionRequest, ExamQuestionStatus } from '@/api/types'
import { FLAG_LABEL, SOURCE_LABEL, STATUS_BADGE, passageDraftSummary } from '@/lib/questionReview'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Modal } from '@/components/ui/modal'
import { Textarea } from '@/components/ui/textarea'
import { PageSpinner } from '@/components/ui/spinner'
import { PassageText } from '@/components/PassageText'
import { QuestionEditDialog } from '@/components/QuestionEditDialog'
import { ReviewNoteDialog } from '@/components/ReviewNoteDialog'

const OPTIONS = ['A', 'B', 'C', 'D'] as const
const PAGE_SIZE = 10

/**
 * Đoạn văn 文章の文法 của một cấp độ: duyệt, loại, rút cả đoạn; sửa đoạn văn hoặc từng câu hỏi của đoạn; nhờ AI viết
 * nháp đoạn mới. Trang cha đổi key khi đổi cấp độ.
 */
export function PassageReviewList({ level, status }: { level: string; status?: ExamQuestionStatus }) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [dialogKey, setDialogKey] = useState(0)
  const [pending, setPending] = useState<{ passage: AdminExamPassage; status: 'REJECTED' | 'RETIRED' } | null>(null)
  const [editingPassage, setEditingPassage] = useState<AdminExamPassage | null>(null)
  const [editingQuestion, setEditingQuestion] = useState<AdminExamQuestion | null>(null)
  const [editError, setEditError] = useState<string | null>(null)

  const filter = { level, status, page, size: PAGE_SIZE }
  const passagesQuery = useQuery({
    queryKey: ['admin', 'exam-passages', filter],
    queryFn: () => examAdminApi.passages(filter),
    placeholderData: keepPreviousData,
  })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'exam-passages'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'exam-questions', 'stats'] })
  }

  const statusMutation = useMutation({
    mutationFn: (vars: { id: number; status: ExamQuestionStatus; note?: string }) =>
      examAdminApi.changePassageStatus(vars.id, vars.status, vars.note),
    onSuccess: () => {
      setPending(null)
      refresh()
    },
  })
  const passageMutation = useMutation({
    mutationFn: (vars: { id: number; title: string; content: string }) =>
      examAdminApi.updatePassage(vars.id, { title: vars.title, content: vars.content }),
    onSuccess: () => {
      setEditingPassage(null)
      refresh()
    },
    onError: (err) => setEditError(extractErrorMessage(err)),
  })
  const draftMutation = useMutation({
    mutationFn: () => examAdminApi.draftPassage(level),
    onSuccess: refresh,
  })
  const questionMutation = useMutation({
    mutationFn: (vars: { id: number; payload: AdminExamQuestionRequest }) => examAdminApi.update(vars.id, vars.payload),
    onSuccess: () => {
      setEditingQuestion(null)
      refresh()
    },
    onError: (err) => setEditError(extractErrorMessage(err)),
  })

  if (passagesQuery.isLoading) return <PageSpinner label="Đang tải đoạn văn..." />
  if (passagesQuery.isError || !passagesQuery.data) return <Alert>{extractErrorMessage(passagesQuery.error)}</Alert>
  const data = passagesQuery.data

  function openDialog(open: () => void) {
    setEditError(null)
    setDialogKey((key) => key + 1)
    open()
  }

  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col gap-3 p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="min-w-0 flex-1">
            <p className="font-black">Nhờ AI viết nháp đoạn văn</p>
            <p className="text-sm font-semibold text-muted-foreground">
              Một đoạn văn mới cho đề {level}, đủ số chỗ trống như đề thật. Tốn 2 lượt Gemini: viết nháp và giải lại
              để kiểm tra.
            </p>
          </div>
          <Button disabled={draftMutation.isPending} onClick={() => draftMutation.mutate()}>
            <Sparkles className="h-4 w-4" /> {draftMutation.isPending ? 'AI đang viết...' : 'Sinh đoạn văn'}
          </Button>
        </div>
        {draftMutation.isError && <Alert>{extractErrorMessage(draftMutation.error)}</Alert>}
        {draftMutation.data && (
          <p className="rounded-2xl bg-secondary-soft px-4 py-3 text-sm font-bold text-secondary-dark">
            {passageDraftSummary(draftMutation.data)}
          </p>
        )}
      </Card>
      {statusMutation.isError && <Alert>{extractErrorMessage(statusMutation.error)}</Alert>}
      {data.content.length === 0 ? (
        <Card className="p-6 text-center font-semibold text-muted-foreground">Không có đoạn văn nào khớp bộ lọc.</Card>
      ) : (
        <>
          <p className="text-sm font-bold text-muted-foreground">{data.totalElements} đoạn văn</p>
          {data.content.map((passage) => {
            const badge = STATUS_BADGE[passage.status]
            const busy = statusMutation.isPending
            return (
              <Card key={passage.id} className={cn('p-4 sm:p-5', passage.flag && 'border-orange')}>
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <span className="text-sm font-black text-muted-foreground">Đoạn #{passage.id}</span>
                  <Badge variant={badge.variant}>{badge.label}</Badge>
                  {passage.flag && <Badge variant="orange">⚠ {FLAG_LABEL[passage.flag]}</Badge>}
                  <Badge variant="outline">{SOURCE_LABEL[passage.source] ?? passage.source}</Badge>
                  <Badge variant="outline">{passage.questions.length} chỗ trống</Badge>
                </div>
                {passage.title && <h3 className="mb-1 text-center font-jp font-black">{passage.title}</h3>}
                <div className="rounded-2xl bg-muted px-4 py-3">
                  <PassageText content={passage.content} />
                </div>
                {passage.reviewNote && (
                  <p
                    className={cn(
                      'mt-2 rounded-xl px-3 py-2 text-sm font-semibold',
                      passage.flag ? 'bg-orange-soft text-orange-dark' : 'bg-muted text-muted-foreground',
                    )}
                  >
                    {passage.reviewNote}
                  </p>
                )}

                <ol className="mt-3 flex flex-col gap-3">
                  {passage.questions.map((question) => (
                    <li key={question.id} className="rounded-2xl border-2 border-border p-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-black">【{question.blankNo}】</span>
                        {question.flag && <Badge variant="orange">⚠ {FLAG_LABEL[question.flag]}</Badge>}
                        <Button
                          size="sm"
                          variant="ghost"
                          className="ml-auto"
                          onClick={() => openDialog(() => setEditingQuestion(question))}
                        >
                          <Pencil className="h-4 w-4" /> Sửa câu
                        </Button>
                      </div>
                      <div className="mt-2 grid gap-2 sm:grid-cols-2">
                        {OPTIONS.map((opt) => (
                          <div
                            key={opt}
                            className={cn(
                              'rounded-xl border-2 px-3 py-1.5 font-jp font-bold',
                              question.correctOption === opt
                                ? 'border-primary bg-primary-soft text-primary-dark'
                                : 'border-border text-muted-foreground',
                            )}
                          >
                            <span className="font-sans font-black">{opt}.</span> {question[`option${opt}`]}
                          </div>
                        ))}
                      </div>
                      {question.explanation && (
                        <p className="mt-2 text-sm font-semibold text-secondary-dark">{question.explanation}</p>
                      )}
                      {question.reviewNote && (
                        <p className="mt-1 text-sm font-semibold text-orange-dark">{question.reviewNote}</p>
                      )}
                    </li>
                  ))}
                </ol>

                <div className="mt-4 flex flex-wrap gap-2">
                  {passage.status === 'DRAFT' && (
                    <>
                      <Button
                        size="sm"
                        disabled={busy}
                        onClick={() => statusMutation.mutate({ id: passage.id, status: 'APPROVED' })}
                      >
                        <Check className="h-4 w-4" /> Duyệt cả đoạn
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={busy}
                        onClick={() => openDialog(() => setPending({ passage, status: 'REJECTED' }))}
                      >
                        <X className="h-4 w-4" /> Loại
                      </Button>
                    </>
                  )}
                  {passage.status === 'APPROVED' && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={busy}
                      onClick={() => openDialog(() => setPending({ passage, status: 'RETIRED' }))}
                    >
                      <Undo2 className="h-4 w-4" /> Rút khỏi đề
                    </Button>
                  )}
                  {(passage.status === 'REJECTED' || passage.status === 'RETIRED') && (
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={busy}
                      onClick={() => statusMutation.mutate({ id: passage.id, status: 'DRAFT' })}
                    >
                      <RotateCcw className="h-4 w-4" /> Đưa về chờ duyệt
                    </Button>
                  )}
                  <Button size="sm" variant="ghost" onClick={() => openDialog(() => setEditingPassage(passage))}>
                    <Pencil className="h-4 w-4" /> Sửa đoạn văn
                  </Button>
                </div>
              </Card>
            )
          })}
          {data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-3">
              <Button variant="outline" size="sm" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
                <ChevronLeft className="h-4 w-4" /> Trước
              </Button>
              <span className="text-sm font-bold">
                {page + 1} / {data.totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
                Sau <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          )}
        </>
      )}

      <ReviewNoteDialog
        key={`note-${dialogKey}`}
        action={pending?.status ?? null}
        subject="đoạn văn"
        busy={statusMutation.isPending}
        onConfirm={(note) => pending && statusMutation.mutate({ id: pending.passage.id, status: pending.status, note })}
        onCancel={() => setPending(null)}
      />
      <PassageEditDialog
        key={`passage-${dialogKey}`}
        passage={editingPassage}
        submitting={passageMutation.isPending}
        errorMessage={editError}
        onSubmit={(title, content) =>
          editingPassage && passageMutation.mutate({ id: editingPassage.id, title, content })
        }
        onCancel={() => setEditingPassage(null)}
      />
      <QuestionEditDialog
        key={`question-${dialogKey}`}
        question={editingQuestion}
        submitting={questionMutation.isPending}
        errorMessage={editError}
        onSubmit={(payload) => editingQuestion && questionMutation.mutate({ id: editingQuestion.id, payload })}
        onCancel={() => setEditingQuestion(null)}
      />
    </div>
  )
}

/** Sửa tiêu đề và nội dung đoạn văn; các chỗ trống 【n】 phải giữ nguyên số lượng và số thứ tự. */
function PassageEditDialog({
  passage,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: {
  passage: AdminExamPassage | null
  submitting: boolean
  errorMessage: string | null
  onSubmit: (title: string, content: string) => void
  onCancel: () => void
}) {
  const [title, setTitle] = useState(passage?.title ?? '')
  const [content, setContent] = useState(passage?.content ?? '')
  return (
    <Modal
      open={passage !== null}
      onClose={onCancel}
      title={passage ? `Sửa đoạn văn #${passage.id}` : 'Sửa đoạn văn'}
      description="Giữ đủ các chỗ trống 【1】【2】... - mỗi chỗ đúng một lần."
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="outline" onClick={onCancel}>
            Huỷ
          </Button>
          <Button disabled={submitting || !content.trim()} onClick={() => onSubmit(title, content)}>
            {submitting ? 'Đang lưu...' : 'Lưu'}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {errorMessage && <Alert>{errorMessage}</Alert>}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="passage-title">Tiêu đề</Label>
          <Input
            id="passage-title"
            className="font-jp"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="passage-content">Nội dung</Label>
          <Textarea
            id="passage-content"
            className="min-h-60 font-jp"
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
        </div>
      </div>
    </Modal>
  )
}
