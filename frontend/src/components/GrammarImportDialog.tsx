import { type ChangeEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { FileUp } from 'lucide-react'
import { grammarApi } from '@/api/grammar'
import { extractErrorMessage } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Alert } from '@/components/ui/alert'
import { Modal } from '@/components/ui/modal'
import { Textarea } from '@/components/ui/textarea'

const EXAMPLE = `cap_do,bai,mau,nghia,cach_noi,giai_thich
N4,N4-26,〜んです,Giải thích lý do / hỏi để biết thêm,普通形 + んです,
N4,N4-34,〜とおりに,"Làm đúng như, theo như",Vた / Nの + とおりに,`

/** Dán hoặc chọn file CSV danh sách ngữ pháp; nhập lại cùng file không tạo trùng. Trang cha đổi key mỗi lần mở. */
export function GrammarImportDialog({
  open,
  onClose,
  onImported,
}: {
  open: boolean
  onClose: () => void
  onImported: () => void
}) {
  const [csv, setCsv] = useState('')
  const importMutation = useMutation({ mutationFn: grammarApi.importCsv, onSuccess: onImported })

  async function readFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) setCsv(await file.text())
    e.target.value = ''
  }

  const result = importMutation.data

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Nhập danh sách ngữ pháp"
      description="Cột: cấp độ, bài, mẫu, nghĩa tiếng Việt, cách nối, giải thích (hai cột cuối có thể bỏ). Cùng cấp độ + mẫu thì cập nhật, không tạo trùng."
      className="sm:max-w-2xl"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Đóng
          </Button>
          <Button disabled={!csv.trim() || importMutation.isPending} onClick={() => importMutation.mutate(csv)}>
            {importMutation.isPending ? 'Đang nhập...' : 'Nhập'}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        {importMutation.isError && <Alert>{extractErrorMessage(importMutation.error)}</Alert>}
        {result && (
          <div className="rounded-2xl bg-secondary-soft px-4 py-3 text-sm font-bold text-secondary-dark">
            Thêm mới {result.created} · cập nhật {result.updated} · giữ nguyên {result.unchanged}
            {result.errors.length > 0 && (
              <ul className="mt-2 list-disc pl-5 font-semibold text-destructive-dark">
                {result.errors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            )}
          </div>
        )}
        <label className="flex w-fit cursor-pointer items-center gap-2 rounded-2xl border-2 border-border px-4 py-2 text-sm font-extrabold hover:bg-muted">
          <FileUp className="h-4 w-4" strokeWidth={2.5} /> Chọn file .csv
          <input type="file" accept=".csv,text/csv" className="sr-only" onChange={readFile} />
        </label>
        <Textarea
          className="min-h-56 font-jp text-sm"
          value={csv}
          onChange={(e) => setCsv(e.target.value)}
          placeholder={EXAMPLE}
          aria-label="Nội dung CSV"
        />
      </div>
    </Modal>
  )
}
