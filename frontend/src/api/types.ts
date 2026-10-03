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
  /** Mẹo nhớ chung do AI sinh (dựa trên âm Hán Việt); null nếu chưa có. */
  mnemonic: string | null
  tags: TagResponse[]
}

export interface MnemonicResponse {
  mnemonic: string
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
  /** Cách nhớ riêng người học tự ghi; null nếu chưa có. */
  personalNote: string | null
  /** Từ mới, chưa học lần nào. */
  newCard: boolean
  /** Số ngày tới lần ôn sau nếu chấm Quên, Khó, Nhớ, Dễ. */
  intervals: number[]
}

/** Kế hoạch ôn hôm nay - số thẻ ôn và từ mới vừa với thời gian ôn mỗi ngày. */
export interface DailyPlanResponse {
  dailyMinutes: number
  /** Đo từ nhịp ôn thật của người học (chưa đủ dữ liệu thì lấy mặc định). */
  secondsPerCard: number
  reviewCapacity: number
  /** Thẻ ôn đang đến hạn, không tính từ mới. */
  dueReviews: number
  reviewsToday: number
  newPerDay: number
  /** Số từ mới mỗi ngày người học muốn: tự đặt, cần để kịp ngày thi, hoặc mặc định. */
  newPerDayWanted: number
  newPerDaySource: 'DEFAULT' | 'CUSTOM' | 'EXAM'
  /** Số từ mới mỗi ngày đã bị giảm vì thời gian ôn không đủ cho lượng ôn sắp tới. */
  newPerDayLimitedByTime: boolean
  newLearnedToday: number
  /** Từ mới đã đưa vào Ôn tập mà chưa học. */
  newWaiting: number
  newToday: number
  estimatedMinutes: number
  goalSet: boolean
  targetLevel: JlptLevel | null
  /** Ngày dạng ISO (2026-12-06). */
  examDate: string | null
  /** Từ trong các bài từ N5 tới cấp mục tiêu mà bạn chưa học lần nào; null nếu chưa chọn cấp độ. */
  wordsToLearn: number | null
  /** Số từ mới học được trung bình mỗi ngày trong 2 tuần gần đây. */
  recentNewPerDay: number
  /** Ngày học xong mục tiêu nếu giữ nhịp 2 tuần gần đây; null nếu chưa có nhịp. */
  projectedFinish: string | null
  /** Kịp học xong trước ngày thi 2 tuần không; null nếu chưa đủ dữ liệu. */
  onTrack: boolean | null
  /** Bài nên thêm vào Ôn tập khi sắp hết từ mới. */
  nextLesson: { tagId: number; name: string; words: number } | null
}

/** Mục tiêu học. */
export interface LearningProfileResponse {
  configured: boolean
  targetLevel: JlptLevel | null
  examDate: string | null
  dailyMinutes: number
  /** null = để app tự tính. */
  newWordsPerDay: number | null
  scheduler: Scheduler
  /** Tỉ lệ nhớ mong muốn khi xếp lịch bằng FSRS (0.8 = 80%). */
  desiredRetention: number
  fsrs: FsrsParametersResponse
}

export type LearningProfileRequest = Omit<LearningProfileResponse, 'configured' | 'fsrs'>

/** Mô hình trí nhớ FSRS của người học: thông số chung hay đã tối ưu theo lịch sử ôn của chính họ. */
export interface FsrsParametersResponse {
  personalized: boolean
  optimizedAt: string | null
  /** Số từ đã học và ôn lại vào một ngày khác, theo mức chấm lần đầu Quên, Khó, Nhớ, Dễ. */
  firstReviews: number[]
  /** Một mức chấm cần chừng này từ mới tối ưu được. */
  minFirstReviews: number
  /** Số ngày còn nhớ 90% một từ mới, theo mức chấm lần đầu - đang dùng để xếp lịch. */
  initialStabilities: number[]
  /** Như trên với thông số chung của FSRS. */
  defaultInitialStabilities: number[]
}

