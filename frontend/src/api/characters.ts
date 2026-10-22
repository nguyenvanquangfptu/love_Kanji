import { apiClient } from './client'
import type { CharacterDetail, CharacterRebuildReport, WordCharacter } from './types'

/** Server nhận tối đa chừng này từ mỗi lần hỏi. */
const MAX_WORD_IDS = 200

export const characterApi = {
  /** Các chữ Hán của từng từ, theo id từ; từ không tách được thì không có trong kết quả. */
  parts: async (wordIds: number[]): Promise<Record<number, WordCharacter[]>> => {
    const chunks: number[][] = []
    for (let i = 0; i < wordIds.length; i += MAX_WORD_IDS) chunks.push(wordIds.slice(i, i + MAX_WORD_IDS))
    const pages = await Promise.all(
      chunks.map((ids) =>
        apiClient
          .get<Record<number, WordCharacter[]>>('/characters', { params: { wordIds: ids.join(',') } })
          .then((r) => r.data),
      ),
    )
    return Object.assign({}, ...pages)
  },

  detail: (character: string) =>
    apiClient.get<CharacterDetail>(`/characters/${encodeURIComponent(character)}`).then((r) => r.data),

  /** Tách lại Hán Việt từng chữ cho cả kho (chỉ quản trị); dryRun chỉ báo cáo. */
  rebuild: (dryRun: boolean) =>
    apiClient.post<CharacterRebuildReport>('/admin/characters/rebuild', null, { params: { dryRun } }).then((r) => r.data),
}
