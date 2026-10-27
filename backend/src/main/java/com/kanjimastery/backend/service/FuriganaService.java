package com.kanjimastery.backend.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.kanjimastery.backend.dto.RubySegment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chia câu tiếng Nhật thành các đoạn kèm furigana, theo cách đọc mà Kuromoji (từ điển IPADIC) đoán cho từng từ.
 * <p>
 * Từ được hỏi (phần gạch chân) được tách riêng ra trước khi phân tích phần còn lại, nên gạch chân không cắt ngang một
 * từ, và cách đọc của nó lấy từ kho từ chứ không để Kuromoji đoán (今日 きょう/こんにち, 上手 じょうず/うわて).
 * Furigana chỉ đặt trên phần chữ Hán: 食べる → 食(た)べる.
 * <p>
 * Tokenizer của Kuromoji dùng chung được giữa nhiều luồng, nên service giữ một bản cho cả ứng dụng (nạp từ điển mất
 * khoảng 0,2 giây và vài chục MB) thay vì nạp lại mỗi request.
 */
@Service
public class FuriganaService {

    /** Cách đọc dạng み(る): phần trong ngoặc là okurigana. */
    private static final Pattern OKURIGANA_READING = Pattern.compile("^(.*?)[(（](.+?)[)）]$");
    /** Từ IPADIC đọc khác cách người học gặp trong sách: 日本 đứng riêng bị đọc にっぽん (日本語 thì vẫn にほんご). */
    private static final Map<String, String> READING_FIXES = Map.of("日本", "にほん");

    private final Tokenizer tokenizer = new Tokenizer();

    /** Câu không có từ nào được hỏi: furigana cho mọi từ có chữ Hán. */
    public List<RubySegment> annotate(String sentence) {
        return annotate(sentence, null, null, false);
    }

    /**
     * @param target            từ được hỏi, nằm nguyên văn trong câu; null nếu không có
     * @param targetReading     cách đọc của {@code target} trong kho từ (có thể dạng み(る)); null thì để Kuromoji đoán
     * @param hideTargetReading câu hỏi chính là hỏi cách đọc của {@code target}: không đưa ra furigana của nó, ở mọi lần
     *                          xuất hiện trong câu
     * @return null nếu không có câu
     */
    public List<RubySegment> annotate(String sentence, String target, String targetReading, boolean hideTargetReading) {
        if (!StringUtils.hasText(sentence)) {
            return null;
        }
        Segments segments = new Segments();
        int from = 0;
        if (StringUtils.hasLength(target)) {
            boolean first = true;
            for (int at = sentence.indexOf(target); at >= 0; at = sentence.indexOf(target, from)) {
                addTokens(segments, sentence.substring(from, at), false);
                addTarget(segments, target, targetReading, hideTargetReading, first);
                first = false;
                from = at + target.length();
            }
        }
        addTokens(segments, sentence.substring(from), false);
        return segments.list;
    }

    private void addTarget(Segments segments, String target, String reading, boolean hideReading, boolean underline) {
        if (hideReading) {
            segments.add(target, null, underline);
        } else if (StringUtils.hasText(reading)) {
            for (RubySegment part : align(target, kanaReading(target, reading))) {
                segments.add(part.text(), part.ruby(), underline);
            }
        } else {
            addTokens(segments, target, underline);
        }
    }

    /** Phân tích {@code text} bằng Kuromoji; chỗ nào Kuromoji bỏ qua (nếu có) vẫn giữ nguyên văn. */
    private void addTokens(Segments segments, String text, boolean underline) {
        if (text.isEmpty()) {
            return;
        }
        int covered = 0;
        for (Token token : tokenizer.tokenize(text)) {
            int start = token.getPosition();
            if (start > covered) {
                segments.add(text.substring(covered, start), null, underline);
            }
            String surface = token.getSurface();
            for (RubySegment part : align(surface, readingOf(token))) {
                segments.add(part.text(), part.ruby(), underline);
            }
            covered = Math.max(covered, start + surface.length());
        }
        if (covered < text.length()) {
            segments.add(text.substring(covered), null, underline);
        }
    }

