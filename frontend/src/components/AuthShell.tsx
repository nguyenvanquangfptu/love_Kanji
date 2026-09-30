import { type ReactNode, useState, forwardRef, type InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

const TILES = [
  { char: '漢', className: '-rotate-6 border-primary-dark bg-primary' },
  { char: '字', className: '-translate-y-2 border-secondary-dark bg-secondary' },
  { char: '学', className: 'rotate-6 border-orange-dark bg-orange' },
]

export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-6 flex gap-3">
            {TILES.map((tile) => (
              <span
                key={tile.char}
                className={cn(
                  'flex h-16 w-16 animate-bounce-in items-center justify-center rounded-2xl border-b-[6px] font-jp text-3xl font-extrabold text-white',
                  tile.className,
                )}
              >
                {tile.char}
              </span>
            ))}
          </div>
          <h1 className="text-3xl font-black">{title}</h1>
          <p className="mt-2 text-muted-foreground">{subtitle}</p>
        </div>
        {children}
        <p className="mt-6 text-center text-sm font-semibold text-muted-foreground">{footer}</p>
      </div>
    </div>
  )
}

export const PasswordInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>((props, ref) => {
  const [visible, setVisible] = useState(false)
  return (
    <div className="relative">
      <Input ref={ref} type={visible ? 'text' : 'password'} className="pr-12" {...props} />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        className="absolute right-2 top-1/2 -translate-y-1/2 rounded-xl p-2 text-muted-foreground hover:text-foreground"
      >
        {visible ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
      </button>
    </div>
  )
})
PasswordInput.displayName = 'PasswordInput'
