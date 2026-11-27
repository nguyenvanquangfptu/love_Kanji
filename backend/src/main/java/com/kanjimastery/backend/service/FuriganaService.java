package com.kanjimastery.backend.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.kanjimastery.backend.dto.RubySegment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    /** Một từ trong kho tách ra tối đa chừng này từ của Kuromoji (落ち + 着く, 一生 + 懸命). */
    private static final int MAX_TOKENS_PER_WORD = 4;

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

    /**
     * Vị trí của một từ trong câu.
     *
     * @param start       chữ đầu tiên của từ
     * @param end         ngay sau phần là chính từ đó (怒っていた: hết 怒っ)
     * @param extendedEnd ngay sau cả phần chia đi kèm - trợ động từ, て/で, いる/しまう, する của danh từ する (怒っていた:
     *                    hết た) - để che cả cụm khi hỏi điền từ
     */
    public record Span(int start, int end, int extendedEnd) {

        public String core(String sentence) {
            return sentence.substring(start, end);
        }

        public String extended(String sentence) {
            return sentence.substring(start, extendedEnd);
        }
    }

    /**
     * Tìm từ {@code word} (dạng từ điển, vd. 怒る, 連絡する, 楽な) trong câu, kể cả khi đã chia (怒って, 連絡した, 楽に):
     * so dạng từ điển Kuromoji đoán cho từng từ trong câu, một từ trong kho có thể là vài từ của Kuromoji (取り + 消す).
     * Không thấy thì tìm nguyên văn.
     */
    public Optional<Span> locate(String sentence, String word) {
        if (!StringUtils.hasText(sentence) || !StringUtils.hasText(word)) {
            return Optional.empty();
        }
        List<String> spellings = new ArrayList<>(List.of(word));
        if (word.endsWith("する") && word.length() > 2) {
            spellings.add(word.substring(0, word.length() - 2));
        }
        if (word.endsWith("な") && word.length() > 1 && isKanjiLike(word.codePointBefore(word.length() - 1))) {
            spellings.add(word.substring(0, word.length() - 1));
        }
        List<Token> tokens = tokenizer.tokenize(sentence);
        for (int i = 0; i < tokens.size(); i++) {
            StringBuilder surfaces = new StringBuilder();
            for (int j = i; j < Math.min(tokens.size(), i + MAX_TOKENS_PER_WORD); j++) {
                Token last = tokens.get(j);
                String base = "*".equals(last.getBaseForm()) || last.getBaseForm() == null
                        ? last.getSurface() : last.getBaseForm();
                String asWord = surfaces + base;
                surfaces.append(last.getSurface());
                if (spellings.contains(asWord)) {
                    int start = tokens.get(i).getPosition();
                    int end = last.getPosition() + last.getSurface().length();
                    return Optional.of(new Span(start, end, extend(tokens, j, end, !asWord.equals(word))));
                }
            }
        }
        for (String spelling : spellings) {
            int at = sentence.indexOf(spelling);
            if (at >= 0) {
                return Optional.of(new Span(at, at + spelling.length(), at + spelling.length()));
            }
        }
        return Optional.empty();
    }

    /** Kéo dài sau từ thứ {@code last}: trợ động từ (た, ない, ます), て/で, động từ phụ (いる, しまう), する của danh từ する. */
    private static int extend(List<Token> tokens, int last, int end, boolean suruNoun) {
        for (int k = last + 1; k < tokens.size(); k++) {
            Token token = tokens.get(k);
            String pos = token.getPartOfSpeechLevel1();
            String pos2 = token.getPartOfSpeechLevel2();
            boolean attached = "助動詞".equals(pos)
                    || "助詞".equals(pos) && "接続助詞".equals(pos2) && ("て".equals(token.getSurface())
                    || "で".equals(token.getSurface()))
                    || "動詞".equals(pos) && ("非自立".equals(pos2) || "接尾".equals(pos2))
                    || suruNoun && k == last + 1 && "する".equals(token.getBaseForm());
            if (!attached) {
                break;
            }
            end = token.getPosition() + token.getSurface().length();
        }
        return end;
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
