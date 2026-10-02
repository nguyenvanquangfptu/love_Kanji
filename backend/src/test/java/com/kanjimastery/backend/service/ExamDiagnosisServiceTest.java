package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.ReviewRating;
import com.kanjimastery.backend.model.ReviewSource;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamDiagnosisServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ATTEMPT_ID = 30L;

    @Mock
    private UserExamAttemptRepository attemptRepository;
    @Mock
    private UserExamAnswerRepository answerRepository;
    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private SrsService srsService;

    @InjectMocks
    private ExamDiagnosisService diagnosisService;

    @Test
    void diagnose_shouldRecordEveryAnsweredQuestionForItsWords_asAnExamAnswer() {
        when(attemptRepository.markDiagnosed(eq(ATTEMPT_ID), any())).thenReturn(1);
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel("N5").status(ExamAttemptStatus.COMPLETED)
                .startedAt(LocalDateTime.now().minusMinutes(20)).build()));
        when(answerRepository.findByAttemptId(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "C", false), answer(2L, "A", true), answer(3L, null, false)));
        when(questionRepository.findAllWithWordsByIdIn(List.of(1L, 2L))).thenReturn(List.of(
                question(1L, QuizDirection.KANJI_TO_READING, 15L, 16L), question(2L, QuizDirection.MEANING, 17L)));

        int recorded = diagnosisService.diagnose(ATTEMPT_ID);

        // Câu 1 sai hỏi tới hai từ: ghi cho cả hai; câu 3 bỏ trống: không ghi.
        assertThat(recorded).isEqualTo(3);
        ArgumentCaptor<Long> kanjiIds = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<SrsService.Answer> answers = ArgumentCaptor.forClass(SrsService.Answer.class);
        verify(srsService, times(3)).recordQuizAnswer(eq(USER_ID), kanjiIds.capture(), answers.capture());
        assertThat(kanjiIds.getAllValues()).containsExactlyInAnyOrder(15L, 16L, 17L);
        assertThat(answers.getAllValues())
                .extracting(SrsService.Answer::source, SrsService.Answer::direction, SrsService.Answer::correct,
                        SrsService.Answer::rating, SrsService.Answer::chosenAnswer)
                .containsOnly(
                        tuple(ReviewSource.EXAM, QuizDirection.KANJI_TO_READING, false, ReviewRating.AGAIN, "ぎん"),
                        tuple(ReviewSource.EXAM, QuizDirection.MEANING, true, ReviewRating.GOOD, "một"));
    }

    @Test
    void diagnose_shouldDoNothing_whenTheExamWasAlreadyAddedToReview() {
        when(attemptRepository.markDiagnosed(eq(ATTEMPT_ID), any())).thenReturn(0);

        assertThat(diagnosisService.diagnose(ATTEMPT_ID)).isZero();

        verify(attemptRepository, never()).findById(anyLong());
        verifyNoInteractions(answerRepository, questionRepository, srsService);
    }

    private static UserExamAnswer answer(Long questionId, String selected, boolean correct) {
        return UserExamAnswer.builder().attemptId(ATTEMPT_ID).questionId(questionId).selectedOption(selected)
                .isCorrect(correct).build();
    }

    private static ExamQuestion question(Long id, String skill, Long... kanjiIds) {
        return ExamQuestion.builder().id(id).jlptLevel("N5").questionText("Câu " + id).optionA("một").optionB("hai")
                .optionC("ぎん").optionD("きん").correctOption(id == 1L ? "D" : "A").skill(skill)
                .kanjiIds(Set.of(kanjiIds)).build();
    }
}
