import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useCharacterSheetStore } from '@/store/characterSheetStore'

/** Phím "/" ở bất cứ đâu (trừ lúc đang gõ hay đang mở bảng từ cùng chữ) là sang trang Tìm từ. */
export function useSearchShortcut() {
  const navigate = useNavigate()
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key !== '/' || e.ctrlKey || e.metaKey || e.altKey) return
      const target = e.target as HTMLElement | null
      if (target?.closest('input, textarea, select, [contenteditable="true"]')) return
      if (useCharacterSheetStore.getState().character !== null) return
      e.preventDefault()
      navigate('/search')
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [navigate])
}
