import { apiClient } from './client'
import type { GrammarImportResult, GrammarPoint, GrammarPointRequest } from './types'

/** Chỉ ADMIN: danh sách điểm ngữ pháp theo cấp độ. */
export const grammarApi = {
  list: (level?: string) =>
    apiClient.get<GrammarPoint[]>('/admin/grammar-points', { params: { level } }).then((r) => r.data),

  create: (payload: GrammarPointRequest) =>
    apiClient.post<GrammarPoint>('/admin/grammar-points', payload).then((r) => r.data),

  update: (id: number, payload: GrammarPointRequest) =>
    apiClient.put<GrammarPoint>(`/admin/grammar-points/${id}`, payload).then((r) => r.data),

  delete: (id: number) => apiClient.delete<void>(`/admin/grammar-points/${id}`).then((r) => r.data),

  /** Cột: cấp độ, bài, mẫu, nghĩa tiếng Việt, cách nối, giải thích; cùng cấp độ + mẫu thì cập nhật. */
  importCsv: (csv: string) =>
    apiClient.post<GrammarImportResult>('/admin/grammar-points/import', { csv }).then((r) => r.data),

  exportCsv: (level?: string) =>
    apiClient
      .get<Blob>('/admin/grammar-points/export', { params: { level }, responseType: 'blob' })
      .then((r) => r.data),
}
