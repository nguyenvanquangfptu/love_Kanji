import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Cỡ chữ lớn nhất vẫn vừa mặt thẻ flashcard, theo độ dài từ. */
export function wordSizeClass(word: string) {
  const length = [...word].length
  if (length <= 2) return 'text-7xl sm:text-8xl'
  if (length <= 4) return 'text-6xl sm:text-7xl'
  return 'text-4xl sm:text-5xl'
}
