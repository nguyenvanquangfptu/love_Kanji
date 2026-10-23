// Gắn class "dark" lên <html> trước khi trang vẽ lần đầu, để người dùng chế độ tối không thấy màn hình trắng chớp lên.
// File riêng (không viết thẳng trong index.html) vì CSP chỉ cho chạy script từ chính trang ('self').
// Cùng cách chọn với lib/theme.ts: "light" / "dark" / "system" (theo máy, mặc định).
;(function () {
  var theme = 'system'
  try {
    var saved = JSON.parse(localStorage.getItem('kanji-mastery-preferences') || '{}')
    if (saved && saved.state && saved.state.theme) theme = saved.state.theme
  } catch {
    // localStorage bị chặn hoặc dữ liệu hỏng: theo máy
  }
  var dark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', dark)
  var meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', dark ? '#131f24' : '#58cc02')
})()
