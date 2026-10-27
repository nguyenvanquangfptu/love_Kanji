import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/** Chế độ màu: theo máy (mặc định), luôn sáng, hay luôn tối. */
export type ThemePreference = 'system' | 'light' | 'dark'

/** Tuỳ chọn hiển thị của người học, lưu trên trình duyệt này. */
interface PreferencesState {
  /** Hiện furigana trên câu tiếng Nhật (mặc định bật). */
  showFurigana: boolean
  toggleFurigana: () => void
  /** public/theme-init.js đọc thẳng giá trị này từ localStorage - đổi tên thì sửa cả ở đó. */
  theme: ThemePreference
  setTheme: (theme: ThemePreference) => void
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      showFurigana: true,
      toggleFurigana: () => set((state) => ({ showFurigana: !state.showFurigana })),
      theme: 'system',
      setTheme: (theme) => set({ theme }),
    }),
    { name: 'kanji-mastery-preferences' },
  ),
)
