import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ChevronRight, ScrollText } from 'lucide-react'
import { GRAMMAR_SESSION_KEY, grammarSrsApi } from '@/api/grammarSrs'
import { cn } from '@/lib/utils'

/** Lối vào Ôn ngữ pháp ở trang Ôn tập; chỉ hiện khi hôm nay còn điểm ngữ pháp cần ôn. */
export function GrammarDueLink({ className }: { className?: string }) {
  const { data: session } = useQuery({ queryKey: GRAMMAR_SESSION_KEY, queryFn: grammarSrsApi.session })
  if (!session || session.cards.length === 0) return null
  return (
    <Link
      to="/grammar/review"
      className={cn(
        'flex items-center gap-3 rounded-2xl border-2 border-purple/30 bg-purple-soft px-4 py-3 font-bold text-purple-dark transition-all hover:brightness-[0.98]',
        className,
      )}
    >
      <ScrollText className="h-5 w-5 shrink-0" strokeWidth={2.5} />
      <span className="flex-1">Ngữ pháp: {session.cards.length} điểm cần ôn hôm nay</span>
      <ChevronRight className="h-5 w-5 shrink-0" />
    </Link>
  )
}
