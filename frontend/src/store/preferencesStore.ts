import { create } from 'zustand'
import { persist } from 'zustand/middleware'

/** Tuỳ chọn hiển thị của người học, lưu trên trình duyệt này. */
interface PreferencesState {
  /** Hiện furigana trên câu tiếng Nhật (mặc định bật). */
  showFurigana: boolean
  toggleFurigana: () => void
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      showFurigana: true,
      toggleFurigana: () => set((state) => ({ showFurigana: !state.showFurigana })),
    }),
    { name: 'kanji-mastery-preferences' },
  ),
)
