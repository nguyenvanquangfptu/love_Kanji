import { create } from 'zustand'

/**
 * Chữ Hán đang mở trong bảng "từ cùng chữ" (null = đang đóng). Bảng nằm một chỗ cho cả ứng dụng; các trang có phím tắt
 * (lật thẻ, chấm thẻ) đọc trạng thái này để không nhận phím khi bảng đang mở.
 */
interface CharacterSheetState {
  character: string | null
  open: (character: string) => void
  close: () => void
}

export const useCharacterSheetStore = create<CharacterSheetState>()((set) => ({
  character: null,
  open: (character) => set({ character }),
  close: () => set({ character: null }),
}))
