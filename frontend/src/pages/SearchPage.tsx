import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Search, SearchX } from 'lucide-react'
import { searchApi } from '@/api/search'
import { extractErrorMessage } from '@/api/client'
import { useCharacterParts } from '@/lib/characters'
import { describeAddResult, useAddToReview } from '@/lib/review'
import { lessonFullTitle } from '@/lib/levels'
import { useCharacterSheetStore } from '@/store/characterSheetStore'
import { PageHeader } from '@/components/PageHeader'
import { CharacterChips } from '@/components/CharacterChips'
import { SpeakButton } from '@/components/SpeakButton'
import { EmptyState } from '@/components/EmptyState'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

/** Chờ người học ngừng gõ chừng này ms mới tìm, khỏi gọi API theo từng phím. */
const DEBOUNCE_MS = 250

/** Chỉ một chữ Hán: gợi ý mở bảng các từ có chữ đó. */
const SINGLE_KANJI = /^\p{Script=Han}$/u

/**
 * Tìm từ trong kho bằng chữ Hán, kana, romaji, Hán Việt hay nghĩa (có dấu hay không). Chuỗi tìm nằm trên URL
 * (/search?q=...) để Back và chia sẻ link vẫn đúng.
 */
export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const query = (params.get('q') ?? '').trim()
  const [text, setText] = useState(() => params.get('q') ?? '')
  const openCharacter = useCharacterSheetStore((state) => state.open)
  const addToReview = useAddToReview()
  const queryClient = useQueryClient()

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (text.trim() !== query) setParams(text.trim() ? { q: text.trim() } : {}, { replace: true })
    }, DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [text, query, setParams])

  const { data, isFetching, isError, error } = useQuery({
    queryKey: ['search', query],
    queryFn: () => searchApi.search(query),
    enabled: query !== '',
    staleTime: 60_000,
  })
  const results = query ? (data ?? []) : []
  const { data: characterParts } = useCharacterParts(results.map((word) => word.id))

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title="Tìm từ" subtitle="Gõ chữ Hán, kana, romaji, Hán Việt hay nghĩa - có dấu hay không dấu đều được." />

      <label className="flex items-center gap-3 rounded-2xl border-2 border-border bg-card px-4 py-3 focus-within:border-secondary">
        <Search className="h-5 w-5 shrink-0 text-muted-foreground" strokeWidth={2.5} />
        <input
          autoFocus
          type="search"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="vd. 学生, がくせい, gakusei, hoc sinh"
          aria-label="Tìm từ"
          className="min-w-0 flex-1 bg-transparent font-jp text-lg font-bold outline-none placeholder:font-sans placeholder:text-sm placeholder:font-semibold"
        />
      </label>

      {SINGLE_KANJI.test(query) && (
        <button
          type="button"
          onClick={() => openCharacter(query)}
          className="mt-3 text-sm font-extrabold text-secondary hover:underline"
        >
          Xem mọi từ có chữ <span className="font-jp">{query}</span> →
        </button>
      )}

      {addToReview.isSuccess && (
        <p className="mt-3 text-sm font-bold text-primary-dark">{describeAddResult(addToReview.data)}</p>
      )}
      {addToReview.isError && <Alert className="mt-3">{extractErrorMessage(addToReview.error)}</Alert>}
      {isError && <Alert className="mt-4">{extractErrorMessage(error)}</Alert>}

      <div className="mt-5 flex flex-col gap-3">
        {query && isFetching && !data && <PageSpinner />}
        {query && data && results.length === 0 && (
          <EmptyState icon={SearchX} title="Không tìm thấy từ nào" description="Thử cách viết khác, hoặc gõ ít chữ hơn." />
        )}
        {results.map((word) => (
          <Card key={word.id} className="flex items-center gap-4 p-4">
            <div className="flex min-w-[4.5rem] shrink-0 flex-col items-center">
              {word.reading && <span className="font-jp text-xs font-bold text-secondary-dark">{word.reading}</span>}
              <span className="font-jp text-3xl font-bold leading-tight">{word.character}</span>
            </div>
            <div className="min-w-0 flex-1">
              <CharacterChips hanViet={word.hanViet} parts={characterParts?.[word.id]} className="text-muted-foreground" />
              <p className="mt-1 text-sm font-semibold leading-snug">{word.meaning}</p>
              <div className="mt-1.5 flex flex-wrap gap-1">
                <Badge variant="outline">{word.jlptLevel}</Badge>
                {word.lessons.map((lesson) => (
                  <Badge key={lesson} variant="outline">
                    {lessonFullTitle(lesson)}
                  </Badge>
                ))}
              </div>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1">
              <SpeakButton text={word.reading ?? word.character} />
              {word.inReview ? (
                <Badge variant="secondary">Đang ôn</Badge>
              ) : (
                <button
                  type="button"
                  disabled={addToReview.isPending}
                  onClick={() =>
                    addToReview.mutate([word.id], {
                      onSuccess: () => queryClient.invalidateQueries({ queryKey: ['search'] }),
                    })
                  }
                  title="Thêm từ này vào Ôn tập"
                  className="inline-flex items-center gap-1 rounded-xl px-2 py-1 text-xs font-extrabold text-secondary-dark hover:bg-secondary-soft disabled:opacity-60"
                >
                  <Plus className="h-4 w-4" strokeWidth={2.5} /> Ôn tập
                </button>
              )}
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}
