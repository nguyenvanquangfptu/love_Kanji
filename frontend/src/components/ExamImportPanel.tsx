import { useRef, useState } from 'react'
import { CheckCircle2, FileUp, ListChecks, Upload } from 'lucide-react'
import { examAdminApi } from '@/api/examAdmin'
import { extractErrorMessage } from '@/api/client'
import type { ExamImportResult } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'

/** Một file đề đã chọn: nội dung đọc được, kết quả chạy thử và kết quả nhập. */
interface ImportFile {
  name: string
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

/**
 * Nhập đề tự soạn: mỗi file JSON là một đề (phần Từ vựng và Ngữ pháp). Chọn file thì chạy thử ngay - xem lỗi phải sửa
 * theo số câu trong đề và các cảnh báo - rồi mới nhập những đề không còn lỗi. Câu nhập vào ở trạng thái chờ duyệt;
 * nhập lại một đề đã nhập không tạo câu trùng.
 */
export function ExamImportPanel({
  onImported,
  onReview,
}: {
  onImported: () => void
  /** Mở danh sách câu chờ duyệt của một đề. */
  onReview: (testCode: string, level: string | null) => void
}) {
  const [files, setFiles] = useState<ImportFile[]>([])
  const [busy, setBusy] = useState<'checking' | 'importing' | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  async function check(selected: File[]) {
    setBusy('checking')
    const checked: ImportFile[] = []
    for (const file of selected) {
      const entry: ImportFile = { name: file.name, test: null, level: null }
      try {
        entry.test = JSON.parse(await file.text())
        const level = (entry.test as { cap_do?: unknown }).cap_do
        entry.level = typeof level === 'string' ? level.trim().toUpperCase() : null
        entry.check = await examAdminApi.importTest(entry.test, true)
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
        next[i] = { ...next[i], result: await examAdminApi.importTest(next[i].test, false) }
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
            Mỗi file JSON là một đề. Chọn file thì chạy thử ngay, chưa ghi gì; đề không còn lỗi mới nhập được. Câu nhập
            vào ở trạng thái chờ duyệt, nhập lại một đề không tạo câu trùng.
          </p>
        </div>
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
          <FileUp className="h-4 w-4" /> {busy === 'checking' ? 'Đang chạy thử...' : 'Chọn file đề'}
        </Button>
      </div>

      {files.map((file, index) => (
        <ImportFileReport key={`${index}-${file.name}`} file={file} onReview={onReview} />
      ))}

      {files.length > 0 && (
        <div className="flex flex-wrap items-center justify-end gap-3">
          {readyCount === 0 && busy === null && (
            <p className="text-sm font-semibold text-muted-foreground">Không có đề nào sẵn sàng để nhập.</p>
          )}
          <Button disabled={readyCount === 0 || busy !== null} onClick={() => void importReady()}>
            <Upload className="h-4 w-4" />
            {busy === 'importing' ? 'Đang nhập...' : `Nhập ${readyCount} đề không có lỗi`}
          </Button>
        </div>
      )}
    </Card>
  )
}

function ImportFileReport({
  file,
  onReview,
}: {
  file: ImportFile
  onReview: (testCode: string, level: string | null) => void
}) {
  const report = file.result ?? file.check
  return (
    <div className="rounded-2xl border-2 border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="min-w-0 break-all font-bold">{file.name}</span>
        {report && <Badge variant="outline">Mã đề {report.testCode}</Badge>}
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
          onClick={() => onReview(file.result!.testCode, file.level)}
        >
          <ListChecks className="h-4 w-4" /> Duyệt đề này
        </Button>
      )}
    </div>
  )
}
