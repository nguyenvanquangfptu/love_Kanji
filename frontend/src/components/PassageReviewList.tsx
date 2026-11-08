import { useState, type ReactNode } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, CheckCheck, ChevronLeft, ChevronRight, Pencil, RotateCcw, Sparkles, Undo2, X } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import type {
  AdminExamPassage,
  AdminExamQuestion,
  AdminExamQuestionRequest,
  ExamQuestionStatus,
  ExamSectionName,
  JlptQuestionType,
} from '@/api/types'
import { FLAG_LABEL, SOURCE_LABEL, STATUS_BADGE, bulkApprovalSummary, passageDraftSummary, sourceRefLabel } from '@/lib/questionReview'

/**
 * Đoạn văn nhập từ đề tự soạn (mã "N3-05/NP/19-23", bài đọc "N3-05/DH/28-30"): chỗ trống / câu n ghi theo số câu
 * trong đề gốc (19, 20...).
 */
function firstQuestionLabel(sourceRef: string | null): ((blankNo: number) => number) | undefined {
  const first = Number(sourceRef?.split('/')[2]?.split('-')[0])
  return Number.isInteger(first) && first > 0 ? (blankNo) => first + blankNo - 1 : undefined
}
import { cn } from '@/lib/utils'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
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
import { ReadingPassage, UnderlinedText } from '@/components/ReadingPassage'
import { QuestionReports } from '@/components/QuestionReports'
import { QuestionStats } from '@/components/QuestionStats'
import { QuestionEditDialog } from '@/components/QuestionEditDialog'
import { ReviewNoteDialog } from '@/components/ReviewNoteDialog'

const OPTIONS = ['A', 'B', 'C', 'D'] as const
const PAGE_SIZE = 10

/**
 * Đoạn văn của một cấp độ - 文章の文法 (phần Ngữ pháp) hoặc bài đọc (phần Đọc hiểu, lọc thêm theo dạng): duyệt, loại,
 * rút cả đoạn; sửa đoạn văn hoặc từng câu hỏi của đoạn; nhờ AI viết nháp đoạn 文章の文法 mới. Không chọn phần thi
 * thì có cả hai loại (khi lọc theo mã đề tự soạn). Trang cha đổi key khi đổi cấp độ hoặc bộ lọc.
 */
