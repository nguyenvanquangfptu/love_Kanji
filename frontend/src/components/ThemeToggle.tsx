import { Monitor, Moon, Sun, type LucideIcon } from 'lucide-react'
import { usePreferencesStore, type ThemePreference } from '@/store/preferencesStore'
import { cn } from '@/lib/utils'

const OPTIONS: Record<ThemePreference, { label: string; icon: LucideIcon; next: ThemePreference }> = {
  system: { label: 'Theo máy', icon: Monitor, next: 'light' },
  light: { label: 'Sáng', icon: Sun, next: 'dark' },
  dark: { label: 'Tối', icon: Moon, next: 'system' },
}

/** Nút đổi giao diện, bấm lần lượt: Theo máy → Sáng → Tối. */
export function ThemeToggle({ className }: { className?: string }) {
  const theme = usePreferencesStore((s) => s.theme)
  const setTheme = usePreferencesStore((s) => s.setTheme)
  const { label, icon: Icon, next } = OPTIONS[theme] ?? OPTIONS.system
  const title = `Giao diện: ${label} (bấm để chuyển sang ${OPTIONS[next].label})`

  return (
    <button
      type="button"
      onClick={() => setTheme(next)}
      aria-label={title}
      title={title}
      className={cn('rounded-xl p-2 text-muted-foreground hover:bg-muted hover:text-foreground', className)}
    >
      <Icon className="h-5 w-5" />
    </button>
  )
}
