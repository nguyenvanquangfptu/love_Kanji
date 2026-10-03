import { type TextareaHTMLAttributes, forwardRef } from 'react'
import { cn } from '@/lib/utils'

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        ref={ref}
        className={cn(
          'w-full rounded-2xl border-2 border-border bg-muted px-4 py-3 text-base font-semibold transition-colors placeholder:font-normal placeholder:text-muted-foreground focus-visible:border-secondary focus-visible:bg-card focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50',
          className,
        )}
        {...props}
      />
    )
  },
)
Textarea.displayName = 'Textarea'
