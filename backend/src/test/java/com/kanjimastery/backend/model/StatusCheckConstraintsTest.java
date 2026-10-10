package com.kanjimastery.backend.model;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Các ràng buộc CHECK (V29, sửa ở các migration sau) phải liệt kê đúng các giá trị của enum Java: thêm giá trị vào enum
 * mà quên migration thì DB từ chối ghi, test này đỏ trước. Định nghĩa ở migration mới nhất được tính.
 */
class StatusCheckConstraintsTest {

    private static final Pattern IN_LIST = Pattern.compile("CONSTRAINT (ck_\\w+) CHECK \\(\\w+ IN \\(([^)]*)\\)\\)");

    /** Tên ràng buộc -> enum mà cột đó lưu. */
    private static final Map<String, Class<? extends Enum<?>>> COLUMNS = Map.ofEntries(
            Map.entry("ck_kanji_jlpt_level", JlptLevel.class),
            Map.entry("ck_exam_questions_jlpt_level", JlptLevel.class),
            Map.entry("ck_exam_passages_jlpt_level", JlptLevel.class),
            Map.entry("ck_grammar_points_jlpt_level", JlptLevel.class),
            Map.entry("ck_user_exam_attempts_jlpt_level", JlptLevel.class),
            Map.entry("ck_exam_sittings_jlpt_level", JlptLevel.class),
            Map.entry("ck_user_learning_profiles_target_level", JlptLevel.class),
            Map.entry("ck_exam_questions_status", ExamQuestionStatus.class),
            Map.entry("ck_exam_questions_source", ExamQuestionSource.class),
            Map.entry("ck_exam_questions_flag", ExamQuestionFlag.class),
            Map.entry("ck_exam_questions_question_type", JlptQuestionType.class),
            Map.entry("ck_exam_passages_status", ExamQuestionStatus.class),
            Map.entry("ck_exam_passages_source", ExamQuestionSource.class),
            Map.entry("ck_exam_passages_flag", ExamQuestionFlag.class),
            Map.entry("ck_user_exam_attempts_status", ExamAttemptStatus.class),
            Map.entry("ck_user_exam_attempts_section", ExamSection.class),
            Map.entry("ck_exam_sittings_status", ExamSittingStatus.class),
            Map.entry("ck_exam_question_reports_reason", QuestionReportReason.class),
            Map.entry("ck_exam_question_reports_status", QuestionReportStatus.class),
            Map.entry("ck_review_logs_source", ReviewSource.class),
            Map.entry("ck_review_logs_direction", QuizDirection.class),
            Map.entry("ck_review_logs_state_before", CardState.class),
            Map.entry("ck_user_learning_profiles_scheduler", SchedulerType.class));

    @Test
    void everyEnumColumnCheck_shouldListExactlyTheEnumsValues() throws IOException {
        Map<String, Set<String>> checks = checks();

        assertThat(checks.keySet()).containsAll(COLUMNS.keySet());
        COLUMNS.forEach((constraint, type) -> assertThat(checks.get(constraint)).as(constraint)
                .containsExactlyInAnyOrderElementsOf(names(type)));
        // Câu thi chỉ có các kỹ năng thi, không có hướng hỏi riêng của phần học (gõ cách đọc).
        assertThat(checks.get("ck_exam_questions_skill")).containsExactlyInAnyOrderElementsOf(
                QuizDirection.examSkills().stream().map(Enum::name).toList());
    }

    @Test
    void sittingSectionsCheck_shouldAllowExactlyTheSections() throws IOException {
        Matcher matcher = Pattern.compile("ck_exam_sittings_sections CHECK \\(sections ~ '\\^\\(([A-Z_|]+)\\)")
                .matcher(migration());

        assertThat(matcher.find()).isTrue();
        assertThat(Set.of(matcher.group(1).split("\\|"))).containsExactlyInAnyOrderElementsOf(names(ExamSection.class));
    }

    private static Map<String, Set<String>> checks() throws IOException {
        Map<String, Set<String>> checks = new HashMap<>();
        Matcher matcher = IN_LIST.matcher(migration());
        while (matcher.find()) {
            checks.put(matcher.group(1), Arrays.stream(matcher.group(2).split(","))
                    .map(value -> value.strip().replace("'", ""))
                    .collect(Collectors.toSet()));
        }
        return checks;
    }

    private static Set<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
    }

    /** Mọi migration từ V29, theo thứ tự phiên bản - ràng buộc định nghĩa lại ở migration sau ghi đè bản trước. */
    private static String migration() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
        StringBuilder sql = new StringBuilder();
        for (Resource file : Arrays.stream(files)
                .filter(file -> version(file) >= 29)
                .sorted(Comparator.comparingInt(StatusCheckConstraintsTest::version))
                .toList()) {
            sql.append(file.getContentAsString(StandardCharsets.UTF_8)).append(System.lineSeparator());
        }
        return sql.toString();
    }

    private static int version(Resource file) {
        String name = file.getFilename();
        return Integer.parseInt(name.substring(1, name.indexOf("__")));
    }
}
