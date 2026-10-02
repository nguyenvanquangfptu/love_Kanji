import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ListChecks, PartyPopper, RotateCcw } from 'lucide-react'
import { srsApi } from '@/api/srs'
import { extractErrorMessage } from '@/api/client'
import { PageHeader } from '@/components/PageHeader'
import { EmptyState } from '@/components/EmptyState'
import { SpeakButton } from '@/components/SpeakButton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'

/** Từ người học quên đi quên lại - luyện riêng bằng trắc nghiệm chỉ gồm những từ này. */
export function HardWordsPage() {
  const navigate = useNavigate()
  const { data, isLoading, isError, error } = useQuery({ queryKey: ['srs', 'hard-words'], queryFn: srsApi.getHardWords })
  const words = data?.words ?? []

  return (
    <div>
      <PageHeader
        title="Từ khó của tôi"
        subtitle={
          data ? `Những từ bạn đã quên từ ${data.lapseThreshold} lần trở lên - luyện riêng để dứt điểm.` : undefined
        }
        action={
          words.length > 0 && (
            <Button onClick={() => navigate('/study/quiz?hardWords=1')}>
              <ListChecks className="h-5 w-5" strokeWidth={2.5} />
              Luyện trắc nghiệm
            </Button>
          )
        }
      />

      {isLoading && <PageSpinner />}
      {isError && <Alert>{extractErrorMessage(error)}</Alert>}
      {data && words.length === 0 && (
        <EmptyState
          icon={PartyPopper}
          iconClassName="bg-accent-soft text-accent-dark"
          title="Chưa có từ khó nào"
          description="Từ nào bạn quên đi quên lại nhiều lần sẽ được gom vào đây để luyện riêng."
          action={
            <Button size="lg" onClick={() => navigate('/flashcards')}>
              <RotateCcw className="h-5 w-5" /> Về Ôn tập
            </Button>
          }
        />
      )}

      <div className="flex flex-col gap-2">
        {words.map(({ kanji, lapseCount }) => (
          <Card key={kanji.id} className="flex items-center gap-3 p-3">
            <div className="flex min-w-[4.5rem] shrink-0 flex-col items-center">
              {kanji.reading && <span className="font-jp text-xs font-bold text-secondary-dark">{kanji.reading}</span>}
              <span className="font-jp text-2xl font-bold">{kanji.character}</span>
            </div>
            <div className="min-w-0 flex-1">
              {kanji.hanViet && (
                <p className="text-xs font-extrabold uppercase tracking-wide text-muted-foreground">{kanji.hanViet}</p>
              )}
              <p className="text-sm font-semibold">{kanji.meaning}</p>
            </div>
            <Badge variant="destructive" className="shrink-0">
              Quên {lapseCount} lần
            </Badge>
            <SpeakButton text={kanji.reading ?? kanji.character} />
          </Card>
        ))}
      </div>
    </div>
  )
}
