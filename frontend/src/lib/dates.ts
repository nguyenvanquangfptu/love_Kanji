/** Ngày dạng ISO (2026-12-06) như API trả về -> 06/12/2026. */
export function formatDay(iso: string) {
  const [year, month, day] = iso.split('-')
  return `${day}/${month}/${year}`
}

/** Ngày ISO (giờ máy người dùng) - dùng cho input type="date". */
export function toIsoDay(date: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** Khoảng ôn: 5 ngày, ~3 tháng, ~1,5 năm. */
export function formatInterval(days: number) {
  if (days < 30) return `${days} ngày`
  if (days < 365) return `~${Math.round(days / 30)} tháng`
  return `~${(days / 365).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} năm`
}

export function addDays(iso: string, days: number) {
  const date = new Date(`${iso}T00:00:00`)
  date.setDate(date.getDate() + days)
  return toIsoDay(date)
}

/** Kỳ thi JLPT thường rơi vào Chủ nhật đầu tiên của tháng 7 và tháng 12 - gợi ý {@code count} kỳ sắp tới. */
export function upcomingJlptDays(count: number, from = new Date()) {
  const today = toIsoDay(from)
  const days: string[] = []
  for (let year = from.getFullYear(); days.length < count; year++) {
    for (const month of [6, 11]) {
      const date = new Date(year, month, 1)
      date.setDate(1 + ((7 - date.getDay()) % 7))
      const iso = toIsoDay(date)
      if (iso >= today && days.length < count) days.push(iso)
    }
  }
  return days
}
