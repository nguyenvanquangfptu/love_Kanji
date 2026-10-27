import { useEffect } from 'react'
import { usePreferencesStore, type ThemePreference } from '@/store/preferencesStore'

const DARK_QUERY = '(prefers-color-scheme: dark)'

/** Màu thanh trình duyệt / thanh trạng thái điện thoại: xanh lá khi sáng, trùng nền khi tối. */
const THEME_COLORS = { light: '#58cc02', dark: '#131f24' }

function applyTheme(theme: ThemePreference) {
  const dark = theme === 'dark' || (theme === 'system' && window.matchMedia(DARK_QUERY).matches)
  document.documentElement.classList.toggle('dark', dark)
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? THEME_COLORS.dark : THEME_COLORS.light)
}

/**
 * Giữ class "dark" trên <html> khớp với lựa chọn của người học; chọn "theo máy" thì đổi theo khi máy chuyển sáng/tối.
 * Lần vẽ đầu tiên do public/theme-init.js lo (trước khi React chạy).
 */
export function useThemeSync() {
  const theme = usePreferencesStore((s) => s.theme)
  useEffect(() => {
    applyTheme(theme)
    if (theme !== 'system') return
    const media = window.matchMedia(DARK_QUERY)
    const onChange = () => applyTheme('system')
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [theme])
}