/** Thuật toán xếp lịch ôn: SM-2 cổ điển hoặc mô hình trí nhớ FSRS. */
export type Scheduler = 'SM2' | 'FSRS'

/** Tiến bộ của người học, tính từ lịch sử trả lời. */
export interface ProgressResponse {
  /** 8 tuần gần nhất (thứ Hai đầu tuần), cũ trước. Tỉ lệ nhớ = remembered / reviews ở các lần ôn đúng hạn. */
  weeks: { weekStart: string; reviews: number; remembered: number }[]
  /** 14 ngày học gần nhất, cũ trước. */
  days: { day: string; reviews: number; newWords: number }[]
  /** Trắc nghiệm 30 ngày gần nhất theo hướng hỏi. */
  directions: { direction: QuizDirection; answers: number; correct: number }[]
  /** Những đáp án sai chọn nhiều lần nhất trong 90 ngày. */
  confusions: {
    kanjiId: number
    character: string
    reading: string | null
    meaning: string
    direction: QuizDirection
    chosenAnswer: string
    times: number
  }[]
  /**
   * 30 ngày gần nhất: lúc đến lượt ôn, FSRS đoán còn nhớ bao nhiêu (predicted) và thật sự nhớ được bao nhiêu (actual),
   * trên `reviews` lượt ôn; null nếu chưa đủ lượt ôn để so.
   */
  calibration: { reviews: number; predicted: number; actual: number } | null
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
  words: { kanji: KanjiResponse; lapseCount: number; nextReviewAt: string; personalNote: string | null }[]
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
  /** Câu ví dụ kiểu đề JLPT, gạch chân `highlight`; null nếu không có. */
  sentence: string | null
  highlight: string | null
  /** Dạng câu JLPT; null với câu chỉ dùng cho thi nhanh. */
  questionType: JlptQuestionType | null
  /** Câu điền vào chỗ trống 【blankNo】 của đoạn văn passageId (文章の文法); null với câu đứng riêng. */
  passageId: number | null
  blankNo: number | null
}

/** Đoạn văn của 問題3 文章の文法: chỗ trống đánh dấu 【1】【2】... theo blankNo của câu hỏi. */
export interface ExamPassage {
  id: number
  title: string | null
  content: string
}

/** Các phần của đề JLPT (chỉ phần Kiến thức ngôn ngữ). */
export type ExamSectionName = 'VOCABULARY' | 'GRAMMAR'

/** Dạng câu (大問) trong đề JLPT. */
export type JlptQuestionType =
  | 'KANJI_READING'
  | 'ORTHOGRAPHY'
  | 'CONTEXT'
  | 'PARAPHRASE'
  | 'USAGE'
  | 'GRAMMAR_FORM'
  | 'SENTENCE_ORDER'
  | 'TEXT_GRAMMAR'

/** Một 問題 của phần đề JLPT đang làm: câu hỏi của bài xếp liền nhau theo thứ tự các 問題. */
export interface ExamMondai {
  /** Số thứ tự trong đề thật (問題1, 問題2...). */
  number: number
  type: JlptQuestionType
  questionCount: number
  /** Số câu của dạng này trong đề thật - lớn hơn questionCount khi ngân hàng câu hỏi chưa đủ. */
  plannedCount: number
}

export interface StartExamResponse {
  attemptId: number
  jlptLevel: string
  questions: ExamQuestionPublicResponse[]
  remainingSeconds: number
  startedAt: string
  /** Buổi làm đề JLPT và phần đang làm; null với thi nhanh. */
  sittingId: number | null
  section: ExamSectionName | null
  mondai: ExamMondai[] | null
  /** Đoạn văn của các câu 文章の文法 trong bài. */
  passages: ExamPassage[] | null
}

export interface StartJlptExamRequest {
  jlptLevel: string
  sections: ExamSectionName[]
}

