import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { X } from 'lucide-react'
import { Progress } from '@/components/ui/progress'

/** Thanh trên cùng của phiên học: nút thoát + thanh tiến độ, giống màn hình bài học của app học ngôn ngữ. */
export function SessionHeader({
  exitTo,
  progress,
  right,
}: {
  exitTo?: string
  progress: number
  right?: ReactNode
}) {
  return (
    <header className="sticky top-0 z-20 bg-background/95 backdrop-blur">
      <div className="mx-auto flex max-w-2xl items-center gap-3 px-4 py-4 sm:gap-4">
        {exitTo && (
          <Link
            to={exitTo}
            aria-label="Thoát"
            className="-ml-1 rounded-xl p-1 text-border-strong transition-colors hover:bg-muted hover:text-muted-foreground"
          >
            <X className="h-7 w-7" strokeWidth={3} />
          </Link>
        )}
        <Progress value={progress} className="flex-1" />
        {right}
      </div>
    </header>
  )
}
