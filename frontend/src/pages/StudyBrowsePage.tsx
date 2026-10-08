import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { BookMarked, ListChecks, Lock } from 'lucide-react'
import { tagApi } from '@/api/tags'
import { extractErrorMessage } from '@/api/client'
import { JLPT_LEVELS } from '@/api/types'
import { LEVEL_META, OTHER_STYLE, groupTagsByLevel, lessonTitle, parseTagName } from '@/lib/levels'
import { LEVEL_QUIZ_SIZES, lessonQuery, levelQuizQuery } from '@/lib/lesson'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import { Card, CardButton } from '@/components/ui/card'
import { Alert } from '@/components/ui/alert'
import { PageSpinner } from '@/components/ui/spinner'
import { PageHeader } from '@/components/PageHeader'
import { EmptyState } from '@/components/EmptyState'
import { LevelTabs, type LevelKey } from '@/components/LevelTabs'
import { useQuizStart } from '@/lib/quizStart'

const STORAGE_KEY = 'study:selected-level'

function readSavedLevel(): LevelKey | null {
  try {
    return localStorage.getItem(STORAGE_KEY) as LevelKey | null
  } catch {
    return null
  }
}

function saveLevel(level: LevelKey) {
  try {
    localStorage.setItem(STORAGE_KEY, level)
  } catch {
    // Không lưu được lựa chọn (chế độ ẩn danh...) - chỉ mất khả năng nhớ cấp độ lần sau.
  }
}

export function StudyBrowsePage() {
  const navigate = useNavigate()
  const quiz = useQuizStart()
  const [selected, setSelected] = useState<LevelKey | null>(readSavedLevel)

  const { data: tags, isLoading, isError, error } = useQuery({ queryKey: ['tags'], queryFn: tagApi.list })

  const groups = useMemo(() => groupTagsByLevel(tags ?? []), [tags])
  const levelKeys: LevelKey[] = groups.OTHER.length > 0 ? [...JLPT_LEVELS, 'OTHER'] : [...JLPT_LEVELS]
  const hasWords = (key: LevelKey) => groups[key].some((tag) => (tag.wordCount ?? 0) > 0)

  const activeLevel: LevelKey =
    selected && levelKeys.includes(selected)
      ? selected
      : (levelKeys.find(hasWords) ?? levelKeys.find((key) => groups[key].length > 0) ?? 'N5')

  function chooseLevel(level: LevelKey) {
    setSelected(level)
    saveLevel(level)
  }

  if (isLoading) return <PageSpinner />
  if (isError) return <Alert>{extractErrorMessage(error)}</Alert>

  const lessons = groups[activeLevel]
  const meta = activeLevel === 'OTHER' ? null : LEVEL_META[activeLevel]
  const style = meta?.style ?? OTHER_STYLE
  const readyCount = lessons.filter((tag) => (tag.wordCount ?? 0) > 0).length

  return (
    <div>
      {quiz.dialog}
      <PageHeader title="Học bài" subtitle="Chọn cấp độ, rồi chọn một bài để bắt đầu học." />

      <LevelTabs
        levels={levelKeys}
        active={activeLevel}
        onChange={chooseLevel}
        lessonCounts={Object.fromEntries(levelKeys.map((key) => [key, groups[key].length])) as Record<LevelKey, number>}
      />

      <div className={cn('mt-5 flex items-center gap-4 rounded-2xl p-4 sm:p-5', style.soft)}>
        <span
          className={cn(
            'flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border-b-4 text-xl font-black',
            style.solid,
          )}
        >
          {activeLevel === 'OTHER' ? <BookMarked className="h-7 w-7" /> : activeLevel}
        </span>
        <div className="min-w-0">
          <p className={cn('text-xs font-extrabold uppercase tracking-wider', style.text)}>
            {meta?.book ?? (activeLevel === 'OTHER' ? 'Bộ từ tự tạo' : 'Chưa có giáo trình')}
          </p>
          <h2 className="text-xl font-black">{activeLevel === 'OTHER' ? 'Bộ từ khác' : `Từ vựng ${activeLevel}`}</h2>
          <p className="text-sm font-semibold text-muted-foreground">
            {lessons.length} bài · {readyCount} bài đã có từ vựng
          </p>
        </div>
      </div>

      {activeLevel !== 'OTHER' && readyCount > 0 && (
        <Card className="mt-3 flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <ListChecks className={cn('h-7 w-7 shrink-0', style.text)} strokeWidth={2.5} />
            <div className="min-w-0">
              <p className="font-extrabold">Trắc nghiệm tổng hợp {activeLevel}</p>
              <p className="text-sm font-semibold text-muted-foreground">
                Ưu tiên từ bạn hay sai, lấy từ cả {readyCount} bài
              </p>
            </div>
          </div>
          <div className="grid grid-cols-4 gap-2">
            {LEVEL_QUIZ_SIZES.map((size) => (
              <Button
                key={size}
                size="sm"
                variant="outline"
                onClick={() => quiz.start(`/study/quiz?${levelQuizQuery(activeLevel, size)}`)}
              >
                {size} câu
              </Button>
            ))}
          </div>
        </Card>
      )}

      {lessons.length === 0 ? (
        <EmptyState
          icon={BookMarked}
          title={`Chưa có bài học ${activeLevel === 'OTHER' ? '' : activeLevel}`.trim()}
          description="Khi có từ vựng cho cấp độ này, các bài học sẽ xuất hiện ở đây."
        />
      ) : (
        <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {lessons.map((tag) => {
            const count = tag.wordCount ?? 0
            const empty = count === 0
            const { lesson } = parseTagName(tag.name)
            return (
              <CardButton
                key={tag.id}
                disabled={empty}
                onClick={() => navigate(`/study/vocab?${lessonQuery(tag)}`)}
                className="flex items-center gap-3 p-3 sm:p-4"
              >
                <span
                  className={cn(
                    'flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border-b-4 text-lg font-black',
                    empty ? 'border-border-strong bg-border text-muted-foreground' : style.solid,
                  )}
                >
                  {empty ? <Lock className="h-5 w-5" /> : (lesson ?? <BookMarked className="h-5 w-5" />)}
                </span>
                <span className="min-w-0">
                  <span className="block truncate font-extrabold">{lessonTitle(tag.name)}</span>
                  <span className="block text-xs font-bold text-muted-foreground">
                    {empty ? 'Sắp có' : `${count} từ vựng`}
                  </span>
                </span>
              </CardButton>
            )
          })}
        </div>
      )}
    </div>
  )
}
