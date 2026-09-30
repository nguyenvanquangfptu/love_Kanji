import { type ButtonHTMLAttributes, type HTMLAttributes, forwardRef } from 'react'
import { cn } from '@/lib/utils'

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('rounded-2xl border-2 border-border bg-card text-card-foreground', className)} {...props} />
  )
}

/** Thẻ bấm được (vd. chọn bài học) - là <button> thật để dùng được bàn phím & trình đọc màn hình. */
export const CardButton = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement>>(
  ({ className, type = 'button', ...props }, ref) => (
    <button
      ref={ref}
      type={type}
      className={cn(
        'rounded-2xl border-2 border-b-4 border-border bg-card text-left text-card-foreground transition-all hover:bg-muted active:translate-y-[2px] active:border-b-2 disabled:pointer-events-none disabled:opacity-60 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40',
        className,
      )}
      {...props}
    />
  ),
)
CardButton.displayName = 'CardButton'

export function CardHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-1.5 p-5 sm:p-6', className)} {...props} />
}

export function CardTitle({ className, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return <h3 className={cn('text-lg font-extrabold leading-tight', className)} {...props} />
}

export function CardDescription({ className, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-sm text-muted-foreground', className)} {...props} />
}

export function CardContent({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('p-5 pt-0 sm:p-6 sm:pt-0', className)} {...props} />
}

export function CardFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex items-center p-5 pt-0 sm:p-6 sm:pt-0', className)} {...props} />
}
