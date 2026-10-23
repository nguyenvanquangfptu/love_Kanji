/** Người học đã bấm "Để sau" ở dải nhắc đặt mục tiêu (hoặc đã đi hết hướng dẫn) - nhớ trên trình duyệt này. */
const KEY = 'kanji-mastery-welcome-dismissed'

export function welcomeDismissed(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    // localStorage bị chặn (ẩn danh): coi như chưa bấm, dải nhắc vẫn hiện.
    return false
  }
}

export function dismissWelcome() {
  try {
    localStorage.setItem(KEY, '1')
  } catch {
    // Không lưu được thì lần sau dải nhắc hiện lại - không ảnh hưởng gì khác.
  }
}
