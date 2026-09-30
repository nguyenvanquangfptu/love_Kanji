import { Volume2 } from 'lucide-react'
import { canSpeak, speakJapanese } from '@/lib/speech'
import { cn } from '@/lib/utils'

export function SpeakButton({ text, className }: { text: string; className?: string }) {
  if (!canSpeak) return null
  return (
    <button
      type="button"
      aria-label={`Nghe phát âm ${text}`}
      title="Nghe phát âm"
      onClick={(e) => {
        e.stopPropagation()
        speakJapanese(text)
      }}
      className={cn('shrink-0 rounded-xl p-2 text-secondary transition-colors hover:bg-secondary-soft', className)}
    >
      <Volume2 className="h-5 w-5" strokeWidth={2.5} />
    </button>
  )
}
