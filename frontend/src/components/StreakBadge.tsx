import { useQuery } from '@tanstack/react-query'
import { Flame } from 'lucide-react'
import { Link } from 'react-router-dom'
import { progressApi } from '@/api/progress'
import { cn } from '@/lib/utils'

/**
 * Chuỗi ngày học ở thanh bên: số ngày liên tiếp, và nhắc học hôm nay để giữ chuỗi. Chưa có chuỗi nào thì không hiện.
 * Bấm vào để xem lịch nhiệt ở trang Tiến bộ.
 */
export function StreakBadge({ className }: { className?: string }) {
  const { data: streak } = useQuery({
    queryKey: ['progress', 'streak'],
    queryFn: progressApi.getStreak,
    staleTime: 60_000,
  })
  if (!streak || streak.current === 0) return null
  return (
    <Link
      to="/progress"
      title={`Dài nhất: ${streak.longest} ngày`}
      className={cn(
        'flex items-center gap-2 rounded-2xl border-2 px-4 py-2.5 text-sm font-extrabold transition-colors',
        streak.studiedToday
          ? 'border-orange/40 bg-orange-soft text-orange-dark'
          : 'border-border bg-card text-muted-foreground hover:bg-muted',
        className,
      )}
    >
      <Flame className={cn('h-5 w-5 shrink-0', streak.studiedToday && 'fill-orange')} strokeWidth={2.5} />
      <span className="flex-1">{streak.current} ngày liên tiếp</span>
      {!streak.studiedToday && <span className="text-xs font-bold">Học hôm nay để giữ chuỗi</span>}
    </Link>
  )
}
