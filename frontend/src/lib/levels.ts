import { JLPT_LEVELS, type JlptLevel, type TagResponse } from '@/api/types'

export interface LevelStyle {
  /** Nền đặc + viền đáy 3D (dùng cho ô số bài, nút cấp độ đang chọn). */
  solid: string
  soft: string
  text: string
  border: string
  bar: string
}

// Class Tailwind phải là chuỗi literal đầy đủ để trình biên dịch Tailwind quét được.
const STYLES = {
  primary: {
    solid: 'bg-primary border-primary-dark text-white',
    soft: 'bg-primary-soft',
    text: 'text-primary-dark',
    border: 'border-primary',
    bar: 'bg-primary',
  },
  secondary: {
    solid: 'bg-secondary border-secondary-dark text-white',
    soft: 'bg-secondary-soft',
    text: 'text-secondary-dark',
    border: 'border-secondary',
    bar: 'bg-secondary',
  },
  purple: {
    solid: 'bg-purple border-purple-dark text-white',
    soft: 'bg-purple-soft',
    text: 'text-purple-dark',
    border: 'border-purple',
    bar: 'bg-purple',
  },
  orange: {
    solid: 'bg-orange border-orange-dark text-white',
    soft: 'bg-orange-soft',
    text: 'text-orange-dark',
    border: 'border-orange',
    bar: 'bg-orange',
  },
  destructive: {
    solid: 'bg-destructive border-destructive-dark text-white',
    soft: 'bg-destructive-soft',
    text: 'text-destructive-dark',
    border: 'border-destructive',
    bar: 'bg-destructive',
  },
  neutral: {
    solid: 'bg-muted-foreground border-foreground text-white',
    soft: 'bg-muted',
    text: 'text-muted-foreground',
    border: 'border-border-strong',
    bar: 'bg-muted-foreground',
  },
} satisfies Record<string, LevelStyle>

export const LEVEL_META: Record<JlptLevel, { book: string | null; style: LevelStyle }> = {
  N5: { book: 'Minna no Nihongo I', style: STYLES.primary },
  N4: { book: 'Minna no Nihongo II', style: STYLES.secondary },
  N3: { book: 'Mimikara Oboeru N3', style: STYLES.purple },
  N2: { book: null, style: STYLES.orange },
  N1: { book: null, style: STYLES.destructive },
}

export const OTHER_STYLE = STYLES.neutral

export function levelStyle(level: string | null | undefined): LevelStyle {
  return level && level in LEVEL_META ? LEVEL_META[level as JlptLevel].style : STYLES.neutral
}

const TAG_PATTERN = /^(N[1-5])-(\d+)$/

/** Tag đặt tên theo quy ước "<cấp độ>-<số bài>", vd. "N3-01" -> { level: 'N3', lesson: 1 }. */
export function parseTagName(name: string): { level: JlptLevel | null; lesson: number | null } {
  const match = TAG_PATTERN.exec(name)
  if (!match) return { level: null, lesson: null }
  return { level: match[1] as JlptLevel, lesson: Number(match[2]) }
}

export function lessonTitle(tagName: string): string {
  const { lesson } = parseTagName(tagName)
  return lesson !== null ? `Bài ${lesson}` : tagName
}

export function lessonFullTitle(tagName: string): string {
  const { level, lesson } = parseTagName(tagName)
  return level && lesson !== null ? `${level} · Bài ${lesson}` : tagName
}

/** Gom tag theo cấp độ; tag không theo quy ước tên vào nhóm "OTHER". */
export function groupTagsByLevel(tags: TagResponse[]): Record<JlptLevel | 'OTHER', TagResponse[]> {
  const groups = Object.fromEntries([...JLPT_LEVELS, 'OTHER'].map((k) => [k, [] as TagResponse[]])) as Record<
    JlptLevel | 'OTHER',
    TagResponse[]
  >
  for (const tag of tags) {
    const { level } = parseTagName(tag.name)
    groups[level ?? 'OTHER'].push(tag)
  }
  for (const list of Object.values(groups)) {
    list.sort((a, b) => (parseTagName(a.name).lesson ?? 0) - (parseTagName(b.name).lesson ?? 0) || a.name.localeCompare(b.name))
  }
  return groups
}
