import { Link, useSearchParams } from 'react-router-dom'
import { CalendarCheck } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * Nút quay về trang Học hôm nay để làm bước tiếp theo - chỉ hiện khi trang được mở từ đó (?from=today), đặt ở màn
 * "đã xong" của từng bước.
 */
export function BackToToday({ className }: { className?: string }) {
  const [searchParams] = useSearchParams()
  if (searchParams.get('from') !== 'today') return null
  return (
    <Link to="/today" className={cn(buttonVariants({ size: 'lg' }), className)}>
      <CalendarCheck className="h-5 w-5" /> Tiếp tục Học hôm nay
    </Link>
  )
}
