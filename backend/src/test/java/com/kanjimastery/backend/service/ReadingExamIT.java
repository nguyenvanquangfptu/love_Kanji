package com.kanjimastery.backend.service;

import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.config.JlptBlueprintProperties;
import com.kanjimastery.backend.dto.AdminExamPassageResponse;
import com.kanjimastery.backend.dto.ExamImportRequest;
import com.kanjimastery.backend.dto.ExamImportResponse;
import com.kanjimastery.backend.dto.ExamMondaiResponse;
import com.kanjimastery.backend.dto.ExamPassageImportRequest;
import com.kanjimastery.backend.dto.ExamPassageResponse;
import com.kanjimastery.backend.dto.ExamQuestionPublicResponse;
import com.kanjimastery.backend.dto.ExamReviewResponse;
import com.kanjimastery.backend.dto.ExamSittingResponse;
import com.kanjimastery.backend.dto.JlptLevelResponse;
import com.kanjimastery.backend.dto.StartExamResponse;
import com.kanjimastery.backend.dto.StartJlptExamRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExamPassage;
import com.kanjimastery.backend.model.ExamQuestion;
import com.kanjimastery.backend.model.ExamQuestionSource;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.ExamSection;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ReviewState;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.ExamPassageRepository;
import com.kanjimastery.backend.repository.ExamQuestionRepository;
import com.kanjimastery.backend.repository.ExamSessionStore;
import com.kanjimastery.backend.repository.UserExamAttemptRepository;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Phần Đọc hiểu trên PostgreSQL + Redis thật: nhập bài đọc lẻ, duyệt cả bài, phần Đọc hiểu khoá tới khi đủ bài, ghép
 * đề lấy trọn bài đúng phần thi (phần Ngữ pháp không lấy bài đọc và ngược lại), số 問題 và số câu tiếp theo phần Ngữ
 * pháp, điểm Đọc hiểu tính riêng.
 */
class ReadingExamIT extends AbstractIntegrationTest {

    /** Cấp độ giả: Ngữ pháp có 1 câu 文章の文法; Đọc hiểu có 1 bài ngắn × 1 câu và 1 bài trung bình × 2 câu. */
    private static final JlptLevel LEVEL = JlptLevel.N2;

    @Autowired
    private ExamImportService importService;
    @Autowired
    private ExamPassageReviewService passageReviewService;
    @Autowired
    private JlptExamService jlptExamService;
    @Autowired
    private ExamService examService;
    @Autowired
    private JlptBlueprintProperties blueprints;
    @Autowired
    private ExamPassageRepository passageRepository;
    @Autowired
    private ExamQuestionRepository questionRepository;
    @Autowired
    private UserExamAttemptRepository attemptRepository;
    @Autowired
    private ExamSessionStore examSessionStore;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private final List<Long> passageIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        userId = userRepository.save(User.builder().username("reading_" + suffix)
                .email("reading_" + suffix + "@test.local").passwordHash("x").build()).getId();

