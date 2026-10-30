import { useRef, useState } from 'react'
import { CheckCircle2, FileUp, ListChecks, Upload } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import type { ExamImportResult, JlptQuestionType } from '@/api/types'
import { QUESTION_TYPE_META } from '@/lib/jlpt'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'

/** Dạng bài đọc nhập lẻ được (một file JSON là một bài đọc). */
const READING_TYPES: JlptQuestionType[] = ['READING_SHORT', 'READING_MID', 'READING_LONG', 'INFO_SEARCH']

/** Một file đề đã chọn: nội dung đọc được, kết quả chạy thử và kết quả nhập. */
interface ImportFile {
  name: string
  /** Dạng bài đọc khi nhập một bài lẻ; không có = file là cả một đề. */
  type?: JlptQuestionType
  /** Nội dung JSON của file; null nếu không đọc được. */
  test: unknown
  /** Cấp độ ghi trong file ("cap_do"), để mở đúng cấp độ khi duyệt. */
  level: string | null
  failure?: string
  check?: ExamImportResult
  result?: ExamImportResult
}

/** Chạy thử không lỗi, chưa nhập và còn câu mới để nhập. */
function ready(file: ImportFile) {
  return (
    !file.failure &&
    !file.result &&
    file.check !== undefined &&
    file.check.errors.length === 0 &&
    file.check.questions + file.check.passages > 0
  )
}

/** Chạy thử hoặc nhập một file: cả đề, hay một bài đọc lẻ của dạng đã chọn. */
function send(file: ImportFile, dryRun: boolean) {
  return file.type
    ? examAdminApi.importReading(file.type, file.test, dryRun)
    : examAdminApi.importTest(file.test, dryRun)
}

/**
 * Nhập đề tự soạn: mỗi file JSON là một đề (phần Từ vựng, Ngữ pháp, Đọc hiểu), hoặc chế độ "Một bài đọc lẻ": mỗi file
 * là một bài đọc của dạng chọn trong danh sách. Chọn file thì chạy thử ngay - xem lỗi phải sửa theo số câu trong đề
 * và các cảnh báo - rồi mới nhập những file không còn lỗi. Câu nhập vào ở trạng thái chờ duyệt;
 * nhập lại một đề đã nhập không tạo câu trùng.
 */
export function ExamImportPanel({
  onImported,
  onReview,
  onReviewReading,
}: {
  onImported: () => void
  /** Mở danh sách câu chờ duyệt của một đề. */
  onReview: (testCode: string, level: string | null) => void
  /** Mở danh sách bài đọc chờ duyệt của một dạng. */
  onReviewReading: (type: JlptQuestionType, level: string | null) => void
}) {
  const [files, setFiles] = useState<ImportFile[]>([])
  // null = mỗi file là cả một đề; một dạng bài đọc = mỗi file là một bài đọc lẻ của dạng đó.
  const [readingType, setReadingType] = useState<JlptQuestionType | null>(null)
  const [busy, setBusy] = useState<'checking' | 'importing' | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  async function check(selected: File[]) {
    setBusy('checking')
    const checked: ImportFile[] = []
    for (const file of selected) {
      const entry: ImportFile = { name: file.name, type: readingType ?? undefined, test: null, level: null }
      try {
        entry.test = JSON.parse(await file.text())
        const level = (entry.test as { cap_do?: unknown }).cap_do
        entry.level = typeof level === 'string' ? level.trim().toUpperCase() : null
        entry.check = await send(entry, true)
      } catch (error) {
        entry.failure =
          error instanceof SyntaxError ? 'Không đọc được file: nội dung không phải JSON hợp lệ.' : extractErrorMessage(error)
      }
      checked.push(entry)
    }
    setFiles(checked)
    setBusy(null)
  }

  async function importReady() {
    setBusy('importing')
    const next = [...files]
    for (let i = 0; i < next.length; i++) {
      if (!ready(next[i])) continue
      try {
        next[i] = { ...next[i], result: await send(next[i], false) }
      } catch (error) {
        next[i] = { ...next[i], failure: extractErrorMessage(error) }
      }
      setFiles([...next])
    }
    setBusy(null)
    onImported()
  }

  const readyCount = files.filter(ready).length

  return (
    <Card className="mb-4 flex flex-col gap-3 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="font-black">Nhập đề tự soạn</p>
          <p className="text-sm font-semibold text-muted-foreground">
            {readingType
              ? `Mỗi file JSON là một bài ${QUESTION_TYPE_META[readingType].jp} (${QUESTION_TYPE_META[readingType].vi}).`
              : 'Mỗi file JSON là một đề.'}{' '}
            Chọn file thì chạy thử ngay, chưa ghi gì; file không còn lỗi mới nhập được. Câu nhập vào ở trạng thái chờ
            duyệt, nhập lại không tạo câu trùng.
          </p>
        </div>
        <select
          className="h-10 rounded-xl border-2 border-border bg-card px-3 text-sm font-bold"
          value={readingType ?? ''}
          disabled={busy !== null}
          onChange={(e) => {
            setReadingType((e.target.value || null) as JlptQuestionType | null)
            setFiles([])
          }}
          aria-label="Loại file nhập"
        >
          <option value="">Cả đề</option>
          {READING_TYPES.map((type) => (
            <option key={type} value={type}>
              Một bài đọc lẻ: {QUESTION_TYPE_META[type].jp}
            </option>
          ))}
        </select>
        <input
          ref={inputRef}
          type="file"
          accept=".json,application/json"
          multiple
          className="hidden"
          onChange={(e) => {
            const selected = Array.from(e.target.files ?? [])
            // Cho chọn lại đúng file đó sau khi sửa.
            e.target.value = ''
            if (selected.length > 0) void check(selected)
          }}
        />
        <Button variant="outline" disabled={busy !== null} onClick={() => inputRef.current?.click()}>
          <FileUp className="h-4 w-4" />{' '}
          {busy === 'checking' ? 'Đang chạy thử...' : readingType ? 'Chọn file bài đọc' : 'Chọn file đề'}
        </Button>
      </div>

      {files.map((file, index) => (
        <ImportFileReport
          key={`${index}-${file.name}`}
          file={file}
          onReview={onReview}
          onReviewReading={onReviewReading}
        />
      ))}

      {files.length > 0 && (
        <div className="flex flex-wrap items-center justify-end gap-3">
          {readyCount === 0 && busy === null && (
            <p className="text-sm font-semibold text-muted-foreground">Không có file nào sẵn sàng để nhập.</p>
          )}
          <Button disabled={readyCount === 0 || busy !== null} onClick={() => void importReady()}>
            <Upload className="h-4 w-4" />
            {busy === 'importing'
              ? 'Đang nhập...'
              : `Nhập ${readyCount} ${readingType ? 'bài đọc' : 'đề'} không có lỗi`}
          </Button>
        </div>
      )}
    </Card>
  )
}

