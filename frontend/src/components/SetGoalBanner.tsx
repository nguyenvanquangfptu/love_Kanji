import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Target } from 'lucide-react'
import { profileApi } from '@/api/profile'
import { dismissWelcome, welcomeDismissed } from '@/lib/welcome'
import { Button } from '@/components/ui/button'

/** Nhắc người học chưa đặt mục tiêu đi qua hướng dẫn lần đầu; ẩn khi đã đặt hoặc đã bấm "Để sau". */
export function SetGoalBanner() {
  const navigate = useNavigate()
  const [dismissed, setDismissed] = useState(welcomeDismissed)
  const { data: profile } = useQuery({
    queryKey: ['profile', 'learning'],
    queryFn: profileApi.getLearning,
    enabled: !dismissed,
  })
  if (dismissed || !profile || profile.configured) return null
  return (
    <div className="mb-6 flex flex-wrap items-center gap-3 rounded-2xl border-2 border-secondary/40 bg-secondary-soft px-4 py-3">
      <Target className="h-6 w-6 shrink-0 text-secondary-dark" strokeWidth={2.5} />
      <p className="min-w-[12rem] flex-1 font-bold text-secondary-dark">
        Đặt mục tiêu (cấp JLPT, ngày thi, số phút mỗi ngày) để app tính mỗi ngày nên học bao nhiêu.
      </p>
      <div className="flex gap-2">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => {
            dismissWelcome()
            setDismissed(true)
          }}
        >
          Để sau
        </Button>
        <Button variant="secondary" size="sm" onClick={() => navigate('/welcome')}>
          Bắt đầu
        </Button>
      </div>
    </div>
  )
}
