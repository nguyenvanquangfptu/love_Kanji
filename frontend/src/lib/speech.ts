export const canSpeak = typeof window !== 'undefined' && 'speechSynthesis' in window

/** Đọc to tiếng Nhật bằng giọng đọc có sẵn của trình duyệt (Web Speech API). */
export function speakJapanese(text: string) {
  if (!canSpeak) return
  const synth = window.speechSynthesis
  synth.cancel()
  const utterance = new SpeechSynthesisUtterance(text.replace(/[()]/g, ''))
  utterance.lang = 'ja-JP'
  utterance.rate = 0.9
  const voice = synth.getVoices().find((v) => v.lang.toLowerCase().startsWith('ja'))
  if (voice) utterance.voice = voice
  synth.speak(utterance)
}
