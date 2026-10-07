package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.GrammarImportResult;
import com.kanjimastery.backend.dto.GrammarPointRequest;
import com.kanjimastery.backend.dto.GrammarPointResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.model.ExamQuestionStatus;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Danh sách điểm ngữ pháp theo cấp độ: thêm, sửa, xoá, nhập và xuất CSV. Nhập lại cùng một file không tạo trùng -
 * mỗi điểm được nhận ra bằng cấp độ + mẫu, nội dung khác thì cập nhật.
 */
@Service
@RequiredArgsConstructor
public class GrammarPointService {

    /** Các cột của file CSV, theo thứ tự; hai cột cuối có thể bỏ. Dòng đầu là tiêu đề thì bỏ qua. */
    static final List<String> CSV_COLUMNS = List.of("cap_do", "bai", "mau", "nghia", "cach_noi", "giai_thich");
    private static final Pattern LEVEL = Pattern.compile("N[1-5]");

    private final GrammarPointRepository repository;

    /** Theo cấp độ (null = mọi cấp độ), kèm số câu thi đã duyệt và đang chờ duyệt của từng điểm. */
    @Transactional(readOnly = true)
    public List<GrammarPointResponse> list(String level) {
        List<GrammarPoint> points = StringUtils.hasText(level)
                ? repository.findByJlptLevelOrderByLessonAscIdAsc(level.toUpperCase())
                : repository.findAllByOrderByJlptLevelDescLessonAscIdAsc();
        Map<Long, Map<ExamQuestionStatus, Long>> counts = new HashMap<>();
        if (!points.isEmpty()) {
            for (GrammarPointRepository.QuestionCount count : repository.countQuestionsByStatus(
                    points.stream().map(GrammarPoint::getId).toList())) {
                counts.computeIfAbsent(count.getGrammarPointId(), id -> new HashMap<>())
                        .put(count.getStatus(), count.getCount());
            }
        }
        return points.stream().map(point -> toResponse(point, counts.getOrDefault(point.getId(), Map.of()))).toList();
    }

    @Transactional
    public GrammarPointResponse create(GrammarPointRequest request) {
        String level = request.getJlptLevel().toUpperCase();
        String pattern = normalizePattern(request.getPattern());
        if (repository.findByJlptLevelAndPattern(level, pattern).isPresent()) {
            throw new BadRequestException("Đã có mẫu " + pattern + " ở cấp độ " + level);
        }
        GrammarPoint point = new GrammarPoint();
        apply(point, level, request.getLesson(), pattern, request.getConnection(), request.getMeaningVi(),
                request.getExplanationVi());
        return toResponse(repository.save(point), Map.of());
    }

