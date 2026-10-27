import { apiClient } from './client'
import type { SearchResult } from './types'

export const searchApi = {
  /** Tìm bằng chữ Hán, kana, romaji, Hán Việt hay nghĩa (có dấu hay không); tối đa 30 từ, khớp đúng trước. */
  search: (q: string) => apiClient.get<SearchResult[]>('/search', { params: { q } }).then((r) => r.data),
}
