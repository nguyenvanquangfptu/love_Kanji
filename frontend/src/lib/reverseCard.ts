import type { DailyCardResponse } from '@/api/types'

/** Thẻ đã có khoảng ôn từ chừng này ngày trở lên mới được hỏi ngược - đã thuộc chắc chiều xuôi. */
export const REVERSE_MIN_INTERVAL_DAYS = 21
/** Khoảng 1/3 số lần ôn của thẻ đủ điều kiện được hỏi ngược. */
const REVERSE_SHARE = 3

/**
 * Thẻ này có hỏi ngược không: hiện nghĩa trước, người học tự nhớ ra từ tiếng Nhật (nhớ ra khó hơn nhận ra, nên nhớ
 * lâu hơn). Chọn theo từ và ngày nên cùng một thẻ trong ngày không đổi chiều khi trang vẽ lại hay mở lại.
 */
export function isReverseCard(card: DailyCardResponse, enabled: boolean, today = new Date()): boolean {
  if (!enabled || card.newCard || card.reviewIntervalDays < REVERSE_MIN_INTERVAL_DAYS) return false
  const day = Math.floor(today.getTime() / 86_400_000)
  return (card.kanji.id * 31 + day) % REVERSE_SHARE === 0
}
