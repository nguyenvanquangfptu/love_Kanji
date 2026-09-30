import { type ReactNode } from 'react'
import { type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

export function EmptyState({
  icon: Icon,
  iconClassName = 'bg-secondary-soft text-secondary',
  title,
  description,
  action,
}: {
  icon: LucideIcon
  iconClassName?: string
  title: string
  description?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-3 px-4 py-12 text-center">
      <span className={cn('flex h-20 w-20 animate-bounce-in items-center justify-center rounded-full', iconClassName)}>
        <Icon className="h-10 w-10" strokeWidth={2.5} />
      </span>
      <h2 className="text-xl font-extrabold">{title}</h2>
      {description && <p className="max-w-sm text-muted-foreground">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}
