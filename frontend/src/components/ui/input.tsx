import { type InputHTMLAttributes, forwardRef } from 'react'
import { cn } from '@/lib/utils'

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        ref={ref}
        type={type}
        className={cn(
          'flex h-12 w-full rounded-2xl border-2 border-border bg-muted px-4 text-base font-semibold transition-colors placeholder:font-normal placeholder:text-muted-foreground focus-visible:border-secondary focus-visible:bg-card focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50',
          className,
        )}
        {...props}
      />
    )
  },
)
Input.displayName = 'Input'