/** Cấu trúc đề JLPT của một cấp độ và số câu hỏi hiện có. */
export interface JlptLevelResponse {
  jlptLevel: string
  sections: {
    name: ExamSectionName
    /** Số câu và thời gian của đề thật. */
    plannedQuestions: number
    plannedMinutes: number
    /** Đề ghép được lúc này (dạng chưa đủ câu thì ít câu hơn, thời gian giảm theo tỉ lệ); 0 câu = chưa làm được. */
    questionCount: number
    minutes: number
    mondai: { number: number; type: JlptQuestionType; plannedCount: number; available: number }[]
  }[]
}

export type ExamSittingStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED'

/** Một buổi làm đề JLPT: các phần đã chọn (theo thứ tự làm bài) và kết quả từng phần. */
export interface ExamSittingResponse {
  sittingId: number
  jlptLevel: string
  status: ExamSittingStatus
  startedAt: string
  finishedAt: string | null
  sections: {
    name: ExamSectionName
    /** null = chưa làm. */
    attemptId: number | null
    status: ExamAttemptStatus | null
    totalScore: number | null
    /** null khi chưa chốt điểm. */
    totalQuestions: number | null
    timeSpentSeconds: number | null
    durationSeconds: number | null
  }[]
  /** Phần làm tiếp theo; null khi đang làm dở một phần, đã làm hết hoặc buổi thi đã kết thúc. */
  nextSection: ExamSectionName | null
}

export interface SaveAnswerRequest {
  questionId: number
  selectedOption: 'A' | 'B' | 'C' | 'D' | ''
}

export interface ExamSessionResponse {
  attemptId: number
  remainingSeconds: number
  answers: Record<number, string>
  sittingId: number | null
  section: ExamSectionName | null
}

export type ExamAttemptStatus = 'IN_PROGRESS' | 'COMPLETED' | 'TIMEOUT'

export interface ExamResultResponse {
  attemptId: number
  status: ExamAttemptStatus
  totalScore: number
  timeSpentSeconds: number
  submittedAt: string
  /** Buổi làm đề JLPT của lượt thi này; null với thi nhanh. */
  sittingId: number | null
}

export interface QuestionReviewItem {
  questionId: number
  questionText: string
  sentence: string | null
  highlight: string | null
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctOption: string
  selectedOption: string | null
  correct: boolean
  explanation: string | null
  /** Kỹ năng câu hỏi kiểm tra; null nếu chưa phân loại. */
  skill: QuizDirection | null
  questionType: JlptQuestionType | null
  passageId: number | null
  blankNo: number | null
}

