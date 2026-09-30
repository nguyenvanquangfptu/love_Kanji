import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('h-6 w-6 animate-spin text-secondary', className)} />
}

export function PageSpinner({ label }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-24">
      <Spinner className="h-10 w-10" />
      {label && <p className="text-sm font-bold text-muted-foreground">{label}</p>}
    </div>
  )
}