        JlptBlueprintProperties.Section grammar = new JlptBlueprintProperties.Section();
        grammar.setName(ExamSection.GRAMMAR);
        grammar.setMinutes(5);
        grammar.getQuestions().put(JlptQuestionType.TEXT_GRAMMAR, 1);
        JlptBlueprintProperties.Section reading = new JlptBlueprintProperties.Section();
        reading.setName(ExamSection.READING);
        reading.setMinutes(10);
        reading.getQuestions().put(JlptQuestionType.READING_SHORT, 1);
        reading.getQuestions().put(JlptQuestionType.READING_MID, 2);
        reading.getPerPassage().put(JlptQuestionType.READING_SHORT, 1);
        reading.getPerPassage().put(JlptQuestionType.READING_MID, 2);
        reading.setNumberingAfter(ExamSection.GRAMMAR);
        reading.setFullSetOnly(true);
        JlptBlueprintProperties.Level level = new JlptBlueprintProperties.Level();
        level.setSections(List.of(grammar, reading));
        blueprints.getLevels().put(LEVEL, level);
    }

    @AfterEach
    void tearDown() {
        blueprints.getLevels().remove(LEVEL);
        attemptRepository.findAll().stream().filter(attempt -> attempt.getUserId().equals(userId))
                .forEach(attempt -> examSessionStore.cleanup(attempt.getId()));
        userRepository.deleteById(userId);
        // Xoá đoạn văn thì câu hỏi của nó đi theo (ON DELETE CASCADE).
        passageIds.forEach(passageRepository::deleteById);
        passageRepository.findAll().stream().filter(passage -> passage.getJlptLevel() == LEVEL)
                .forEach(passageRepository::delete);
    }

    @Test
    void readingSection_shouldOpenWithAFullSet_andNeverMixPassagesWithGrammar() {
        // Đoạn 文章の文法 đã duyệt, và hai "bẫy" ghi thẳng vào DB: đoạn Ngữ pháp có câu Đọc hiểu, bài đọc có câu
        // 文章の文法 - cùng dạng câu nhưng sai phần thi, ghép đề không được lấy.
        ExamPassage grammarPassage = passage(ExamSection.GRAMMAR, "朝は【1】。", JlptQuestionType.TEXT_GRAMMAR, 1);
        ExamPassage grammarTrap = passage(ExamSection.GRAMMAR, "罠の文章。", JlptQuestionType.READING_SHORT, 1);
        ExamPassage readingTrap = passage(ExamSection.READING, "罠【1】。", JlptQuestionType.TEXT_GRAMMAR, 1);

        // Nhập hai bài đọc lẻ (vào kho ở trạng thái chờ duyệt): phần Đọc hiểu còn khoá.
        Long shortId = importReading(JlptQuestionType.READING_SHORT, "短い文章。〔下線〕これ〔/下線〕は何か。", 1);
        Long midId = importReading(JlptQuestionType.READING_MID, "中くらいの文章。", 2);
        assertThat(readingSection().isLocked()).isTrue();
        assertThatThrownBy(() -> jlptExamService.startSitting(userId, request("READING")))
                .isInstanceOf(BadRequestException.class);

        // Mới duyệt bài ngắn: có câu nhưng chưa đủ một đề trọn vẹn.
        passageReviewService.changeStatus(shortId, ExamQuestionStatus.APPROVED, null);
        assertThat(readingSection().isLocked()).isTrue();
        assertThatThrownBy(() -> jlptExamService.startSitting(userId, request("READING")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Hiện chưa thể làm phần Đọc hiểu");

        passageReviewService.changeStatus(midId, ExamQuestionStatus.APPROVED, null);
        JlptLevelResponse.Section reading = readingSection();
        assertThat(reading.isLocked()).isFalse();
        assertThat(reading.getMondai()).extracting(JlptLevelResponse.Mondai::getNumber,
                        JlptLevelResponse.Mondai::getAvailable)
                .containsExactly(tuple(2, 1), tuple(3, 2));
        // Trang duyệt lọc theo phần thi, theo dạng câu và trạng thái.
        assertThat(passageReviewService.search(LEVEL.name(), ExamSection.READING, null, null, 0, 10).getContent())
                .extracting(AdminExamPassageResponse::getId)
                .containsExactlyInAnyOrder(shortId, midId, readingTrap.getId());
        assertThat(passageReviewService.search(LEVEL.name(), null, JlptQuestionType.READING_MID, null, 0, 10)
                .getContent()).extracting(AdminExamPassageResponse::getId).containsExactly(midId);
        assertThat(passageReviewService.search(LEVEL.name(), ExamSection.READING, JlptQuestionType.READING_SHORT,
                ExamQuestionStatus.DRAFT, 0, 10).getContent()).isEmpty();

        // Phần Ngữ pháp chỉ lấy đoạn 文章の文法 của phần Ngữ pháp.
        StartExamResponse grammarExam = jlptExamService.startSitting(userId, request("GRAMMAR", "READING"));
        assertThat(grammarExam.getQuestions()).extracting(ExamQuestionPublicResponse::getPassageId)
                .containsOnly(grammarPassage.getId());
        assertThat(grammarExam.getFirstQuestionNumber()).isEqualTo(1);
        examService.submit(userId, grammarExam.getAttemptId());

        // Phần Đọc hiểu: trọn hai bài đọc, 問題2-3, câu đầu tiên là câu 2 (tiếp theo 1 câu Ngữ pháp).
        StartExamResponse readingExam = jlptExamService.startNextSection(userId, grammarExam.getSittingId());
        assertThat(readingExam.getSection()).isEqualTo(ExamSection.READING);
        assertThat(readingExam.getQuestions())
                .extracting(ExamQuestionPublicResponse::getPassageId, ExamQuestionPublicResponse::getBlankNo,
                        ExamQuestionPublicResponse::getQuestionType)
                .containsExactly(tuple(shortId, 1, JlptQuestionType.READING_SHORT),
                        tuple(midId, 1, JlptQuestionType.READING_MID), tuple(midId, 2, JlptQuestionType.READING_MID));
        assertThat(readingExam.getMondai()).extracting(ExamMondaiResponse::getNumber).containsExactly(2, 3);
        assertThat(readingExam.getFirstQuestionNumber()).isEqualTo(2);
        assertThat(readingExam.getPassages()).extracting(ExamPassageResponse::section).containsOnly(ExamSection.READING);
        assertThat(readingExam.getPassages()).extracting(ExamPassageResponse::id)
                .doesNotContain(grammarTrap.getId(), readingTrap.getId());

        examService.submit(userId, readingExam.getAttemptId());
        ExamReviewResponse review = examService.getReview(userId, readingExam.getAttemptId());
        assertThat(review.getMondai()).extracting(ExamReviewResponse.MondaiScore::getNumber).containsExactly(2, 3);
        assertThat(review.getFirstQuestionNumber()).isEqualTo(2);

        // Bỏ trống hết: 0 điểm mỗi môn, tính riêng từng môn.
        ExamSittingResponse sitting = jlptExamService.getSitting(userId, grammarExam.getSittingId());
        assertThat(sitting.getEstimatedScore()).isZero();
        assertThat(sitting.getReadingEstimatedScore()).isZero();
    }

    @Test
    void importAgain_shouldNotDuplicateALoosePassage_andTheColumnShouldRefuseAPassageWithoutSection() {
        Long first = importReading(JlptQuestionType.READING_SHORT, "同じ文章。", 1);
        ExamImportResponse again = importService.importPassage(JlptQuestionType.READING_SHORT,
                looseRequest("同じ文章。", 1), false);
        assertThat(again.passages()).isZero();
        assertThat(again.alreadyImported()).isEqualTo(1);
        assertThat(passageRepository.findAll()).filteredOn(passage -> passage.getJlptLevel() == LEVEL)
                .extracting(ExamPassage::getId).containsExactly(first);

        // Chỗ tạo đoạn văn nào quên ghi phần thi thì DB từ chối (NOT NULL, không có DEFAULT); Từ vựng không có đoạn văn.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO exam_passages (jlpt_level, content) VALUES ('N2', 'x')"))
                .hasMessageContaining("section");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO exam_passages (jlpt_level, content, section) VALUES ('N2', 'x', 'VOCABULARY')"))
                .hasMessageContaining("ck_exam_passages_section");
    }

    private JlptLevelResponse.Section readingSection() {
        return jlptExamService.levels(null).stream().filter(level -> level.getJlptLevel() == LEVEL).findFirst()
                .orElseThrow().getSections().get(1);
    }

    /** Nhập một bài đọc lẻ có {@code questions} câu hỏi; trả về id bài vừa nhập. */
    private Long importReading(JlptQuestionType type, String content, int questions) {
        ExamImportResponse report = importService.importPassage(type, looseRequest(content, questions), false);
        assertThat(report.errors()).isEmpty();
        assertThat(report.imported()).isTrue();
        ExamPassage stored = passageRepository.findAll().stream()
                .filter(passage -> passage.getJlptLevel() == LEVEL && content.equals(passage.getContent()))
                .findFirst().orElseThrow();
        assertThat(stored.getSection()).isEqualTo(ExamSection.READING);
        assertThat(stored.getSource()).isEqualTo(ExamQuestionSource.IMPORTED);
        passageIds.add(stored.getId());
        return stored.getId();
    }

    private static ExamPassageImportRequest looseRequest(String content, int questions) {
        return new ExamPassageImportRequest(LEVEL.name(), null, null, content, IntStream.rangeClosed(1, questions)
                .mapToObj(number -> new ExamImportRequest.Item(number, "問" + number + "：正しいものはどれか。", null, null,
                        null, null, List.of("一", "二", "三", "四"), 1, null, null, null, null, "Giải thích"))
                .toList());
    }

    /** Đoạn văn đã duyệt ghi thẳng vào kho, có một câu dạng {@code type} đã duyệt. */
    private ExamPassage passage(ExamSection section, String content, JlptQuestionType type, int blankNo) {
        ExamPassage passage = passageRepository.save(ExamPassage.builder().jlptLevel(LEVEL).section(section)
                .content(content).review(new ReviewState(ExamQuestionStatus.APPROVED)).build());
        passageIds.add(passage.getId());
        questionRepository.save(ExamQuestion.builder().jlptLevel(LEVEL).questionText("【" + blankNo + "】")
                .optionA("一").optionB("二").optionC("三").optionD("四").correctOption("A").questionType(type)
                .review(new ReviewState(ExamQuestionStatus.APPROVED)).passageId(passage.getId()).blankNo(blankNo)
                .build());
        return passage;
    }

    private static StartJlptExamRequest request(String... sections) {
        StartJlptExamRequest request = new StartJlptExamRequest();
        request.setJlptLevel(LEVEL.name());
        request.setSections(List.of(sections));
        return request;
    }
}