export interface ExamReviewResponse {
  attemptId: number
  jlptLevel: string
  status: ExamAttemptStatus
  totalScore: number
  totalQuestions: number
  timeSpentSeconds: number
  questions: QuestionReviewItem[]
  /** Điểm theo kỹ năng (đọc, viết, nghĩa); kỹ năng không có câu nào trong bài thì không có. */
  skills: { skill: QuizDirection; correct: number; total: number }[]
  /** Từ vựng của các câu làm sai (không tính câu bỏ trống), theo thứ tự câu hỏi. */
  wrongWords: { kanjiId: number; character: string; reading: string | null; meaning: string }[]
  /** Từ của câu sai đã được đưa vào Ôn tập (bài thi trước khi có tính năng này thì chưa). */
  addedToReview: boolean
  /** Buổi làm đề JLPT và phần của lượt thi này; null với thi nhanh. */
  sittingId: number | null
  section: ExamSectionName | null
  /** Điểm theo từng 問題 của phần đề JLPT, theo thứ tự trong đề; rỗng với thi nhanh. */
  mondai: { number: number; type: JlptQuestionType; correct: number; total: number }[]
  /** Đoạn văn của các câu 文章の文法 trong bài. */
  passages: ExamPassage[]
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

/** Một điểm ngữ pháp (〜てから...) của một cấp độ. */
export interface GrammarPoint {
  id: number
  jlptLevel: string
  /** Bài trong giáo trình (N4-26...); null = không theo bài. */
  lesson: string | null
  pattern: string
  /** Cách nối: Vて + から. */
  connection: string | null
  meaningVi: string
  explanationVi: string | null
  /** Số câu thi gắn với điểm này: đã duyệt và đang chờ duyệt. */
  approvedQuestions: number
  draftQuestions: number
}

export interface GrammarPointRequest {
  jlptLevel: string
  lesson: string
  pattern: string
  connection: string
  meaningVi: string
  explanationVi: string
}

/** Kết quả nhập CSV: số điểm mới / được cập nhật / giữ nguyên, và các dòng bị bỏ qua kèm lý do. */
export interface GrammarImportResult {
  created: number
  updated: number
  unchanged: number
  errors: string[]
}

/** Trạng thái duyệt của câu thi: chỉ câu APPROVED được lấy vào đề. */
export type ExamQuestionStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'RETIRED'

/** Cảnh báo của bước kiểm tra tự động cho người duyệt. */
export type ExamQuestionFlag = 'AMBIGUOUS' | 'WRONG_ANSWER' | 'ABOVE_LEVEL'

/** Một câu thi trên trang duyệt (có đáp án đúng). */
export interface AdminExamQuestion {
  id: number
  jlptLevel: string
  questionType: JlptQuestionType
  skill: QuizDirection | null
  status: ExamQuestionStatus
  flag: ExamQuestionFlag | null
  reviewNote: string | null
  reviewedAt: string | null
  source: 'MANUAL' | 'GENERATED' | 'AI'
  questionText: string
  sentence: string | null
  highlight: string | null
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctOption: 'A' | 'B' | 'C' | 'D'
  explanation: string | null
  passageId: number | null
  blankNo: number | null
  words: { id: number; character: string; reading: string | null }[]
  grammarPoints: { id: number; pattern: string }[]
}

export interface AdminExamQuestionRequest {
  questionText: string
  sentence: string
  highlight: string
  optionA: string
  optionB: string
  optionC: string
  optionD: string
  correctOption: 'A' | 'B' | 'C' | 'D'
  explanation: string
}

/** Ngân hàng câu đề JLPT của một cấp độ theo từng dạng câu. */
export interface QuestionBankStats {
  jlptLevel: string
  types: {
    section: ExamSectionName
    type: JlptQuestionType
    perExam: number
    approved: number
    draft: number
    rejected: number
    retired: number
    /** Số đề đủ câu đã duyệt. */
    exams: number
  }[]
}

/** Kết quả duyệt một lượt nhiều câu: số câu đã duyệt, và các câu bỏ qua kèm lý do. */
export interface BulkApprovalResult {
  approved: number
  skipped: { id: number; reason: string }[]
}

/** Kết quả một lần nhờ AI viết nháp: số câu vào hàng chờ duyệt (trong đó có cảnh báo), bị loại, không đọc được. */
export interface QuestionDraftResult {
  drafted: number
  flagged: number
  rejected: number
  unreadable: number
}

/** Kết quả nhờ AI viết một đoạn văn 文章の文法: chờ duyệt, hoặc bị loại vì sai cấu trúc (lý do ở ghi chú của đoạn). */
export interface PassageDraftResult {
  passageId: number
  status: 'DRAFT' | 'REJECTED'
  flag: ExamQuestionFlag | null
  /** Số câu hỏi (chỗ trống) đọc được. */
  questions: number
}

/** Một đoạn văn 文章の文法 trên trang duyệt, kèm các câu hỏi theo thứ tự chỗ trống. */
export interface AdminExamPassage {
  id: number
  jlptLevel: string
  title: string | null
  content: string
  status: ExamQuestionStatus
  source: 'MANUAL' | 'GENERATED' | 'AI'
  flag: ExamQuestionFlag | null
  reviewNote: string | null
  reviewedAt: string | null
  questions: AdminExamQuestion[]
}
