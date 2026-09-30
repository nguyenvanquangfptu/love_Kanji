import { cn } from '@/lib/utils'

export function LogoMark({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border-b-4 border-primary-dark bg-primary font-jp text-xl font-extrabold text-white',
        className,
      )}
    >
      漢
    </span>
  )
}

export function Logo() {
  return (
    <span className="flex items-center gap-2.5">
      <LogoMark />
      <span className="flex flex-col leading-none">
        <span className="text-lg font-black text-primary">Kanji Mastery</span>
        <span className="font-jp text-xs font-bold text-muted-foreground">漢字マスター</span>
      </span>
    </span>
  )
}