export function PassageReviewList({
  level,
  section,
  type,
  status,
  test,
  emptyExtra,
  quietWhenEmpty = false,
}: {
  level: string
  section?: ExamSectionName
  type?: JlptQuestionType
  status?: ExamQuestionStatus
  /** Chỉ đoạn của một đề tự soạn (mã đề, vd. N3-DH01). */
  test?: string
  /** Gợi ý hiện thêm khi không có đoạn nào (vd. chỗ khác còn đoạn chờ duyệt). */
  emptyExtra?: ReactNode
  /** Không có đoạn nào thì không hiện gì (vd. đề chỉ có câu lẻ, không có đoạn văn). */
  quietWhenEmpty?: boolean
}) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [dialogKey, setDialogKey] = useState(0)
  const [pending, setPending] = useState<{
    passage: AdminExamPassage
    status: 'REJECTED' | 'RETIRED'
  } | null>(null)
  const [editingPassage, setEditingPassage] = useState<AdminExamPassage | null>(null)
  const [editingQuestion, setEditingQuestion] = useState<AdminExamQuestion | null>(null)
  const [editError, setEditError] = useState<string | null>(null)
  const [confirmingBulk, setConfirmingBulk] = useState(false)
  const [confirmingAll, setConfirmingAll] = useState(false)

  const filter = { level, section, type, status, test, page, size: PAGE_SIZE }
  const passagesQuery = useQuery({
    queryKey: ['admin', 'exam-passages', filter],
    queryFn: () => examAdminApi.passages(filter),
    placeholderData: keepPreviousData,
  })
  // Trả về Promise: nút duyệt chỉ nhả ra khi danh sách và bảng thống kê đã tải xong số mới.
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'exam-passages'] }),
      queryClient.invalidateQueries({
        queryKey: ['admin', 'exam-questions', 'stats'],
      }),
    ])

  const statusMutation = useMutation({
    mutationFn: (vars: { id: number; status: ExamQuestionStatus; note?: string }) =>
      examAdminApi.changePassageStatus(vars.id, vars.status, vars.note),
    onSuccess: () => {
      setPending(null)
      return refresh()
    },
  })
  const passageMutation = useMutation({
    mutationFn: (vars: { id: number; title: string; content: string }) =>
      examAdminApi.updatePassage(vars.id, {
        title: vars.title,
        content: vars.content,
      }),
    onSuccess: () => {
      setEditingPassage(null)
      return refresh()
    },
    onError: (err) => setEditError(extractErrorMessage(err)),
  })
  const draftMutation = useMutation({
    mutationFn: () => examAdminApi.draftPassage(level),
    onSuccess: () => refresh(),
  })
  const dismissMutation = useMutation({
    mutationFn: (id: number) => examAdminApi.dismissReports(id),
    onSuccess: () => refresh(),
  })
  const bulkMutation = useMutation({
    mutationFn: (ids: number[]) => examAdminApi.approveAllPassages(ids),
    onSuccess: () => {
      setConfirmingBulk(false)
      // Đoạn đã duyệt rời khỏi danh sách chờ duyệt: quay về trang đầu.
      setPage(0)
      return refresh()
    },
  })
  const approveAllMutation = useMutation({
    mutationFn: () => examAdminApi.approveAllPassagesMatching({ level, section, type, test }),
    onSuccess: () => {
      setConfirmingAll(false)
      setPage(0)
      return refresh()
    },
  })
  const questionMutation = useMutation({
    mutationFn: (vars: { id: number; payload: AdminExamQuestionRequest }) => examAdminApi.update(vars.id, vars.payload),
    onSuccess: () => {
      setEditingQuestion(null)
      return refresh()
    },
    onError: (err) => setEditError(extractErrorMessage(err)),
  })

  if (passagesQuery.isLoading) return <PageSpinner label="Đang tải đoạn văn..." />
  if (passagesQuery.isError || !passagesQuery.data) return <Alert>{extractErrorMessage(passagesQuery.error)}</Alert>
  const data = passagesQuery.data
  // Đoạn có cảnh báo (ở đoạn hay ở câu hỏi) cần xem và duyệt riêng.
  const approvable = data.content.filter(
    (passage) => passage.status === 'DRAFT' && !passage.flag && passage.questions.every((question) => !question.flag),
  )
  const unit = section === 'READING' ? 'bài' : section === 'GRAMMAR' ? 'đoạn' : 'đoạn / bài'
  // Duyệt tất cả chỉ ở danh sách chờ duyệt; đoạn không đạt (cảnh báo, sai cấu trúc) vẫn giữ nguyên.
  const canApproveAll = status === 'DRAFT' && data.totalElements > 0
  const lastResult = approveAllMutation.data ?? bulkMutation.data

  function openDialog(open: () => void) {
    setEditError(null)
    setDialogKey((key) => key + 1)
    open()
  }

  return (
    <div className="flex flex-col gap-4">
      {/* AI chỉ viết nháp đoạn 文章の文法; bài đọc nhập từ file (Nhập đề tự soạn). */}
      {section === 'GRAMMAR' && (
        <Card className="flex flex-col gap-3 p-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="min-w-0 flex-1">
              <p className="font-black">Nhờ AI viết nháp đoạn văn</p>
              <p className="text-sm font-semibold text-muted-foreground">
                Một đoạn văn mới cho đề {level}, đủ số chỗ trống như đề thật. Tốn 2 lượt Gemini: viết nháp và giải lại để kiểm tra.
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
      )}
      {statusMutation.isError && <Alert>{extractErrorMessage(statusMutation.error)}</Alert>}
      {lastResult && (
        <p className="rounded-2xl bg-secondary-soft px-4 py-3 text-sm font-bold text-secondary-dark">
          {bulkApprovalSummary(lastResult, unit)}
        </p>
      )}
      {data.content.length === 0 ? (
        quietWhenEmpty ? null : (
          <Card className="flex flex-col items-center gap-3 p-6 text-center font-semibold text-muted-foreground">
            Không có đoạn văn nào khớp bộ lọc.
            {emptyExtra}
          </Card>
        )
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-bold text-muted-foreground">
              {data.totalElements} {section === 'READING' ? 'bài đọc' : section === 'GRAMMAR' ? 'đoạn văn' : 'đoạn văn / bài đọc'}
            </p>
            <div className="flex flex-wrap gap-2">
              {approvable.length > 0 && (
                <Button size="sm" variant="outline" onClick={() => setConfirmingBulk(true)}>
                  <CheckCheck className="h-4 w-4" /> Duyệt {approvable.length} {unit} không có cảnh báo trên trang
                </Button>
              )}
              {canApproveAll && (
                <Button size="sm" variant="outline" onClick={() => setConfirmingAll(true)}>
                  <CheckCheck className="h-4 w-4" /> Duyệt tất cả {data.totalElements} {unit} chờ duyệt
                </Button>
              )}
            </div>
          </div>
          {data.content.map((passage) => {
            const badge = STATUS_BADGE[passage.status]
            const busy = statusMutation.isPending
            const labelOf = firstQuestionLabel(passage.sourceRef)
            const reading = passage.section === 'READING'
            const passageType = passage.questions[0]?.questionType
            return (
              <Card key={passage.id} className={cn('p-4 sm:p-5', passage.flag && 'border-orange')}>
                <div className="mb-3 flex flex-wrap items-center gap-2">
                  <span className="text-sm font-black text-muted-foreground">Đoạn #{passage.id}</span>
                  <Badge variant={badge.variant}>{badge.label}</Badge>
                  {passage.flag && <Badge variant="orange">⚠ {FLAG_LABEL[passage.flag]}</Badge>}
                  <Badge variant="outline">
                    {passage.sourceRef ? sourceRefLabel(passage.sourceRef) : (SOURCE_LABEL[passage.source] ?? passage.source)}
                  </Badge>
                  {reading && passageType && (
                    <Badge variant="purple">
                      {QUESTION_TYPE_META[passageType].jp} · {QUESTION_TYPE_META[passageType].vi}
                    </Badge>
                  )}
                  <Badge variant="outline">
                    {passage.questions.length} {reading ? 'câu hỏi' : 'chỗ trống'}
                  </Badge>
                </div>
                {passage.title && <h3 className="mb-1 text-center font-jp font-black">{passage.title}</h3>}
                <div className="rounded-2xl bg-muted px-4 py-3">
                  {reading ? (
                    <ReadingPassage content={passage.content} />
                  ) : (
                    <PassageText content={passage.content} labelOf={labelOf} />
                  )}
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
                        <span className="font-black">
                          {reading
                            ? `Câu ${question.blankNo !== null && labelOf ? labelOf(question.blankNo) : question.blankNo}`
                            : `【${question.blankNo}】`}
                        </span>
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
                      {reading && (
                        <p className="mt-2 font-jp font-bold">
                          <UnderlinedText text={question.questionText} />
                        </p>
                      )}
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
                      {question.reviewNote && <p className="mt-1 text-sm font-semibold text-orange-dark">{question.reviewNote}</p>}
                      <QuestionReports
                        reports={question.reports}
                        busy={dismissMutation.isPending}
                        onDismiss={() => dismissMutation.mutate(question.id)}
                      />
                      <QuestionStats stats={question.stats} />
                    </li>
                  ))}
                </ol>

                <div className="mt-4 flex flex-wrap gap-2">
                  {passage.status === 'DRAFT' && (
                    <>
                      <Button
                        size="sm"
                        disabled={busy}
                        onClick={() =>
                          statusMutation.mutate({
                            id: passage.id,
                            status: 'APPROVED',
                          })
                        }
                      >
                        <Check className="h-4 w-4" /> {reading ? 'Duyệt cả bài' : 'Duyệt cả đoạn'}
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
                      onClick={() =>
                        statusMutation.mutate({
                          id: passage.id,
                          status: 'DRAFT',
                        })
                      }
                    >
                      <RotateCcw className="h-4 w-4" /> Đưa về chờ duyệt
                    </Button>
                  )}
                  <Button size="sm" variant="ghost" onClick={() => openDialog(() => setEditingPassage(passage))}>
                    <Pencil className="h-4 w-4" /> {reading ? 'Sửa bài đọc' : 'Sửa đoạn văn'}
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

      <Modal
        open={confirmingAll}
        onClose={() => setConfirmingAll(false)}
        title={`Duyệt tất cả ${data.totalElements} ${unit} chờ duyệt?`}
        description={`Duyệt mọi ${unit} chờ duyệt khớp bộ lọc, kể cả ở các trang khác - chỉ nên làm khi đã đọc kỹ. Đoạn / bài có cảnh báo hoặc chưa hợp lệ sẽ được giữ lại, không duyệt.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmingAll(false)}>
              Huỷ
            </Button>
            <Button disabled={approveAllMutation.isPending} onClick={() => approveAllMutation.mutate()}>
              <CheckCheck className="h-4 w-4" />{' '}
              {approveAllMutation.isPending ? 'Đang duyệt...' : `Duyệt tất cả ${data.totalElements} ${unit}`}
            </Button>
          </>
        }
      >
        {approveAllMutation.isError && <Alert>{extractErrorMessage(approveAllMutation.error)}</Alert>}
      </Modal>
      <Modal
        open={confirmingBulk}
        onClose={() => setConfirmingBulk(false)}
        title={`Duyệt ${approvable.length} ${unit}?`}
        description={`Chỉ duyệt khi đã đọc từng ${unit} trên trang cùng mọi câu hỏi: đáp án đúng, chỉ một đáp án đúng, nội dung đúng cấp độ. Đoạn / bài có cảnh báo không nằm trong lượt này.`}
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmingBulk(false)}>
              Huỷ
            </Button>
            <Button disabled={bulkMutation.isPending} onClick={() => bulkMutation.mutate(approvable.map((passage) => passage.id))}>
              <CheckCheck className="h-4 w-4" /> {bulkMutation.isPending ? 'Đang duyệt...' : `Duyệt ${approvable.length} ${unit}`}
            </Button>
          </>
        }
      >
        {bulkMutation.isError && <Alert>{extractErrorMessage(bulkMutation.error)}</Alert>}
      </Modal>
      <ReviewNoteDialog
        key={`note-${dialogKey}`}
        action={pending?.status ?? null}
        subject={pending?.passage.section === 'READING' ? 'bài đọc' : 'đoạn văn'}
        busy={statusMutation.isPending}
        onConfirm={(note) =>
          pending &&
          statusMutation.mutate({
            id: pending.passage.id,
            status: pending.status,
            note,
          })
        }
        onCancel={() => setPending(null)}
      />
      <PassageEditDialog
        key={`passage-${dialogKey}`}
        reading={editingPassage?.section === 'READING'}
        passage={editingPassage}
        submitting={passageMutation.isPending}
        errorMessage={editError}
        onSubmit={(title, content) => editingPassage && passageMutation.mutate({ id: editingPassage.id, title, content })}
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

/**
 * Sửa tiêu đề và nội dung đoạn văn; 文章の文法: các chỗ trống 【n】 phải giữ nguyên số lượng và số thứ tự; bài đọc: phần
 * gạch chân 〔下線〕...〔/下線〕 phải đóng mở đủ cặp.
 */
function PassageEditDialog({
  reading,
  passage,
  submitting,
  errorMessage,
  onSubmit,
  onCancel,
}: {
  reading: boolean
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
      description={
        reading
          ? 'Gạch chân viết 〔下線〕...〔/下線〕, bảng viết mỗi hàng một dòng | ô | ô |.'
          : 'Giữ đủ các chỗ trống 【1】【2】... - mỗi chỗ đúng một lần.'
      }
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
          <Input id="passage-title" className="font-jp" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="passage-content">Nội dung</Label>
          <Textarea id="passage-content" className="min-h-60 font-jp" value={content} onChange={(e) => setContent(e.target.value)} />
        </div>
      </div>
    </Modal>
  )
}
