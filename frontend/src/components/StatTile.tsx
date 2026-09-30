import { type ReactNode } from 'react'
import { type LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

const TONES = {
  primary: { border: 'border-primary', header: 'bg-primary', text: 'text-primary-dark' },
  secondary: { border: 'border-secondary', header: 'bg-secondary', text: 'text-secondary-dark' },
  orange: { border: 'border-orange', header: 'bg-orange', text: 'text-orange-dark' },
  purple: { border: 'border-purple', header: 'bg-purple', text: 'text-purple-dark' },
  accent: { border: 'border-accent', header: 'bg-accent', text: 'text-accent-dark' },
  destructive: { border: 'border-destructive', header: 'bg-destructive', text: 'text-destructive-dark' },
}

export type StatTone = keyof typeof TONES

export function StatTile({
  label,
  value,
  icon: Icon,
  tone,
}: {
  label: string
  value: ReactNode
  icon?: LucideIcon
  tone: StatTone
}) {
  const t = TONES[tone]
  return (
    <div className={cn('overflow-hidden rounded-2xl border-2 bg-card', t.border)}>
      <div className={cn('truncate px-2 py-1 text-center text-[11px] font-extrabold uppercase tracking-wide text-white', t.header)}>
        {label}
      </div>
      <div className={cn('flex items-center justify-center gap-1.5 px-2 py-3 text-xl font-black', t.text)}>
        {Icon && <Icon className="h-5 w-5 shrink-0" strokeWidth={3} />}
        {value}
      </div>
    </div>
  )
}
