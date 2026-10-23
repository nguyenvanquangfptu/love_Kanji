import { lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '@/routes/ProtectedRoute'
import { AdminRoute } from '@/routes/AdminRoute'
import { AppLayout, FocusLayout } from '@/components/AppLayout'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { FlashcardPage } from '@/pages/FlashcardPage'
import { HardWordsPage } from '@/pages/HardWordsPage'
import { StudyBrowsePage } from '@/pages/StudyBrowsePage'
import { StudyVocabListPage } from '@/pages/StudyVocabListPage'
import { StudyFlashcardPage } from '@/pages/StudyFlashcardPage'
import { StudyQuizPage } from '@/pages/StudyQuizPage'
import { useThemeSync } from '@/lib/theme'

// Trang học và ôn hằng ngày nằm trong bundle chính; các trang ít mở hơn (thi, tiến bộ, quản trị...) tải riêng khi cần,
// nên người học không phải tải code quản trị và biểu đồ ngay lần đầu. Layout bọc <Outlet /> trong <Suspense>.
const GoalPage = lazy(() => import('@/pages/GoalPage').then((m) => ({ default: m.GoalPage })))
const ProgressPage = lazy(() => import('@/pages/ProgressPage').then((m) => ({ default: m.ProgressPage })))
const ExamSetupPage = lazy(() => import('@/pages/ExamSetupPage').then((m) => ({ default: m.ExamSetupPage })))
const ExamWorkspacePage = lazy(() => import('@/pages/ExamWorkspacePage').then((m) => ({ default: m.ExamWorkspacePage })))
const ExamResultPage = lazy(() => import('@/pages/ExamResultPage').then((m) => ({ default: m.ExamResultPage })))
const ExamSittingPage = lazy(() => import('@/pages/ExamSittingPage').then((m) => ({ default: m.ExamSittingPage })))
const GrammarPracticePage = lazy(() =>
  import('@/pages/GrammarPracticePage').then((m) => ({ default: m.GrammarPracticePage })),
)
const AdminKanjiPage = lazy(() => import('@/pages/AdminKanjiPage').then((m) => ({ default: m.AdminKanjiPage })))
const AdminGrammarPage = lazy(() => import('@/pages/AdminGrammarPage').then((m) => ({ default: m.AdminGrammarPage })))
const AdminQuestionsPage = lazy(() =>
  import('@/pages/AdminQuestionsPage').then((m) => ({ default: m.AdminQuestionsPage })),
)
const CharacterPage = lazy(() => import('@/pages/CharacterPage').then((m) => ({ default: m.CharacterPage })))
const SearchPage = lazy(() => import('@/pages/SearchPage').then((m) => ({ default: m.SearchPage })))
const WelcomePage = lazy(() => import('@/pages/WelcomePage').then((m) => ({ default: m.WelcomePage })))

export default function App() {
  useThemeSync()
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/study" replace />} />
          <Route path="study" element={<StudyBrowsePage />} />
          <Route path="study/vocab" element={<StudyVocabListPage />} />
          <Route path="flashcards" element={<FlashcardPage />} />
          <Route path="flashcards/hard-words" element={<HardWordsPage />} />
          <Route path="goals" element={<GoalPage />} />
          <Route path="characters/:character" element={<CharacterPage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="progress" element={<ProgressPage />} />
          <Route path="exam" element={<ExamSetupPage />} />
          <Route path="exam/:attemptId/result" element={<ExamResultPage />} />
          <Route path="exam/jlpt/:sittingId" element={<ExamSittingPage />} />
          <Route element={<AdminRoute />}>
            <Route path="admin" element={<Navigate to="/admin/kanji" replace />} />
            <Route path="admin/kanji" element={<AdminKanjiPage />} />
            <Route path="admin/grammar" element={<AdminGrammarPage />} />
            <Route path="admin/questions" element={<AdminQuestionsPage />} />
          </Route>
        </Route>

        <Route element={<FocusLayout />}>
          <Route path="study/flashcards" element={<StudyFlashcardPage />} />
          <Route path="study/quiz" element={<StudyQuizPage />} />
          <Route path="exam/:attemptId" element={<ExamWorkspacePage />} />
          <Route path="exam/grammar-practice" element={<GrammarPracticePage />} />
          <Route path="welcome" element={<WelcomePage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
