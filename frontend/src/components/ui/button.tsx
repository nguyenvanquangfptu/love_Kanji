import { type ButtonHTMLAttributes, forwardRef } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

// Nút "nổi 3D": viền đáy đậm màu, khi nhấn thì lún xuống (translate + viền mỏng lại).
export const buttonVariants = cva(
  'inline-flex select-none items-center justify-center gap-2 whitespace-nowrap rounded-2xl font-extrabold transition-all active:translate-y-[2px] disabled:pointer-events-none disabled:opacity-50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring/40',
  {
    variants: {
      variant: {
        default:
          'border-b-4 border-primary-dark bg-primary text-primary-foreground hover:brightness-105 active:border-b-2',
        secondary:
          'border-b-4 border-secondary-dark bg-secondary text-secondary-foreground hover:brightness-105 active:border-b-2',
        accent: 'border-b-4 border-accent-dark bg-accent text-accent-foreground hover:brightness-105 active:border-b-2',
        outline:
          'border-2 border-b-4 border-border bg-card text-foreground hover:bg-muted active:border-b-2',
        ghost: 'text-muted-foreground hover:bg-muted hover:text-foreground active:translate-y-0',
        destructive:
          'border-b-4 border-destructive-dark bg-destructive text-destructive-foreground hover:brightness-105 active:border-b-2',
      },
      size: {
        default: 'h-12 px-5 text-base',
        sm: 'h-10 rounded-xl px-4 text-sm',
        lg: 'h-14 px-8 text-lg',
        icon: 'h-11 w-11 rounded-xl',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, type = 'button', ...props }, ref) => {
    return <button ref={ref} type={type} className={cn(buttonVariants({ variant, size }), className)} {...props} />
  },
)
Button.displayName = 'Button'