function ImportFileReport({
  file,
  onReview,
  onReviewReading,
}: {
  file: ImportFile
  onReview: (testCode: string, level: string | null) => void
  onReviewReading: (type: JlptQuestionType, level: string | null) => void
}) {
  const report = file.result ?? file.check
  return (
    <div className="rounded-2xl border-2 border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="min-w-0 break-all font-bold">{file.name}</span>
        {file.type && <Badge variant="purple">{QUESTION_TYPE_META[file.type].jp}</Badge>}
        {report?.testCode && <Badge variant="outline">Mã đề {report.testCode}</Badge>}
        {file.failure ? (
          <Badge variant="destructive">Không nhập được</Badge>
        ) : file.result ? (
          <Badge variant="success">
            <CheckCircle2 className="h-3.5 w-3.5" /> Đã nhập
          </Badge>
        ) : report && report.errors.length > 0 ? (
          <Badge variant="destructive">{report.errors.length} lỗi</Badge>
        ) : (
          report && <Badge variant="secondary">Sẵn sàng</Badge>
        )}
      </div>

      {file.failure && <Alert className="mt-2">{file.failure}</Alert>}

      {report && !file.failure && (
        <p className="mt-1 text-sm font-semibold text-muted-foreground">
          {file.result ? 'Đã ghi' : 'Sẽ ghi'} {report.questions} câu, {report.passages} đoạn văn
          {report.alreadyImported > 0 && ` · ${report.alreadyImported} câu đã có từ lần nhập trước, bỏ qua`}
        </p>
      )}

      {report && report.errors.length > 0 && (
        <ul className="mt-2 flex list-disc flex-col gap-0.5 pl-5 text-sm font-semibold text-destructive-dark">
          {report.errors.map((error, index) => (
            <li key={index}>{error}</li>
          ))}
        </ul>
      )}

      {report && report.warnings.length > 0 && (
        <details className="mt-2 text-sm">
          <summary className="cursor-pointer font-bold text-orange-dark">{report.warnings.length} cảnh báo</summary>
          <ul className="mt-1 flex list-disc flex-col gap-0.5 pl-5 font-semibold text-muted-foreground">
            {report.warnings.map((warning, index) => (
              <li key={index}>{warning}</li>
            ))}
          </ul>
        </details>
      )}

      {file.result && (
        <Button
          size="sm"
          variant="outline"
          className="mt-2"
          onClick={() => {
            if (file.type) onReviewReading(file.type, file.level)
            else if (file.result?.testCode) onReview(file.result.testCode, file.level)
          }}
        >
          <ListChecks className="h-4 w-4" /> {file.type ? 'Duyệt bài đọc' : 'Duyệt đề này'}
        </Button>
      )}
    </div>
  )
}
