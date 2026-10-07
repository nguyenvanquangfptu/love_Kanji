package com.kanjimastery.backend.config;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.JlptQuestionType;
import com.kanjimastery.backend.model.ExamSection;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        /** Các dạng câu theo thứ tự 問題: dạng thứ i là 問題(i+1). */
        public List<JlptQuestionType> types() {
            return List.copyOf(questions.keySet());
        }

        /** Số câu của phần này trong đề thật. */
        public int plannedQuestions() {
            return questions.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
