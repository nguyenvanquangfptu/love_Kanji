export const canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window

/** Có giọng đọc tiếng Nhật trên máy này không (danh sách giọng có thể nạp trễ - chưa nạp thì coi như có). */
export function hasJapaneseVoice() {
  if (!canSpeak) return false
  const voices = window.speechSynthesis.getVoices()
  return voices.length === 0 || voices.some((v) => v.lang.toLowerCase().startsWith('ja'))
}

/** Đọc to tiếng Nhật bằng giọng đọc có sẵn của trình duyệt (Web Speech API); {@code rate} < 1 là đọc chậm. */
export function speakJapanese(text: string, rate = 0.9) {
  if (!canSpeak) return
  const synth = window.speechSynthesis
  synth.cancel()
  const utterance = new SpeechSynthesisUtterance(text.replace(/[()]/g, ''))
  utterance.lang = 'ja-JP'
  utterance.rate = rate
  const voice = synth.getVoices().find((v) => v.lang.toLowerCase().startsWith('ja'))
  if (voice) utterance.voice = voice
  synth.speak(utterance)
}
