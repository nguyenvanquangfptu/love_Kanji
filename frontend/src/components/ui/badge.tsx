import { type HTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva('inline-flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-extrabold', {
  variants: {
    variant: {
      default: 'bg-primary-soft text-primary-dark',
      secondary: 'bg-secondary-soft text-secondary-dark',
      outline: 'border-2 border-border text-muted-foreground',
      success: 'bg-success-soft text-success-dark',
      destructive: 'bg-destructive-soft text-destructive-dark',
      accent: 'bg-accent-soft text-accent-dark',
      purple: 'bg-purple-soft text-purple-dark',
      orange: 'bg-orange-soft text-orange-dark',
    },
  },
  defaultVariants: { variant: 'default' },
})

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}
