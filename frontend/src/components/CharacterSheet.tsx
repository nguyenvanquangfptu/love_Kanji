import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Modal } from '@/components/ui/modal'
import { buttonVariants } from '@/components/ui/button'
import { CharacterDetailView } from '@/components/CharacterDetailView'
import { useCharacterSheetStore } from '@/store/characterSheetStore'

/**
 * Bảng "từ cùng chữ" mở từ chữ Hán trên thẻ hay danh sách từ - một bảng cho cả ứng dụng, mở đè lên trang đang xem để
 * không làm mất phiên ôn đang dở.
 */
export function CharacterSheet() {
  const character = useCharacterSheetStore((state) => state.character)
  const close = useCharacterSheetStore((state) => state.close)
  const { pathname } = useLocation()

  // Sang trang khác (kể cả bấm Back) thì đóng bảng.
  useEffect(() => close(), [pathname, close])

  return (
    <Modal
      open={character !== null}
      onClose={close}
      title="Từ cùng chữ"
      footer={
        character && (
          <Link
            to={`/characters/${encodeURIComponent(character)}`}
            className={buttonVariants({ variant: 'outline', size: 'sm' })}
          >
            Mở trang riêng
          </Link>
        )
      }
    >
      {character && <CharacterDetailView key={character} character={character} />}
    </Modal>
  )
}
