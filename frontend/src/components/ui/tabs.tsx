import { createContext, useContext, useState, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

interface TabsContextValue {
  value: string
  setValue: (value: string) => void
}

const TabsContext = createContext<TabsContextValue | null>(null)

function useTabsContext() {
  const ctx = useContext(TabsContext)
  if (!ctx) throw new Error('Tabs.* phải nằm trong <Tabs>')
  return ctx
}

export function Tabs({
  defaultValue,
  children,
  className,
}: {
  defaultValue: string
  children: ReactNode
  className?: string
}) {
  const [value, setValue] = useState(defaultValue)
  return (
    <TabsContext.Provider value={{ value, setValue }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  )
}

export function TabsList({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <div role="tablist" className={cn('flex gap-1 rounded-2xl border-2 border-border bg-muted p-1', className)}>
      {children}
    </div>
  )
}

export function TabsTrigger({ value, children }: { value: string; children: ReactNode }) {
  const { value: active, setValue } = useTabsContext()
  const isActive = active === value
  return (
    <button
      type="button"
      role="tab"
      aria-selected={isActive}
      onClick={() => setValue(value)}
      className={cn(
        'flex flex-1 items-center justify-center gap-1.5 rounded-xl px-3 py-2 text-sm font-extrabold transition-colors',
        isActive ? 'bg-card text-secondary shadow-sm' : 'text-muted-foreground hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}

export function TabsContent({ value, children }: { value: string; children: ReactNode }) {
  const { value: active } = useTabsContext()
  if (active !== value) return null
  return <div className="mt-4">{children}</div>
}
