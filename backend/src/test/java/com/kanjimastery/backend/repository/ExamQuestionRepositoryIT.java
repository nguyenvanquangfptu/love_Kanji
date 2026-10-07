package com.kanjimastery.backend.repository;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.QuizDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** Câu thi mẫu được gắn kỹ năng và từ vựng (V18) trên PostgreSQL thật. */
class ExamQuestionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private KanjiRepository kanjiRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private GrammarPointRepository grammarPointRepository;

    /** Không chạy trong transaction, như ExamService.getReview: cả hai tập liên kết phải được nạp sẵn. */
    @Test
    void findAllWithLinksByIdIn_shouldLoadEveryWordAndGrammarPoint_withoutAnOpenTransaction() {
        List<Long> words = kanjiRepository.findAll().stream().limit(3).map(Kanji::getId).toList();
        List<GrammarPoint> points = grammarPointRepository.saveAll(List.of(
                GrammarPoint.builder().jlptLevel(JlptLevel.N1).pattern("[RepoIT] 〜ものの").meaningVi("tuy").build(),
                GrammarPoint.builder().jlptLevel(JlptLevel.N1).pattern("[RepoIT] 〜からこそ").meaningVi("chính vì").build()));
        ExamQuestion saved = question("Câu có cả từ lẫn ngữ pháp", JlptQuestionType.GRAMMAR_FORM);
        saved.getKanjiIds().addAll(words);
        points.forEach(point -> saved.getGrammarPointIds().add(point.getId()));
        Long id = questionRepository.save(saved).getId();
        try {
            List<ExamQuestion> loaded = questionRepository.findAllWithLinksByIdIn(List.of(id));

            assertThat(loaded).singleElement().satisfies(question -> {
                assertThat(question.getKanjiIds()).containsExactlyInAnyOrderElementsOf(words);
                assertThat(question.getGrammarPointIds()).containsExactlyInAnyOrderElementsOf(
                        points.stream().map(GrammarPoint::getId).toList());
            });
        } finally {
            questionRepository.deleteById(id);
            grammarPointRepository.deleteAll(points);
        }
    }

    /** Không chạy trong transaction: câu lệnh lỗi đầu tiên sẽ làm hỏng cả transaction. Không dòng nào được ghi. */
    @Test
    void database_shouldRejectStatusesAndLevelsNoEnumKnows() {
        String insert = """
                INSERT INTO exam_questions (jlpt_level, question_text, option_a, option_b, option_c, option_d,
                                            correct_option, status, source)
                VALUES (?, 'Câu', '1', '2', '3', '4', 'A', ?, 'MANUAL')
                """;

        assertThatThrownBy(() -> jdbc.update(insert, "N1", "APPROVD"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_exam_questions_status");
        assertThatThrownBy(() -> jdbc.update(insert, "N6", "APPROVED"))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_exam_questions_jlpt_level");
    }

    @Test
    @Transactional
    void seedQuestions_shouldEachTestOneSkillAndTheKanjiTheyAskAbout() {
        List<ExamQuestion> seed = questionRepository.findAll().stream()
                .filter(question -> ExamQuestionSource.MANUAL.equals(question.getSource()))
                .toList();

        assertThat(seed).hasSize(20).allSatisfy(question -> {
            assertThat(question.getSkill()).isNotNull();
            assertThat(question.getKanjiIds()).hasSize(1);
        });
        assertThat(seed.stream().collect(Collectors.groupingBy(ExamQuestion::getSkill, Collectors.counting())))
                .containsOnly(entry(QuizDirection.MEANING, 13L), entry(QuizDirection.KANJI_TO_READING, 5L),
                        entry(QuizDirection.READING_TO_KANJI, 2L));

        Map<Long, Kanji> words = kanjiRepository.findAllById(seed.stream()
                        .flatMap(question -> question.getKanjiIds().stream()).toList()).stream()
                .collect(Collectors.toMap(Kanji::getId, Function.identity()));
        // Chữ được gắn chính là chữ câu hỏi nhắc tới (hoặc là đáp án đúng của câu "chữ nào...").
        assertThat(seed).allSatisfy(question -> {
            String character = words.get(question.getKanjiIds().iterator().next()).getCharacter();
            assertThat(question.getQuestionText() + correctAnswer(question)).contains(character);
        });
    }

    @Test
    @Transactional
    void findRandomUnclassifiedByLevel_shouldLeaveOutJlptGrammarQuestions() {
        ExamQuestion untyped = questionRepository.save(question("Câu cũ chưa phân loại", null));
        questionRepository.save(question("Câu ngữ pháp", JlptQuestionType.GRAMMAR_FORM));

        // Thi nhanh chỉ hỏi từ vựng: câu ngữ pháp (không kỹ năng, có dạng câu) chỉ vào đề JLPT.
        assertThat(questionRepository.findRandomUnclassifiedByLevel("N1", 10)).extracting(ExamQuestion::getId)
                .containsExactly(untyped.getId());
    }

    private static ExamQuestion question(String text, JlptQuestionType questionType) {
        return ExamQuestion.builder().jlptLevel(JlptLevel.N1).questionText(text).optionA("1").optionB("2").optionC("3")
                .optionD("4").correctOption("A").questionType(questionType).build();
    }

    private static String correctAnswer(ExamQuestion question) {
        return switch (question.getCorrectOption()) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            default -> question.getOptionD();
        };
    }
}
