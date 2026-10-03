import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, CheckCheck, ChevronLeft, ChevronRight, Pencil, RotateCcw, Undo2, X } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import {
  JLPT_LEVELS,
  type AdminExamQuestion,
  type AdminExamQuestionRequest,
  type ExamQuestionStatus,
  type JlptLevel,
  type JlptQuestionType,
} from '@/api/types'
import { LEVEL_META } from '@/lib/levels'
import { QUESTION_TYPE_META, SECTION_META } from '@/lib/jlpt'
import { FLAG_LABEL, SOURCE_LABEL, STATUS_BADGE } from '@/lib/questionReview'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Modal } from '@/components/ui/modal'
import { PageSpinner } from '@/components/ui/spinner'
import { PageHeader } from '@/components/PageHeader'
import { AdminNav } from '@/components/AdminNav'
import { SentenceWithTarget } from '@/components/SentenceWithTarget'
import { QuestionEditDialog } from '@/components/QuestionEditDialog'
import { ReviewNoteDialog } from '@/components/ReviewNoteDialog'
import { PassageReviewList } from '@/components/PassageReviewList'
import { QuestionReports } from '@/components/QuestionReports'
import { VocabularyDraftPanel } from '@/components/VocabularyDraftPanel'

const STATUS_TABS: { value: ExamQuestionStatus | ''; label: string }[] = [
  { value: 'DRAFT', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã loại' },
  { value: 'RETIRED', label: 'Đã rút' },
  { value: '', label: 'Tất cả' },
]

const OPTIONS = ['A', 'B', 'C', 'D'] as const
/** Dạng câu từ vựng nhờ AI viết nháp được theo từ trong bài. */
const VOCABULARY_DRAFT_TYPES: JlptQuestionType[] = ['PARAPHRASE', 'USAGE']
const PAGE_SIZE = 20

/** Một thao tác cần ghi lý do: loại câu (bắt buộc) hoặc rút khỏi đề. */
interface PendingNote {
  question: AdminExamQuestion
  status: 'REJECTED' | 'RETIRED'
}

/** Duyệt câu thi đề JLPT: lọc, xem câu đúng như trong đề, sửa, duyệt / loại / rút; thống kê đủ bao nhiêu đề. */
export function AdminQuestionsPage() {
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const level = (params.get('level') as JlptLevel | null) ?? 'N5'
  const type = (params.get('type') as JlptQuestionType | null) ?? undefined
  const status = params.has('status') ? (params.get('status') as ExamQuestionStatus | '') : 'DRAFT'
  const flagged = params.get('flagged') === 'true'
  const reported = params.get('reported') === 'true'
  const grammarPointId = params.get('grammarPointId') ? Number(params.get('grammarPointId')) : undefined
  const page = Number(params.get('page') ?? 0)

  const [editing, setEditing] = useState<AdminExamQuestion | null>(null)
  const [editKey, setEditKey] = useState(0)
  const [editError, setEditError] = useState<string | null>(null)
  const [pendingNote, setPendingNote] = useState<PendingNote | null>(null)
  const [noteKey, setNoteKey] = useState(0)
  const [confirmingBulk, setConfirmingBulk] = useState(false)

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === undefined) next.delete(key)
      else next.set(key, value)
    }
    if (!('page' in changes)) next.delete('page')
    setParams(next, { replace: true })
  }

  const filter = { level, type, status: status || undefined, flagged, reported, grammarPointId, page, size: PAGE_SIZE }
  const questionsQuery = useQuery({
    queryKey: ['admin', 'exam-questions', filter],
    queryFn: () => examAdminApi.search(filter),
    placeholderData: keepPreviousData,
    // 文章の文法 duyệt theo cả đoạn văn, ở danh sách đoạn văn.
    enabled: type !== 'TEXT_GRAMMAR',
  })
  const statsQuery = useQuery({ queryKey: ['admin', 'exam-questions', 'stats'], queryFn: examAdminApi.stats })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'exam-questions'] })

  const statusMutation = useMutation({
    mutationFn: (vars: { id: number; status: ExamQuestionStatus; note?: string }) =>
      examAdminApi.changeStatus(vars.id, vars.status, vars.note),
    onSuccess: () => {
      setPendingNote(null)
      refresh()
    },
  })
  const dismissMutation = useMutation({
    mutationFn: (id: number) => examAdminApi.dismissReports(id),
    onSuccess: refresh,
  })
  const bulkMutation = useMutation({
    mutationFn: (ids: number[]) => examAdminApi.approveAll(ids),
    onSuccess: () => {
      setConfirmingBulk(false)
      // Câu đã duyệt rời khỏi danh sách chờ duyệt: quay về trang đầu.
      update({})
      refresh()
    },
  })
  const editMutation = useMutation({
    mutationFn: (vars: { id: number; payload: AdminExamQuestionRequest }) => examAdminApi.update(vars.id, vars.payload),
    onSuccess: () => {
      setEditing(null)
      refresh()
    },
    onError: (err) => setEditError(extractErrorMessage(err)),
  })

  const levelStats = statsQuery.data?.find((stats) => stats.jlptLevel === level)
  // Chỉ nhờ AI viết dạng câu có trong đề của cấp độ (đề N5 không có 用法).
  const canDraftVocabulary =
    type !== undefined &&
    VOCABULARY_DRAFT_TYPES.includes(type) &&
    (levelStats?.types.some((row) => row.type === type) ?? false)
  const data = questionsQuery.data
  // Duyệt cả trang: chỉ câu chờ duyệt không có cảnh báo (câu có cảnh báo cần xem riêng).
  const approvable = data?.content.filter((question) => question.status === 'DRAFT' && !question.flag) ?? []
  const grammarLabel = data?.content.flatMap((q) => q.grammarPoints).find((g) => g.id === grammarPointId)?.pattern

  return (
    <div>
      <AdminNav />
      <PageHeader title="Duyệt câu hỏi thi" subtitle="Chỉ câu đã duyệt mới được lấy vào đề JLPT." />

      <div className="mb-4 grid grid-cols-5 gap-2" role="tablist" aria-label="Cấp độ">
        {JLPT_LEVELS.map((lv) => (
          <button
            key={lv}
            type="button"
            role="tab"
            aria-selected={level === lv}
            onClick={() => update({ level: lv, type: undefined, grammarPointId: undefined })}
            className={cn(
              'rounded-2xl border-2 border-b-4 py-2 text-lg font-black transition-all active:translate-y-[2px] active:border-b-2',
              level === lv ? LEVEL_META[lv].style.solid : 'border-border bg-card hover:bg-muted',
            )}
          >
            {lv}
          </button>
        ))}
      </div>

      {levelStats && (
        <Card className="mb-4 overflow-x-auto p-4">
          <h2 className="mb-2 font-black">Ngân hàng câu {level}</h2>
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead className="text-xs font-extrabold uppercase text-muted-foreground">
              <tr>
                <th className="py-1.5">Dạng câu</th>
                <th className="py-1.5 text-right">1 đề</th>
                <th className="py-1.5 text-right">Đã duyệt</th>
                <th className="py-1.5 text-right">Chờ duyệt</th>
                <th className="py-1.5 text-right">Đã loại</th>
                <th className="py-1.5 text-right">Đủ cho</th>
              </tr>
            </thead>
            <tbody>
              {levelStats.types.map((row) => (
                <tr key={row.type} className="border-t-2 border-border">
                  <td className="py-1.5">
                    <button
                      type="button"
                      className="text-left font-bold hover:underline"
                      onClick={() => update({ type: row.type })}
                    >
                      <span className="font-jp">{QUESTION_TYPE_META[row.type].jp}</span>
                      <span className="text-muted-foreground"> · {SECTION_META[row.section].vi}</span>
                    </button>
                  </td>
                  <td className="py-1.5 text-right tabular-nums">{row.perExam}</td>
                  <td className="py-1.5 text-right font-bold tabular-nums">{row.approved}</td>
                  <td className="py-1.5 text-right tabular-nums">{row.draft}</td>
                  <td className="py-1.5 text-right tabular-nums">{row.rejected}</td>
                  <td
                    className={cn('py-1.5 text-right font-black tabular-nums', row.exams === 0 && 'text-orange-dark')}
                  >
                    {row.exams} đề
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap gap-1.5" role="tablist" aria-label="Trạng thái">
          {STATUS_TABS.map((tab) => (
            <button
              key={tab.label}
              type="button"
              role="tab"
              aria-selected={status === tab.value}
              onClick={() => update({ status: tab.value })}
              className={cn(
                'rounded-xl border-2 px-3 py-1.5 text-sm font-extrabold',
                status === tab.value ? 'border-secondary bg-secondary-soft text-secondary-dark' : 'border-border',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <select
          className="h-10 rounded-xl border-2 border-border bg-card px-3 text-sm font-bold"
          value={type ?? ''}
          onChange={(e) => update({ type: e.target.value || undefined })}
          aria-label="Dạng câu"
        >
          <option value="">Mọi dạng câu</option>
          {(Object.keys(QUESTION_TYPE_META) as JlptQuestionType[]).map((key) => (
            <option key={key} value={key}>
              {QUESTION_TYPE_META[key].jp} · {QUESTION_TYPE_META[key].vi}
            </option>
          ))}
        </select>
        <label className="flex items-center gap-2 text-sm font-bold">
          <input
            type="checkbox"
            className="h-4 w-4 accent-secondary"
            checked={flagged}
            onChange={(e) => update({ flagged: e.target.checked ? 'true' : undefined })}
          />
          Chỉ câu có cảnh báo
        </label>
        <label className="flex items-center gap-2 text-sm font-bold">
          <input
            type="checkbox"
            className="h-4 w-4 accent-secondary"
            checked={reported}
            onChange={(e) => update({ reported: e.target.checked ? 'true' : undefined })}
          />
          Chỉ câu bị báo lỗi
        </label>
        {grammarPointId && (
          <Badge variant="purple" className="gap-1.5 py-1 text-sm">
            Điểm ngữ pháp: <span className="font-jp">{grammarLabel ?? `#${grammarPointId}`}</span>
            <button type="button" aria-label="Bỏ lọc điểm ngữ pháp" onClick={() => update({ grammarPointId: undefined })}>
              <X className="h-3.5 w-3.5" />
            </button>
          </Badge>
        )}
      </div>

      {statusMutation.isError && <Alert className="mb-4">{extractErrorMessage(statusMutation.error)}</Alert>}
      {bulkMutation.data && (
        <p className="mb-4 rounded-2xl bg-secondary-soft px-4 py-3 text-sm font-bold text-secondary-dark">
          Đã duyệt {bulkMutation.data.approved} câu.
          {bulkMutation.data.skipped.length > 0 &&
            ` Bỏ qua ${bulkMutation.data.skipped.length} câu: ${bulkMutation.data.skipped
              .map((skipped) => `#${skipped.id} ${skipped.reason}`)
              .join('; ')}.`}
        </p>
      )}
      {canDraftVocabulary && type && (
        <VocabularyDraftPanel key={`${level}-${type}`} level={level} type={type} onDrafted={refresh} />
      )}

      {type === 'TEXT_GRAMMAR' ? (
        <PassageReviewList key={level} level={level} status={status || undefined} />
      ) : questionsQuery.isLoading ? (
        <PageSpinner label="Đang tải câu hỏi..." />
      ) : questionsQuery.isError || !data ? (
        <Alert>{extractErrorMessage(questionsQuery.error)}</Alert>
      ) : data.content.length === 0 ? (
        <Card className="p-6 text-center font-semibold text-muted-foreground">Không có câu nào khớp bộ lọc.</Card>
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
            <p className="text-sm font-bold text-muted-foreground">{data.totalElements} câu</p>
            {approvable.length > 0 && (
              <Button size="sm" variant="outline" onClick={() => setConfirmingBulk(true)}>
                <CheckCheck className="h-4 w-4" /> Duyệt {approvable.length} câu không có cảnh báo trên trang
              </Button>
            )}
          </div>
          <div className="flex flex-col gap-4">
            {data.content.map((question) => (
              <QuestionReviewCard
                key={question.id}
                question={question}
                busy={statusMutation.isPending}
                onApprove={() => statusMutation.mutate({ id: question.id, status: 'APPROVED' })}
                onBackToDraft={() => statusMutation.mutate({ id: question.id, status: 'DRAFT' })}
                onNote={(nextStatus) => {
                  setNoteKey((key) => key + 1)
                  setPendingNote({ question, status: nextStatus })
                }}
                onEdit={() => {
                  setEditError(null)
                  setEditKey((key) => key + 1)
                  setEditing(question)
                }}
                onDismissReports={() => dismissMutation.mutate(question.id)}
                dismissing={dismissMutation.isPending}
              />
            ))}
          </div>
          {data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-center gap-3">
              <Button variant="outline" size="sm" disabled={data.first} onClick={() => update({ page: String(page - 1) })}>
                <ChevronLeft className="h-4 w-4" /> Trước
              </Button>
              <span className="text-sm font-bold">
                {page + 1} / {data.totalPages}
              </span>
              <Button variant="outline" size="sm" disabled={data.last} onClick={() => update({ page: String(page + 1) })}>
                Sau <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          )}
        </>
      )}

      <Modal
        open={confirmingBulk}
        onClose={() => setConfirmingBulk(false)}
        title={`Duyệt ${approvable.length} câu?`}
        description="Chỉ duyệt khi đã đọc từng câu trên trang: đáp án đúng, chỉ một đáp án đúng, từ vựng đúng cấp độ. Câu có cảnh báo không nằm trong lượt này."
        footer={
          <>
            <Button variant="outline" onClick={() => setConfirmingBulk(false)}>
              Huỷ
            </Button>
            <Button
              disabled={bulkMutation.isPending}
              onClick={() => bulkMutation.mutate(approvable.map((question) => question.id))}
            >
              <CheckCheck className="h-4 w-4" /> {bulkMutation.isPending ? 'Đang duyệt...' : `Duyệt ${approvable.length} câu`}
            </Button>
          </>
        }
      >
        {bulkMutation.isError && <Alert>{extractErrorMessage(bulkMutation.error)}</Alert>}
      </Modal>

      <QuestionEditDialog
        key={editKey}
        question={editing}
        submitting={editMutation.isPending}
        errorMessage={editError}
        onSubmit={(payload) => editing && editMutation.mutate({ id: editing.id, payload })}
        onCancel={() => setEditing(null)}
      />

      <ReviewNoteDialog
        key={noteKey}
        action={pendingNote?.status ?? null}
        subject="câu hỏi"
        busy={statusMutation.isPending}
        onConfirm={(note) =>
          pendingNote && statusMutation.mutate({ id: pendingNote.question.id, status: pendingNote.status, note })
        }
        onCancel={() => setPendingNote(null)}
      />
    </div>
  )
}

/** Câu hỏi hiện như trong đề, kèm đáp án đúng, giải thích, cảnh báo và các nút duyệt. */
function QuestionReviewCard({
  question,
  busy,
  onApprove,
  onBackToDraft,
  onNote,
  onEdit,
  onDismissReports,
  dismissing,
}: {
  question: AdminExamQuestion
  busy: boolean
  onApprove: () => void
  onBackToDraft: () => void
  onNote: (status: 'REJECTED' | 'RETIRED') => void
  onEdit: () => void
  onDismissReports: () => void
  dismissing: boolean
}) {
  const meta = QUESTION_TYPE_META[question.questionType]
  const badge = STATUS_BADGE[question.status]
  return (
    <Card className={cn('p-4 sm:p-5', question.flag && 'border-orange')}>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <span className="text-sm font-black text-muted-foreground">#{question.id}</span>
        <span className="font-jp text-sm font-black">{meta.jp}</span>
        <span className="text-sm font-semibold text-muted-foreground">· {meta.vi}</span>
        <Badge variant={badge.variant}>{badge.label}</Badge>
        {question.flag && <Badge variant="orange">⚠ {FLAG_LABEL[question.flag]}</Badge>}
        <Badge variant="outline">{SOURCE_LABEL[question.source] ?? question.source}</Badge>
      </div>

      {(!question.sentence || question.questionType === 'USAGE') && (
        <p className="font-jp text-lg font-bold">{question.questionText}</p>
      )}
      {question.sentence && (
        <p className="font-jp text-lg leading-loose">
          <SentenceWithTarget sentence={question.sentence} target={question.highlight} />
        </p>
      )}

      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        {OPTIONS.map((opt) => {
          const correct = question.correctOption === opt
          return (
            <div
              key={opt}
              className={cn(
                'flex items-start gap-2 rounded-xl border-2 px-3 py-2 font-jp font-bold',
                correct ? 'border-primary bg-primary-soft text-primary-dark' : 'border-border text-muted-foreground',
              )}
            >
              <span className="font-sans font-black">{opt}.</span> {question[`option${opt}`]}
            </div>
          )
        })}
      </div>

      {question.explanation && <p className="mt-3 text-sm font-semibold text-secondary-dark">{question.explanation}</p>}
      <QuestionReports reports={question.reports} busy={dismissing} onDismiss={onDismissReports} />
      {question.reviewNote && (
        <p
          className={cn(
            'mt-2 rounded-xl px-3 py-2 text-sm font-semibold',
            question.flag ? 'bg-orange-soft text-orange-dark' : 'bg-muted text-muted-foreground',
          )}
        >
          {question.reviewNote}
        </p>
      )}
      {(question.words.length > 0 || question.grammarPoints.length > 0) && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {question.words.map((word) => (
            <Badge key={`w${word.id}`} variant="secondary" className="font-jp">
              {word.character}
            </Badge>
          ))}
          {question.grammarPoints.map((point) => (
            <Badge key={`g${point.id}`} variant="purple" className="font-jp">
              {point.pattern}
            </Badge>
          ))}
        </div>
      )}

      <div className="mt-4 flex flex-wrap gap-2">
        {question.status === 'DRAFT' && (
          <>
            <Button size="sm" disabled={busy} onClick={onApprove}>
              <Check className="h-4 w-4" /> Duyệt
            </Button>
            <Button size="sm" variant="outline" disabled={busy} onClick={() => onNote('REJECTED')}>
              <X className="h-4 w-4" /> Loại
            </Button>
          </>
        )}
        {question.status === 'APPROVED' && (
          <Button size="sm" variant="outline" disabled={busy} onClick={() => onNote('RETIRED')}>
            <Undo2 className="h-4 w-4" /> Rút khỏi đề
          </Button>
        )}
        {(question.status === 'REJECTED' || question.status === 'RETIRED') && (
          <Button size="sm" variant="outline" disabled={busy} onClick={onBackToDraft}>
            <RotateCcw className="h-4 w-4" /> Đưa về chờ duyệt
          </Button>
        )}
        <Button size="sm" variant="ghost" onClick={onEdit}>
          <Pencil className="h-4 w-4" /> Sửa
        </Button>
      </div>
    </Card>
  )
}