    /** Cách đọc hiragana Kuromoji đoán; null với từ nó không biết. */
    private static String readingOf(Token token) {
        String fixed = READING_FIXES.get(token.getSurface());
        if (fixed != null) {
            return fixed;
        }
        String reading = token.getReading();
        return reading == null || "*".equals(reading) ? null : RomajiConverter.katakanaToHiragana(reading);
    }

    /** Cách đọc trong kho từ ứng với đúng phần {@code word} có trong câu: 見 + み(る) → み, 見る + み(る) → みる. */
    static String kanaReading(String word, String reading) {
        String kana = RomajiConverter.katakanaToHiragana(reading.strip());
        Matcher okurigana = OKURIGANA_READING.matcher(kana);
        if (!okurigana.matches()) {
            return kana;
        }
        return word.endsWith(okurigana.group(2)) ? okurigana.group(1) + okurigana.group(2) : okurigana.group(1);
    }

    /**
     * Đặt cách đọc lên từng khúc chữ Hán của {@code surface}, phần kana giữ nguyên: 取り消す + とりけす →
     * 取(と)り消(け)す. Không khớp được (cách đọc lệch với phần kana) thì đặt cả cách đọc lên cả từ.
     */
    static List<RubySegment> align(String surface, String reading) {
        List<String> runs = runs(surface);
        if (!StringUtils.hasText(reading) || runs.stream().noneMatch(FuriganaService::isKanjiRun)) {
            return List.of(new RubySegment(surface, null, false));
        }
        StringBuilder regex = new StringBuilder("^");
        for (String run : runs) {
            regex.append(isKanjiRun(run) ? "(.+?)" : Pattern.quote(RomajiConverter.katakanaToHiragana(run)));
        }
        Matcher matcher = Pattern.compile(regex.append('$').toString()).matcher(reading);
        if (!matcher.matches()) {
            return List.of(new RubySegment(surface, reading, false));
        }
        List<RubySegment> parts = new ArrayList<>(runs.size());
        int group = 1;
        for (String run : runs) {
            parts.add(new RubySegment(run, isKanjiRun(run) ? matcher.group(group++) : null, false));
        }
        return parts;
    }

    /** Chia chữ thành các khúc liền nhau: toàn chữ Hán, hoặc toàn chữ khác. */
    private static List<String> runs(String text) {
        List<String> runs = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        Boolean kanjiRun = null;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            boolean kanji = isKanjiLike(codePoint);
            if (kanjiRun != null && kanji != kanjiRun) {
                runs.add(run.toString());
                run.setLength(0);
            }
            kanjiRun = kanji;
            run.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        if (!run.isEmpty()) {
            runs.add(run.toString());
        }
        return runs;
    }

    private static boolean isKanjiRun(String run) {
        return isKanjiLike(run.codePointAt(0));
    }

    /** Chữ Hán, kể cả 々; ヶ ヵ (一ヶ月) đọc như một chữ Hán nên đi chung khúc. */
    private static boolean isKanjiLike(int codePoint) {
        return QuizDistractorGenerator.isKanji(codePoint) || codePoint == 'ヶ' || codePoint == 'ヵ';
    }

    /** Danh sách đoạn, gộp các đoạn liền nhau không có furigana để gửi đi gọn hơn. */
    private static final class Segments {

        private final List<RubySegment> list = new ArrayList<>();

        void add(String text, String ruby, boolean target) {
            if (text.isEmpty()) {
                return;
            }
            if (ruby == null && !list.isEmpty()) {
                RubySegment last = list.get(list.size() - 1);
                if (last.ruby() == null && last.target() == target) {
                    list.set(list.size() - 1, new RubySegment(last.text() + text, null, target));
                    return;
                }
            }
            list.add(new RubySegment(text, ruby, target));
        }
    }
}
