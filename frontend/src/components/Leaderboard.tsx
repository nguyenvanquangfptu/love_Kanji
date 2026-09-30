import { useQuery } from '@tanstack/react-query'
import { Trophy } from 'lucide-react'
import { leaderboardApi } from '@/api/leaderboard'
import { useAuthStore } from '@/store/authStore'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'

const MEDALS: Record<number, string> = {
  1: 'border-accent-dark bg-accent text-accent-foreground',
  2: 'border-border-strong bg-border text-foreground',
  3: 'border-orange-dark bg-orange text-white',
}

export function Leaderboard({ level }: { level: string }) {
  const username = useAuthStore((s) => s.username)

  const { data: top, isLoading } = useQuery({
    queryKey: ['leaderboard', level],
    queryFn: () => leaderboardApi.getTop(level, 10),
  })

  const { data: myRank } = useQuery({
    queryKey: ['leaderboard', level, 'my-rank'],
    queryFn: () => leaderboardApi.getMyRank(level),
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-10">
        <Spinner />
      </div>
    )
  }

  const isInTop = top?.some((e) => e.username === username)

  return (
    <div className="flex flex-col gap-2">
      {top?.map((entry) => {
        const isMe = entry.username === username
        return (
          <div
            key={entry.userId}
            className={cn(
              'flex items-center gap-3 rounded-2xl border-2 px-4 py-3',
              isMe ? 'border-secondary bg-secondary-soft' : 'border-border bg-card',
            )}
          >
            <span
              className={cn(
                'flex h-9 w-9 shrink-0 items-center justify-center rounded-full border-b-4 text-sm font-black',
                MEDALS[entry.rank] ?? 'border-transparent bg-muted text-muted-foreground',
              )}
            >
              {entry.rank <= 3 ? <Trophy className="h-4 w-4" strokeWidth={3} /> : entry.rank}
            </span>
            <span className={cn('flex-1 truncate font-extrabold', isMe && 'text-secondary-dark')}>
              {entry.username}
              {isMe && ' (bạn)'}
            </span>
            <span className="font-black tabular-nums">{entry.score.toFixed(2)}</span>
          </div>
        )
      })}

      {!top?.length && (
        <p className="py-8 text-center font-semibold text-muted-foreground">Chưa có ai trên bảng xếp hạng. Hãy là người đầu tiên!</p>
      )}

      {!isInTop && myRank?.rank != null && (
        <div className="mt-2 flex items-center justify-between rounded-2xl border-2 border-dashed border-secondary px-4 py-3 font-extrabold text-secondary-dark">
          <span>Hạng của bạn: {myRank.rank}</span>
          <span className="tabular-nums">{myRank.score?.toFixed(2)}</span>
        </div>
      )}
    </div>
  )
}
