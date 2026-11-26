import { useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, FileUp, Upload } from 'lucide-react'
import { kanjiApi } from '@/api/kanji'
import { extractErrorMessage } from '@/api/client'
import type { VocabularyImportResult } from '@/api/types'
import { Modal } from '@/components/ui/modal'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'

const EXAMPLE = `{
  "bai": "N2-01",
  "cap_do": "N2",
  "tu_vung": [
    { "tu": "好む", "doc": "このむ", "han_viet": "HIẾU", "nghia": "Thích, ưa", "loai": "動詞" },
    { "tu": "静かな", "doc": "しずかな", "han_viet": "TĨNH", "nghia": "Yên tĩnh", "loai": "形容詞" }
  ]
}`

/** Một file đã chọn: nội dung, kết quả kiểm tra và kết quả nhập. */
interface LessonFile {
  name: string
  lesson: unknown
  failure?: string
  check?: VocabularyImportResult
  result?: VocabularyImportResult
}

/** Kiểm tra không lỗi, chưa nhập và còn từ để ghi. */
function ready(file: LessonFile) {
  return !file.failure && !file.result && !!file.check && file.check.errors.length === 0
    && file.check.created + file.check.linked > 0
}

/**
 * Nhập từ vựng theo bài từ file JSON (mỗi file một bài): chọn file thì kiểm tra ngay, chưa ghi gì; bài không còn lỗi
 * mới nhập được. Từ đã có cùng cách viết, cách đọc thì chỉ gắn vào bài; nhập lại không tạo trùng.
 */
export function VocabularyImportDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [files, setFiles] = useState<LessonFile[]>([])
  const [busy, setBusy] = useState<'checking' | 'importing' | null>(null)

  async function check(selected: File[]) {
    setBusy('checking')
    const checked: LessonFile[] = []
    for (const file of selected) {
      const entry: LessonFile = { name: file.name, lesson: null }
      try {
        entry.lesson = JSON.parse(await file.text())
        entry.check = await kanjiApi.importLesson(entry.lesson, true)
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
        next[i] = { ...next[i], result: await kanjiApi.importLesson(next[i].lesson, false) }
      } catch (error) {
        next[i] = { ...next[i], failure: extractErrorMessage(error) }
      }
      setFiles([...next])
    }
    setBusy(null)
    void queryClient.invalidateQueries({ queryKey: ['admin', 'kanji'] })
    void queryClient.invalidateQueries({ queryKey: ['tags'] })
  }

  const readyCount = files.filter(ready).length
  const checkedOnly = files.some((file) => file.check && !file.result)

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Nhập từ vựng theo bài"
      description="Mỗi file JSON là một bài. Chọn file thì chỉ kiểm tra, chưa ghi gì; bài không còn lỗi mới nhập được."
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="outline" disabled={busy !== null} onClick={() => inputRef.current?.click()}>
            <FileUp className="h-4 w-4" /> {busy === 'checking' ? 'Đang kiểm tra...' : 'Chọn file'}
          </Button>
          <Button disabled={readyCount === 0 || busy !== null} onClick={() => void importReady()}>
            <Upload className="h-4 w-4" /> {busy === 'importing' ? 'Đang nhập...' : `Nhập ${readyCount} bài không có lỗi`}
          </Button>
        </>
      }
    >
      <input
        ref={inputRef}
        type="file"
        accept=".json,application/json"
        multiple
        className="hidden"
        onChange={(e) => {
          const selected = Array.from(e.target.files ?? [])
          e.target.value = ''
          if (selected.length > 0) void check(selected)
        }}
      />
      <div className="flex flex-col gap-3 text-sm">
        {files.length === 0 ? (
          <>
            <p className="font-semibold text-muted-foreground">
              Từ đã có cùng cách viết và cách đọc chỉ được gắn vào bài; cùng chữ khác cách đọc thành dòng riêng; từ chỉ
              viết bằng kana mà khác nghĩa thì ghi <code>"tao_moi": true</code>. Số nét và Hán Việt từng chữ được tính tự
              động.
            </p>
            <pre className="overflow-x-auto rounded-2xl bg-muted p-3 font-jp text-xs">{EXAMPLE}</pre>
          </>
        ) : (
          checkedOnly && (
            <Alert className="border-orange/40 bg-orange-soft text-orange-dark">
              Mới kiểm tra, chưa ghi gì vào kho. Bấm "Nhập {readyCount} bài không có lỗi" để ghi.
            </Alert>
          )
        )}
        {files.map((file, index) => (
          <LessonReport key={`${index}-${file.name}`} file={file} />
        ))}
      </div>
    </Modal>
  )
}

function LessonReport({ file }: { file: LessonFile }) {
  const report = file.result ?? file.check
  return (
    <div className="rounded-2xl border-2 border-border p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="min-w-0 break-all font-bold">{file.name}</span>
        {report?.lesson && <Badge variant="outline">Bài {report.lesson}</Badge>}
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
        <p className="mt-1 font-semibold text-muted-foreground">
          {file.result ? 'Đã' : 'Sẽ'} thêm {report.created} từ mới, gắn {report.linked} từ có sẵn
          {report.alreadyInLesson > 0 && ` · ${report.alreadyInLesson} từ đã có trong bài, bỏ qua`}
        </p>
      )}
      {report && report.errors.length > 0 && (
        <ul className="mt-2 flex list-disc flex-col gap-0.5 pl-5 font-semibold text-destructive-dark">
          {report.errors.map((error, index) => (
            <li key={index}>{error}</li>
          ))}
        </ul>
      )}
      {report && report.warnings.length > 0 && (
        <details className="mt-2">
          <summary className="cursor-pointer font-bold text-orange-dark">{report.warnings.length} cảnh báo</summary>
          <ul className="mt-1 flex list-disc flex-col gap-0.5 pl-5 font-semibold text-muted-foreground">
            {report.warnings.map((warning, index) => (
              <li key={index}>{warning}</li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