    @Transactional
    public GrammarPointResponse update(Long id, GrammarPointRequest request) {
        GrammarPoint point = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm ngữ pháp: " + id));
        String level = request.getJlptLevel().toUpperCase();
        String pattern = normalizePattern(request.getPattern());
        repository.findByJlptLevelAndPattern(level, pattern)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BadRequestException("Đã có mẫu " + pattern + " ở cấp độ " + level);
                });
        apply(point, level, request.getLesson(), pattern, request.getConnection(), request.getMeaningVi(),
                request.getExplanationVi());
        return list(level).stream().filter(response -> response.getId().equals(id)).findFirst().orElseThrow();
    }

    /** Xoá cả liên kết với câu thi (câu thi vẫn giữ). */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy điểm ngữ pháp: " + id);
        }
        repository.deleteById(id);
    }

    @Transactional
    public GrammarImportResult importCsv(String csv) {
        List<List<String>> rows = Csv.parse(csv == null ? "" : csv);
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            List<String> row = rows.get(index).stream().map(String::strip).toList();
            String level = row.get(0).toUpperCase();
            if (index == 0 && !LEVEL.matcher(level).matches()) {
                continue;
            }
            String error = validate(row, level);
            if (error != null) {
                errors.add("Dòng " + (index + 1) + ": " + error);
                continue;
            }
            String lesson = column(row, 1);
            String pattern = normalizePattern(row.get(2));
            String meaning = row.get(3);
            String connection = column(row, 4);
            String explanation = column(row, 5);

            GrammarPoint point = repository.findByJlptLevelAndPattern(level, pattern).orElse(null);
            if (point == null) {
                point = new GrammarPoint();
                apply(point, level, lesson, pattern, connection, meaning, explanation);
                repository.save(point);
                created++;
            } else if (Objects.equals(point.getLesson(), lesson) && Objects.equals(point.getConnection(), connection)
                    && point.getMeaningVi().equals(meaning) && Objects.equals(point.getExplanationVi(), explanation)) {
                unchanged++;
            } else {
                apply(point, level, lesson, pattern, connection, meaning, explanation);
                updated++;
            }
        }
        return new GrammarImportResult(created, updated, unchanged, errors);
    }

    /** CSV cùng định dạng với lúc nhập, để sửa ngoài app rồi nhập lại. */
    @Transactional(readOnly = true)
    public String exportCsv(String level) {
        List<GrammarPoint> points = StringUtils.hasText(level)
                ? repository.findByJlptLevelOrderByLessonAscIdAsc(level.toUpperCase())
                : repository.findAllByOrderByJlptLevelDescLessonAscIdAsc();
        StringBuilder csv = new StringBuilder(Csv.line(CSV_COLUMNS)).append("\r\n");
        for (GrammarPoint point : points) {
            csv.append(Csv.line(Arrays.asList(point.getJlptLevel(), point.getLesson(), point.getPattern(),
                    point.getMeaningVi(), point.getConnection(), point.getExplanationVi()))).append("\r\n");
        }
        return csv.toString();
    }

    private static String validate(List<String> row, String level) {
        if (row.size() < 4) {
            return "cần ít nhất 4 cột (cấp độ, bài, mẫu, nghĩa)";
        }
        if (!LEVEL.matcher(level).matches()) {
            return "cấp độ \"" + row.get(0) + "\" không hợp lệ (N1-N5)";
        }
        if (row.get(2).isEmpty()) {
            return "thiếu mẫu ngữ pháp";
        }
        if (row.get(3).isEmpty()) {
            return "thiếu nghĩa tiếng Việt";
        }
        if (row.get(2).length() > 100 || row.get(1).length() > 20 || column(row, 4) != null && column(row, 4).length() > 200) {
            return "mẫu tối đa 100 ký tự, bài 20, cách nối 200";
        }
        return null;
    }

    private static String column(List<String> row, int index) {
        return index < row.size() && !row.get(index).isEmpty() ? row.get(index) : null;
    }

    /** Dấu sóng gõ kiểu nào (~ ～ 〜) cũng thống nhất thành 〜, để nhập lại không tạo bản trùng. */
    static String normalizePattern(String pattern) {
        return pattern.strip().replace('~', '〜').replace('～', '〜');
    }

    private static void apply(GrammarPoint point, String level, String lesson, String pattern, String connection,
                              String meaning, String explanation) {
        point.setJlptLevel(level);
        point.setLesson(StringUtils.hasText(lesson) ? lesson.strip() : null);
        point.setPattern(pattern);
        point.setConnection(StringUtils.hasText(connection) ? connection.strip() : null);
        point.setMeaningVi(meaning.strip());
        point.setExplanationVi(StringUtils.hasText(explanation) ? explanation.strip() : null);
    }

    private static GrammarPointResponse toResponse(GrammarPoint point, Map<ExamQuestionStatus, Long> counts) {
        return GrammarPointResponse.builder()
                .id(point.getId())
                .jlptLevel(point.getJlptLevel())
                .lesson(point.getLesson())
                .pattern(point.getPattern())
                .connection(point.getConnection())
                .meaningVi(point.getMeaningVi())
                .explanationVi(point.getExplanationVi())
                .approvedQuestions(counts.getOrDefault(ExamQuestionStatus.APPROVED, 0L))
                .draftQuestions(counts.getOrDefault(ExamQuestionStatus.DRAFT, 0L))
                .build();
    }
}
