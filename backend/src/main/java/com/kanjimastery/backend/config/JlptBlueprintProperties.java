package com.kanjimastery.backend.config;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ExamSection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Cấu trúc đề JLPT theo cấp độ - xem jlpt-blueprints.yml. */
@Component
@ConfigurationProperties(prefix = "app.jlpt")
@Getter
@Setter
public class JlptBlueprintProperties {

    /** Theo cấp độ (N5, N4...). */
    private Map<JlptLevel, Level> levels = new LinkedHashMap<>();

    /** Một phần thi của một cấp độ; rỗng nếu cấp độ hoặc phần đó không có trong cấu trúc đề. */
    public Optional<Section> section(JlptLevel level, ExamSection name) {
        Level blueprint = levels.get(level);
        return blueprint == null
                ? Optional.empty()
                : blueprint.getSections().stream().filter(section -> section.getName() == name).findFirst();
    }

    /** Số 問題 đầu tiên của phần thi: 1, hoặc tiếp theo phần {@link Section#getNumberingAfter()}. */
    public int firstMondaiNumber(JlptLevel level, ExamSection name) {
        return numberedBefore(level, name, section -> section.getQuestions().size(), EnumSet.of(name)) + 1;
    }

    /** Số câu đầu tiên của phần thi trong đề thật: 1, hoặc tiếp theo phần {@link Section#getNumberingAfter()}. */
    public int firstQuestionNumber(JlptLevel level, ExamSection name) {
        return numberedBefore(level, name, Section::plannedQuestions, EnumSet.of(name)) + 1;
    }

    /** Tổng {@code size} của các phần đánh số liền trước phần {@code name}; {@code seen} chặn cấu hình vòng tròn. */
    private int numberedBefore(JlptLevel level, ExamSection name, ToIntFunction<Section> size, Set<ExamSection> seen) {
        Optional<Section> previous = section(level, name)
                .map(Section::getNumberingAfter)
                .filter(seen::add)
                .flatMap(after -> section(level, after));
        return previous
                .map(section -> numberedBefore(level, section.getName(), size, seen) + size.applyAsInt(section))
                .orElse(0);
    }

    @Getter
    @Setter
    public static class Level {
        /** Các phần theo thứ tự làm bài. */
        private List<Section> sections = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Section {
        private ExamSection name;
        private int minutes;
        /** Dạng câu -> số câu, theo thứ tự 問題1, 問題2... */
        private Map<JlptQuestionType, Integer> questions = new LinkedHashMap<>();
        /**
         * Dạng câu lấy theo bài -> số câu của mỗi bài (vd. bài đọc trung bình có 3 câu); ghép đề chỉ lấy bài có đúng
         * chừng ấy câu đã duyệt. Dạng không có ở đây mà dùng đoạn văn (文章の文法) thì lấy các đoạn sao cho đủ số câu.
         */
        private Map<JlptQuestionType, Integer> perPassage = new LinkedHashMap<>();
        /**
         * Phần có số 問題 và số câu đánh tiếp theo phần này, như đề thật (Đọc hiểu N3 tiếp theo Ngữ pháp: 問題4, câu
         * 24...); null = đánh từ 1.
         */
        private ExamSection numberingAfter;
        /** Chỉ làm được khi đủ mọi câu của đề thật - không ghép đề thiếu câu như các phần khác. */
        private boolean fullSetOnly;

        /** Các dạng câu theo thứ tự 問題: dạng thứ i là 問題(i+1). */
        public List<JlptQuestionType> types() {
            return List.copyOf(questions.keySet());
        }

        /** Số câu của phần này trong đề thật. */
        public int plannedQuestions() {
            return questions.values().stream().mapToInt(Integer::intValue).sum();
        }

        /** Số câu của mỗi bài với dạng lấy theo bài; rỗng với dạng khác. */
        public Optional<Integer> questionsPerPassage(JlptQuestionType type) {
            return Optional.ofNullable(perPassage.get(type));
        }
    }
}
