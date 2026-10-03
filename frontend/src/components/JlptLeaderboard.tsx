import { useQuery } from '@tanstack/react-query'
import { Trophy } from 'lucide-react'
import { examApi } from '@/api/exam'
import type { JlptLeaderboardEntry } from '@/api/types'
import { formatMinutes } from '@/lib/jlpt'
import { useAuthStore } from '@/store/authStore'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'

const MEDALS: Record<number, string> = {
  1: 'border-accent-dark bg-accent text-accent-foreground',
  2: 'border-border-strong bg-border text-foreground',
  3: 'border-orange-dark bg-orange text-white',
}

/**
 * Bảng xếp hạng đề JLPT của một cấp độ: mỗi người một dòng - buổi thi làm đủ các phần có tỉ lệ đúng cao nhất, bằng nhau
 * thì ai nhanh hơn xếp trên. Tách riêng với bảng xếp hạng thi nhanh.
 */
export function JlptLeaderboard({ level }: { level: string }) {
  const username = useAuthStore((s) => s.username)
  const { data: top, isLoading } = useQuery({
    queryKey: ['jlpt-leaderboard', level],
    queryFn: () => examApi.jlptLeaderboard(level, 10),
  })
  const { data: mine } = useQuery({
    queryKey: ['jlpt-leaderboard', level, 'me'],
    queryFn: () => examApi.myJlptRank(level),
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-10">
        <Spinner />
      </div>
    )
  }

  const isInTop = top?.some((entry) => entry.username === username)
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
                MEDALS[entry.rank ?? 0] ?? 'border-transparent bg-muted text-muted-foreground',
              )}
            >
              {entry.rank !== null && entry.rank <= 3 ? <Trophy className="h-4 w-4" strokeWidth={3} /> : entry.rank}
            </span>
            <span className={cn('min-w-0 flex-1 truncate font-extrabold', isMe && 'text-secondary-dark')}>
              {entry.username}
              {isMe && ' (bạn)'}
            </span>
            <Result entry={entry} />
          </div>
        )
      })}

      {!top?.length && (
        <p className="py-8 text-center font-semibold text-muted-foreground">
          Chưa có ai làm trọn một đề {level}. Hãy là người đầu tiên!
        </p>
      )}

      {!isInTop && mine?.rank != null && (
        <div className="mt-2 flex items-center justify-between gap-3 rounded-2xl border-2 border-dashed border-secondary px-4 py-3 font-extrabold text-secondary-dark">
          <span>Hạng của bạn: {mine.rank}</span>
          <Result entry={mine} />
        </div>
      )}
      <p className="text-xs font-semibold text-muted-foreground">
        Chỉ tính buổi thi làm đủ các phần; mỗi người lấy buổi tốt nhất.
      </p>
    </div>
  )
}

/** Điểm ước tính và chi tiết của một dòng. */
function Result({ entry }: { entry: JlptLeaderboardEntry }) {
  if (entry.estimatedScore === null) return null
  return (
    <span className="shrink-0 text-right">
      <span className="block font-black tabular-nums">{entry.estimatedScore}/60</span>
      <span className="block text-xs font-bold tabular-nums text-muted-foreground">
        {entry.correct}/{entry.total} câu · {formatMinutes(entry.timeSpentSeconds ?? 0)}
      </span>
    </span>
  )
}
