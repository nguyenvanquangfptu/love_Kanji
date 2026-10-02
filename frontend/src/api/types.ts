export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors?: string[]
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresInSeconds: number
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

/** Trang kết quả kiểu Spring Data Page<T>. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface TagResponse {
  id: number
  name: string
  /** Chỉ có khi lấy qua GET /tags. */
  wordCount?: number
}

export interface TagRequest {
  name: string
}

export interface KanjiResponse {
  id: number
  character: string
  hanViet: string
  /** Phiên âm hiragana của cả từ; null nếu từ không có chữ Hán. */
  reading: string | null
  strokeCount: number
  jlptLevel: string
  meaning: string
  /** Câu ví dụ dùng trong trắc nghiệm (do AI sinh hoặc admin sửa); null nếu chưa có. */
  exampleSentence: string | null
  tags: TagResponse[]
}

export interface KanjiRequest {
  character: string
  hanViet: string
  reading: string
  strokeCount: number
  jlptLevel: string
  meaning: string
  exampleSentence: string
  tagIds: number[]
}

export interface DailyCardResponse {
  srsId: number
  kanji: KanjiResponse
  repetitionCount: number
  easinessFactor: number
  reviewIntervalDays: number
  nextReviewAt: string
  lastReviewedAt: string | null
  lapseCount: number
  /** Quên đủ nhiều lần để thành từ khó. */
  hardWord: boolean
}

/** 1 Quên, 2 Khó, 3 Nhớ, 4 Dễ. */
export type ReviewRating = 1 | 2 | 3 | 4

export interface ReviewRequest {
  kanjiId: number
  rating: ReviewRating
  /** Thời gian từ lúc hiện thẻ tới lúc lật thẻ (ms). */
  responseMs?: number
}

export interface ReviewResponse {
  kanjiId: number
  repetitionCount: number
  easinessFactor: number
  reviewIntervalDays: number
  nextReviewAt: string
}

export interface SrsStatsResponse {
  totalCardsStarted: number
  dueForReview: number
  stillLearning: number
  deeplyMemorized: number
  /** Số từ khó: quên từ `HardWordsResponse.lapseThreshold` lần trở lên. */
  hardWords: number
}

export interface HardWordsResponse {
  /** Quên từ chừng này lần trở lên là từ khó. */
  lapseThreshold: number
  /** Quên nhiều lần nhất trước. */
  words: { kanji: KanjiResponse; lapseCount: number; nextReviewAt: string }[]
}

export interface AddSrsCardsResponse {
  added: number
  alreadyInReview: number
}

export interface SrsTagStatusResponse {
  totalWords: number
  inReview: number
}

export interface StartExamRequest {
  jlptLevel: string
  questionCount: number
}

export interface ExamQuestionPublicResponse {
  id: number
  questionText: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
}

export interface StartExamResponse {
  attemptId: number
  jlptLevel: string
  questions: ExamQuestionPublicResponse[]
  remainingSeconds: number
  startedAt: string
}

export interface SaveAnswerRequest {
  questionId: number
  selectedOption: 'A' | 'B' | 'C' | 'D' | ''
}

export interface ExamSessionResponse {
  attemptId: number
  remainingSeconds: number
  answers: Record<number, string>
}

export type ExamAttemptStatus = 'IN_PROGRESS' | 'COMPLETED' | 'TIMEOUT'

export interface ExamResultResponse {
  attemptId: number
  status: ExamAttemptStatus
  totalScore: number
  timeSpentSeconds: number
  submittedAt: string
}

export interface QuestionReviewItem {
  questionId: number
  questionText: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctOption: string
  selectedOption: string | null
  correct: boolean
  explanation: string | null
}

export interface ExamReviewResponse {
  attemptId: number
  jlptLevel: string
  status: ExamAttemptStatus
  totalScore: number
  totalQuestions: number
  timeSpentSeconds: number
  questions: QuestionReviewItem[]
}

export interface LeaderboardEntryResponse {
  rank: number
  userId: number
  username: string
  score: number
}

export interface MyRankResponse {
  userId: number
  rank: number | null
  score: number | null
}

export const JLPT_LEVELS = ['N5', 'N4', 'N3', 'N2', 'N1'] as const
export type JlptLevel = (typeof JLPT_LEVELS)[number]

export type QuizDirection = 'KANJI_TO_READING' | 'READING_TO_KANJI' | 'MEANING'

/** adaptive: ưu tiên từ người học hay sai (mặc định); random: chọn đều trong cả bài. */
export type QuizMode = 'adaptive' | 'random'

export interface QuizQuestionResponse {
  kanjiId: number
  direction: QuizDirection
  /** Từ cần hỏi: dạng Kanji, hoặc hiragana nếu direction = READING_TO_KANJI. */
  prompt: string
  /** Câu ví dụ kiểu đề JLPT có chứa nguyên văn `prompt` (cần gạch chân); null nếu chưa có. */
  sentence: string | null
  choices: string[]
  correctIndex: number
  character: string
  reading: string | null
  meaning: string
  /** Đáp án sai bạn từng chọn nhiều nhất cho từ này (theo hướng hỏi này), có trong `choices`; null nếu chưa từng nhầm. */
  personalTrap: string | null
  personalTrapCount: number
  /** Khi `personalTrap` là cách viết của một từ có thật: cách đọc và nghĩa của từ đó. */
  personalTrapReading: string | null
  personalTrapMeaning: string | null
}

export interface QuizAnswerRequest {
  kanjiId: number
  direction: QuizDirection
  chosenAnswer: string
  /** Thời gian từ lúc hiện câu hỏi tới lúc chọn đáp án (ms). */
  responseMs?: number
}

export interface QuizAnswerResponse {
  correct: boolean
  /** Từ có nằm trong lịch ôn không - từ làm sai luôn được đưa vào. */
  inReview: boolean
  nextReviewAt: string | null
}
