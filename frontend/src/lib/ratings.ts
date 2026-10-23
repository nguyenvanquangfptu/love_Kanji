import type { ReviewRating } from '@/api/types'

/** Bốn mức chấm thẻ (ôn từ vựng, ôn ngữ pháp): nhãn, gợi ý khi chưa có khoảng ôn, màu nút. */
export const RATING_OPTIONS: { rating: ReviewRating; label: string; hint: string; className: string }[] = [
  { rating: 1, label: 'Quên', hint: 'Học lại', className: 'border-destructive-edge bg-destructive text-white' },
  { rating: 2, label: 'Khó', hint: 'Nghĩ lâu mới ra', className: 'border-orange-edge bg-orange text-white' },
  { rating: 3, label: 'Nhớ', hint: 'Nhớ ra được', className: 'border-secondary-edge bg-secondary text-white' },
  { rating: 4, label: 'Dễ', hint: 'Nhớ ngay', className: 'border-primary-edge bg-primary text-white' },
]
