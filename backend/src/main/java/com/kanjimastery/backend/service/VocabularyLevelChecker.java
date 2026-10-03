package com.kanjimastery.backend.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.kanjimastery.backend.repository.KanjiRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kiểm tra một câu tiếng Nhật có dùng từ vượt cấp độ không: tách từ bằng Kuromoji, lấy các từ nội dung (danh từ, động
 * từ, tính từ, phó từ - bỏ trợ từ, số, tên riêng, chữ Latinh như người nói A/B) ở dạng từ điển rồi tra trong kho từ
 * vựng theo cách viết hoặc cách đọc.
 * <p>
 * Kho từ chỉ gồm từ của các bài đã nhập, cấp độ ghi theo bài, nên nhiều từ cơ bản không có hoặc ghi cấp cao hơn thật
 * (thử trên 102 câu soạn tay: 23 câu có ít nhất một từ như vậy). Vì thế chỉ coi là đáng ngờ khi có nhiều từ vượt cấp;
 * còn lại chỉ ghi chú cho người duyệt.
 */
@Component
@RequiredArgsConstructor
public class VocabularyLevelChecker {

    /** Từ vượt cấp độ chừng này trở lên thì đáng ngờ. */
    static final int SUSPICIOUS_ABOVE_LEVEL = 3;
    private static final Set<String> SKIPPED_NOUNS = Set.of("数", "代名詞", "非自立", "接尾", "固有名詞", "特殊");
    /** Từ cơ bản hay không có trong danh sách từ của bài nào. */
    private static final Set<String> BASIC_WORDS = Set.of("する", "ある", "いる", "なる", "できる", "来る", "行く", "言う",
            "ない");
    private static final Pattern LATIN = Pattern.compile("[A-Za-zＡ-Ｚａ-ｚ]+");

    private final KanjiRepository kanjiRepository;

    /**
     * @param aboveLevel từ có trong kho nhưng chỉ ở cấp độ cao hơn
     * @param unknown    từ không có trong kho
     */
    public record Result(List<String> aboveLevel, List<String> unknown) {

        public boolean suspicious() {
            return aboveLevel.size() >= SUSPICIOUS_ABOVE_LEVEL;
        }

        /** Mô tả cho người duyệt; rỗng nếu không có gì đáng nói. */
        public String describe() {
            StringBuilder text = new StringBuilder();
            if (!aboveLevel.isEmpty()) {
                text.append("Từ vượt cấp độ: ").append(String.join(", ", aboveLevel)).append(". ");
            }
            if (!unknown.isEmpty()) {
                text.append("Từ không có trong kho: ").append(String.join(", ", unknown)).append(". ");
            }
            return text.toString().strip();
        }
    }

    /** Mở một lượt kiểm tra cho cấp độ: nạp kho từ và từ điển Kuromoji một lần, dùng cho nhiều câu. */
    public Session open(String level) {
        Map<String, Integer> easiest = new HashMap<>();
        for (KanjiRepository.WordLevel word : kanjiRepository.findAllWordLevels()) {
            int rank = rank(word.getJlptLevel());
            easiest.merge(word.getCharacter(), rank, Math::max);
            if (StringUtils.hasText(word.getReading())) {
                // み(る): phần trong ngoặc là okurigana.
                easiest.merge(word.getReading().replaceAll("[()（）]", ""), rank, Math::max);
            }
        }
        return new Session(rank(level), easiest);
    }

    /** N5 → 5 ... N1 → 1: số càng lớn càng dễ; cấp độ lạ coi như khó nhất. */
    static int rank(String level) {
        return level != null && level.matches("N[1-5]") ? level.charAt(1) - '0' : 0;
    }

    /** Một lượt kiểm tra; không dùng chung giữa các luồng. */
    public static final class Session {

        private final int level;
        private final Map<String, Integer> easiest;
        private final Tokenizer tokenizer = new Tokenizer();

        Session(int level, Map<String, Integer> easiest) {
            this.level = level;
            this.easiest = easiest;
        }

        public Result check(String text) {
            Set<String> above = new LinkedHashSet<>();
            Set<String> unknown = new LinkedHashSet<>();
            for (Token token : tokenizer.tokenize(text)) {
                if (!isContentWord(token) || BASIC_WORDS.contains(token.getBaseForm())) {
                    continue;
                }
                String base = "*".equals(token.getBaseForm()) ? token.getSurface() : token.getBaseForm();
                Integer rank = lookup(base, token);
                if (rank == null) {
                    unknown.add(base);
                } else if (rank < level) {
                    above.add(base);
                }
            }
            return new Result(List.copyOf(above), List.copyOf(unknown));
        }

        /** Cấp độ dễ nhất có từ này; danh từ サ変 (勉強) cũng tra dạng 〜する. */
        private Integer lookup(String base, Token token) {
            Integer rank = easiest.get(base);
            if (rank == null && "サ変接続".equals(token.getPartOfSpeechLevel2())) {
                rank = easiest.get(base + "する");
            }
            if (rank == null) {
                rank = easiest.get(token.getSurface());
            }
            return rank;
        }

        private static boolean isContentWord(Token token) {
            if (LATIN.matcher(token.getSurface()).matches()) {
                return false;
            }
            String kind = token.getPartOfSpeechLevel2();
            return switch (token.getPartOfSpeechLevel1()) {
                case "名詞" -> !SKIPPED_NOUNS.contains(kind);
                case "動詞", "形容詞" -> "自立".equals(kind);
                case "副詞" -> true;
                default -> false;
            };
        }
    }
}
