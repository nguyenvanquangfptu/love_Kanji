import { type HTMLAttributes } from 'react'
import { AlertTriangle } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Alert({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      role="alert"
      className={cn(
        'flex items-start gap-2 rounded-2xl border-2 border-destructive/30 bg-destructive-soft px-4 py-3 text-sm font-semibold text-destructive-dark',
        className,
      )}
      {...props}
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <div>{children}</div>
    </div>
  )
}
