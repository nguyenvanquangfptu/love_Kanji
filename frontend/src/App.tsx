import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '@/routes/ProtectedRoute'
import { AdminRoute } from '@/routes/AdminRoute'
import { AppLayout, FocusLayout } from '@/components/AppLayout'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { FlashcardPage } from '@/pages/FlashcardPage'
import { HardWordsPage } from '@/pages/HardWordsPage'
import { GoalPage } from '@/pages/GoalPage'
import { ProgressPage } from '@/pages/ProgressPage'
import { ExamSetupPage } from '@/pages/ExamSetupPage'
import { ExamWorkspacePage } from '@/pages/ExamWorkspacePage'
import { ExamResultPage } from '@/pages/ExamResultPage'
import { ExamSittingPage } from '@/pages/ExamSittingPage'
import { AdminKanjiPage } from '@/pages/AdminKanjiPage'
import { AdminGrammarPage } from '@/pages/AdminGrammarPage'
import { StudyBrowsePage } from '@/pages/StudyBrowsePage'
import { StudyVocabListPage } from '@/pages/StudyVocabListPage'
import { StudyFlashcardPage } from '@/pages/StudyFlashcardPage'
import { StudyQuizPage } from '@/pages/StudyQuizPage'

export default function App() {
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
          <Route path="progress" element={<ProgressPage />} />
          <Route path="exam" element={<ExamSetupPage />} />
          <Route path="exam/:attemptId/result" element={<ExamResultPage />} />
          <Route path="exam/jlpt/:sittingId" element={<ExamSittingPage />} />
          <Route element={<AdminRoute />}>
            <Route path="admin" element={<Navigate to="/admin/kanji" replace />} />
            <Route path="admin/kanji" element={<AdminKanjiPage />} />
            <Route path="admin/grammar" element={<AdminGrammarPage />} />
          </Route>
        </Route>

        <Route element={<FocusLayout />}>
          <Route path="study/flashcards" element={<StudyFlashcardPage />} />
          <Route path="study/quiz" element={<StudyQuizPage />} />
          <Route path="exam/:attemptId" element={<ExamWorkspacePage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
