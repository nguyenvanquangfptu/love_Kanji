import { apiClient } from './client'
import type { AdminUser, CreateUserRequest, CreatedUser, Page } from './types'

export const adminUsersApi = {
  list: (page: number, size = 20) =>
    apiClient.get<Page<AdminUser>>('/admin/users', { params: { page, size } }).then((r) => r.data),

  /** Luôn tạo người học (ROLE_USER); mật khẩu tạm chỉ trả về lần này. */
  create: (payload: CreateUserRequest) => apiClient.post<CreatedUser>('/admin/users', payload).then((r) => r.data),
}
