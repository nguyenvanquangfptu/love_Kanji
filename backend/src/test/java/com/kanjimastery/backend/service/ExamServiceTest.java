package com.kanjimastery.backend.service;

import com.kanjimastery.backend.config.ExamProperties;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import com.kanjimastery.backend.model.UserExamAnswer;
import com.kanjimastery.backend.model.UserExamAttempt;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserExamAnswerRepository;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ATTEMPT_ID = 30L;

    @Mock
    private ExamQuestionRepository questionRepository;
    @Mock
    private UserExamAttemptRepository attemptRepository;
    @Mock
    private UserExamAnswerRepository answerRepository;
    @Mock
    private ExamSessionStore examSessionStore;
    @Mock
    private ExamFinalizationService examFinalizationService;
    @Spy
    private ExamProperties examProperties = new ExamProperties();
    @Mock
    private KanjiRepository kanjiRepository;

    @InjectMocks
    private ExamService examService;

    @Test
    void getReview_shouldScoreEachSkill_inReadingWritingMeaningOrder() {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel("N5").status(ExamAttemptStatus.COMPLETED).totalScore(3)
                .startedAt(LocalDateTime.now().minusMinutes(20)).build()));
        when(answerRepository.findByAttemptId(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "B", true), answer(2L, "A", false), answer(3L, null, false), answer(4L, "C", true),
                answer(5L, "D", true)));
        when(questionRepository.findAllWithWordsByIdIn(anyList())).thenReturn(List.of(
                question(1L, QuizDirection.MEANING), question(2L, QuizDirection.KANJI_TO_READING),
                question(3L, QuizDirection.KANJI_TO_READING), question(4L, QuizDirection.KANJI_TO_READING),
                question(5L, null)));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        // Câu chưa phân loại kỹ năng không tính vào kỹ năng nào; kỹ năng không có câu nào thì không hiện.
        assertThat(review.getSkills())
                .extracting(ExamReviewResponse.SkillScore::getSkill, ExamReviewResponse.SkillScore::getCorrect,
                        ExamReviewResponse.SkillScore::getTotal)
                .containsExactly(tuple(QuizDirection.KANJI_TO_READING, 1, 3), tuple(QuizDirection.MEANING, 1, 1));
        assertThat(review.getQuestions().get(0).getSkill()).isEqualTo(QuizDirection.MEANING);
    }

    @Test
    void getReview_shouldListTheWordsOfWrongAnswers_butNotOfSkippedQuestions() {
        when(attemptRepository.findById(ATTEMPT_ID)).thenReturn(Optional.of(UserExamAttempt.builder().id(ATTEMPT_ID)
                .userId(USER_ID).jlptLevel("N5").status(ExamAttemptStatus.TIMEOUT).totalScore(1)
                .startedAt(LocalDateTime.now().minusMinutes(31)).diagnosedAt(LocalDateTime.now()).build()));
        when(answerRepository.findByAttemptId(ATTEMPT_ID)).thenReturn(List.of(
                answer(1L, "B", false), answer(2L, null, false), answer(3L, "A", true), answer(4L, "C", false)));
        when(questionRepository.findAllWithWordsByIdIn(anyList())).thenReturn(List.of(
                question(1L, QuizDirection.MEANING, 15L), question(2L, QuizDirection.MEANING, 25L),
                question(3L, QuizDirection.MEANING, 14L), question(4L, QuizDirection.KANJI_TO_READING, 15L)));
        when(kanjiRepository.findAllById(Set.of(15L))).thenReturn(List.of(
                Kanji.builder().id(15L).character("水").reading("みず").meaning("Nước").build()));

        ExamReviewResponse review = examService.getReview(USER_ID, ATTEMPT_ID);

        // Câu 2 bỏ trống (hết giờ) không tính; 水 sai ở hai câu chỉ hiện một lần.
        assertThat(review.getWrongWords()).extracting(ExamReviewResponse.Word::getCharacter).containsExactly("水");
        assertThat(review.isAddedToReview()).isTrue();
    }

    private static UserExamAnswer answer(Long questionId, String selected, boolean correct) {
        return UserExamAnswer.builder().attemptId(ATTEMPT_ID).questionId(questionId).selectedOption(selected)
                .isCorrect(correct).build();
    }

    private static ExamQuestion question(Long id, String skill, Long... kanjiIds) {
        return ExamQuestion.builder().id(id).jlptLevel("N5").questionText("Câu " + id).optionA("1").optionB("2")
                .optionC("3").optionD("4").correctOption("A").skill(skill).kanjiIds(Set.of(kanjiIds)).build();
    }
}
