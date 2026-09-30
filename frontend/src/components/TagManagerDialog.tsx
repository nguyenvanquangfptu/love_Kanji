import { type FormEvent, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { tagApi } from '@/api/tags'
import { extractErrorMessage } from '@/api/client'
import { lessonFullTitle } from '@/lib/levels'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'
import { Spinner } from '@/components/ui/spinner'
import { Modal } from '@/components/ui/modal'

const TAGS_KEY = ['tags']

export function TagManagerDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')

  const { data: tags, isLoading } = useQuery({
    queryKey: TAGS_KEY,
    queryFn: tagApi.list,
    enabled: open,
  })

  const createMutation = useMutation({
    mutationFn: tagApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: TAGS_KEY })
      setName('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: tagApi.delete,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: TAGS_KEY }),
  })

  function handleCreate(e: FormEvent) {
    e.preventDefault()
    if (!name.trim()) return
    createMutation.mutate({ name: name.trim() })
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Quản lý bài (tag)"
      description={
        <>
          Đặt tên theo dạng <b>cấp độ-số bài</b> (vd. <b>N5-01</b>, <b>N3-12</b>) để bài tự hiện đúng cấp độ trong trang Học
          bài.
        </>
      }
      footer={
        <Button variant="outline" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      <div className="flex flex-col gap-3">
        {createMutation.isError && <Alert>{extractErrorMessage(createMutation.error)}</Alert>}

        <form onSubmit={handleCreate} className="flex gap-2">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="vd. N5-01" className="flex-1" />
          <Button type="submit" disabled={createMutation.isPending} aria-label="Thêm tag">
            <Plus className="h-5 w-5" /> Thêm
          </Button>
        </form>

        {isLoading ? (
          <div className="flex justify-center py-6">
            <Spinner />
          </div>
        ) : (
          <div className="flex max-h-72 flex-col gap-1.5 overflow-y-auto">
            {tags?.map((tag) => (
              <div key={tag.id} className="flex items-center gap-3 rounded-xl border-2 border-border px-3 py-2">
                <span className="flex-1 font-bold">{tag.name}</span>
                <span className="text-xs font-bold text-muted-foreground">
                  {lessonFullTitle(tag.name) !== tag.name && `${lessonFullTitle(tag.name)} · `}
                  {tag.wordCount ?? 0} từ
                </span>
                <button
                  type="button"
                  onClick={() => deleteMutation.mutate(tag.id)}
                  disabled={deleteMutation.isPending}
                  aria-label={`Xoá tag ${tag.name}`}
                  className="rounded-lg p-1.5 text-muted-foreground hover:bg-destructive-soft hover:text-destructive"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
            {tags?.length === 0 && <p className="py-4 text-center text-sm text-muted-foreground">Chưa có tag nào.</p>}
          </div>
        )}
      </div>
    </Modal>
  )
}
