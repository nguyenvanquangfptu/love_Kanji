import type { ExamMondai, ExamSectionName, JlptQuestionType } from '@/api/types'

/** Tên các phần thi: tiếng Nhật như trên đề thật, kèm tên tiếng Việt. */
export const SECTION_META: Record<ExamSectionName, { jp: string; vi: string }> = {
  VOCABULARY: { jp: '言語知識（文字・語彙）', vi: 'Từ vựng' },
  GRAMMAR: { jp: '言語知識（文法）', vi: 'Ngữ pháp' },
}

/** Các dạng câu (大問): tên và câu dẫn tiếng Nhật theo kiểu đề thật, kèm bản dịch. */
export const QUESTION_TYPE_META: Record<
  JlptQuestionType,
  { jp: string; vi: string; instruction: string; instructionVi: string }
> = {
  KANJI_READING: {
    jp: '漢字読み',
    vi: 'Đọc chữ Hán',
    instruction: '＿＿の ことばは ひらがなで どう かきますか。',
    instructionVi: 'Từ được gạch chân đọc (viết bằng hiragana) như thế nào?',
  },
  ORTHOGRAPHY: {
    jp: '表記',
    vi: 'Viết chữ Hán',
    instruction: '＿＿の ことばは どう かきますか。',
    instructionVi: 'Từ được gạch chân viết bằng chữ Hán như thế nào?',
  },
  CONTEXT: {
    jp: '文脈規定',
    vi: 'Điền từ theo ngữ cảnh',
    instruction: '（　　）に なにを いれますか。',
    instructionVi: 'Chọn từ hợp nhất điền vào chỗ trống.',
  },
  PARAPHRASE: {
    jp: '言い換え類義',
    vi: 'Diễn đạt tương đương',
    instruction: '＿＿の ぶんと だいたい おなじ いみの ぶんが あります。',
    instructionVi: 'Chọn câu có nghĩa gần nhất với câu có phần gạch chân.',
  },
  USAGE: {
    jp: '用法',
    vi: 'Cách dùng từ',
    instruction: 'つぎの ことばの つかいかたで いちばん いい ものを えらんで ください。',
    instructionVi: 'Chọn câu dùng từ đã cho đúng nhất.',
  },
  GRAMMAR_FORM: {
    jp: '文法形式の判断',
    vi: 'Chọn ngữ pháp',
    instruction: '（　　）に 何を 入れますか。',
    instructionVi: 'Chọn từ hoặc mẫu ngữ pháp điền vào chỗ trống.',
  },
  SENTENCE_ORDER: {
    jp: '文の組み立て',
    vi: 'Sắp xếp câu',
    instruction: '★ に 入る ものは どれですか。',
    instructionVi: 'Sắp xếp các vế thành câu đúng, chọn vế nằm ở vị trí ★.',
  },
  TEXT_GRAMMAR: {
    jp: '文章の文法',
    vi: 'Ngữ pháp trong đoạn văn',
    instruction: '文章を 読んで、（　　）に 入る ものを えらんで ください。',
    instructionVi: 'Đọc đoạn văn rồi chọn từ hoặc cụm điền vào chỗ trống.',
  },
}

/** Một 問題 kèm vị trí các câu của nó trong bài (câu hỏi xếp liền nhau theo thứ tự 問題). */
export interface MondaiRange extends ExamMondai {
  /** Chỉ số câu đầu tiên (từ 0) và số câu. */
  start: number
}

export function mondaiRanges(mondai: ExamMondai[] | null | undefined): MondaiRange[] {
  const ranges: MondaiRange[] = []
  let start = 0
  for (const m of mondai ?? []) {
    ranges.push({ ...m, start })
    start += m.questionCount
  }
  return ranges
}

/** 問題 chứa câu thứ {@code index}; null với thi nhanh. */
export function mondaiAt(ranges: MondaiRange[], index: number): MondaiRange | null {
  return ranges.find((m) => index >= m.start && index < m.start + m.questionCount) ?? null
}

export function formatMinutes(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${minutes}:${rest.toString().padStart(2, '0')}`
}
