package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.VocabularyImportRequest;
import com.kanjimastery.backend.dto.VocabularyImportRequest.Word;
import com.kanjimastery.backend.dto.VocabularyImportResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.ExampleSentenceSource;
import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Nhập từ vựng của một bài (file JSON {@link VocabularyImportRequest}) vào kho từ, theo quy ước của kho:
 * <ul>
 * <li>Từ đã có cùng cách viết và cách đọc thì chỉ gắn thêm vào bài - giữ một dòng cho một từ để tiến độ ôn của người
 * học không bị tách đôi. Cùng cách viết, khác cách đọc (表 ひょう / おもて) là từ đồng tự: thêm dòng riêng.</li>
 * <li>Từ chỉ viết bằng kana không có cách đọc để phân biệt: có sẵn thì gắn vào bài và cảnh báo, khác nghĩa thì ghi
 * {@code "tao_moi": true}.</li>
 * <li>Một bài không có hai dòng cùng cách viết (như {@link KanjiService}).</li>
 * <li>Tính từ đuôi な bỏ な; số nét tính từ bảng số nét từng chữ; Hán Việt viết hoa và tách từng chữ ngay.</li>
 * </ul>
 * Kiểm tra hết trước khi ghi: có lỗi thì không ghi gì. Nhập lại cùng file không tạo trùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyImportService {

    private static final Pattern LESSON = Pattern.compile("[A-Za-z0-9-]{1,30}");
    private static final Pattern READING = Pattern.compile("[\\p{IsHiragana}ー]+");
    private static final Pattern TRAILING_HIRAGANA = Pattern.compile("\\p{IsHiragana}+$");
    /** Độ dài các cột của bảng kanji. */
    private static final int MAX_WORD = 20;
    private static final int MAX_READING = 100;
    private static final int MAX_HAN_VIET = 50;

    private final KanjiRepository kanjiRepository;
    private final TagRepository tagRepository;
    private final StrokeCounter strokeCounter;
    private final Clock clock;

    @CacheEvict(value = "kanji", allEntries = true, condition = "!#dryRun")
    @Transactional
    public VocabularyImportResponse importLesson(VocabularyImportRequest request, boolean dryRun) {
        String lesson = request.lesson() == null ? "" : request.lesson().strip();
        if (!LESSON.matcher(lesson).matches()) {
            throw new BadRequestException("Tên bài (\"bai\") chỉ gồm chữ, số và dấu gạch ngang, tối đa 30 ký tự - vd. N2-01");
        }
        JlptLevel level = Levels.require(request.level());
        if (request.words() == null || request.words().isEmpty()) {
            throw new BadRequestException("File chưa có từ nào (\"tu_vung\")");
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < request.words().size(); i++) {
            Entry entry = parse(i + 1, request.words().get(i), errors, warnings);
            if (entry != null) {
                entries.add(entry);
            }
        }
        Map<String, List<Kanji>> stored = stored(entries);
        Map<String, Integer> strokes = strokeCounter.lookup(entries.stream()
                .flatMap(entry -> StrokeCounter.kanjiOf(entry.word).stream()).collect(Collectors.toSet()));

        List<Kanji> toCreate = new ArrayList<>();
        Set<Kanji> toLink = new LinkedHashSet<>();
        int alreadyInLesson = 0;
        // Cách viết -> từ nào trong file chiếm chỗ đó trong bài: một bài không có hai dòng cùng cách viết.
        Map<String, String> inLesson = new HashMap<>();
        for (Entry entry : entries) {
            List<Kanji> sameWord = stored.getOrDefault(entry.word, List.of());
            Kanji match = entry.forceNew ? null : match(entry, sameWord, warnings);
            if (match != null) {
                if (inLesson(match, lesson)) {
                    alreadyInLesson++;
                } else if (!toLink.add(match)) {
                    warnings.add(entry.label + ": trùng với một từ khác trong file - chỉ gắn một lần");
                }
                inLesson.putIfAbsent(match.getCharacter(), entry.label);
                continue;
            }
            Kanji clash = sameWord.stream().filter(kanji -> inLesson(kanji, lesson)).findFirst().orElse(null);
            String taken = inLesson.putIfAbsent(entry.word, entry.label);
            if (clash != null || taken != null) {
                errors.add(entry.label + ": bài " + lesson + " đã có 「" + entry.word + "」"
                        + (taken != null ? " (" + taken + ")" : "") + " - một bài không có hai dòng cùng cách viết");
                continue;
            }
            Kanji created = build(entry, level, strokes, errors);
            if (created != null) {
                toCreate.add(created);
            }
        }

        if (!errors.isEmpty() || dryRun) {
            return new VocabularyImportResponse(lesson, dryRun, false, toCreate.size(), toLink.size(), alreadyInLesson,
                    errors, warnings);
        }
        Tag tag = tagRepository.findByName(lesson).orElseGet(() -> tagRepository.save(Tag.builder().name(lesson).build()));
        LocalDateTime now = LocalDateTime.now(clock);
        toCreate.forEach(kanji -> {
            kanji.setCreatedAt(now);
            kanji.getTags().add(tag);
        });
        kanjiRepository.saveAll(toCreate);
        toLink.forEach(kanji -> kanji.getTags().add(tag));
        log.info("Nhập từ vựng bài {} ({}): {} từ mới, gắn {} từ có sẵn, {} từ đã có trong bài", lesson, level,
                toCreate.size(), toLink.size(), alreadyInLesson);
        return new VocabularyImportResponse(lesson, false, true, toCreate.size(), toLink.size(), alreadyInLesson, errors,
                warnings);
    }

    /** Đọc và chuẩn hoá một từ; sai thì ghi lỗi và trả null. */
    private static Entry parse(int number, Word item, List<String> errors, List<String> warnings) {
        String word = item == null ? null : strip(item.word());
        String label = "Từ " + number + (word == null ? "" : " 「" + word + "」");
        if (word == null) {
            errors.add(label + ": thiếu \"tu\"");
            return null;
        }
        String reading = strip(item.reading());
        // Tính từ đuôi な: kho ghi không có な (静か, không phải 静かな).
        if (isNaAdjective(item.partOfSpeech()) && word.length() > 1 && word.endsWith("な")) {
            word = word.substring(0, word.length() - 1);
            if (reading != null && reading.endsWith("な")) {
                reading = reading.substring(0, reading.length() - 1);
            }
        }
        List<String> problems = new ArrayList<>();
        if (word.length() > MAX_WORD) {
            problems.add("cách viết dài quá " + MAX_WORD + " ký tự");
        }
        if (!StringUtils.hasText(item.meaning())) {
            problems.add("thiếu \"nghia\"");
        }
        boolean hasKanji = !StrokeCounter.kanjiOf(word).isEmpty();
        if (hasKanji) {
            if (reading == null) {
                problems.add("thiếu \"doc\" (cách đọc hiragana)");
            } else if (!READING.matcher(reading).matches() || reading.length() > MAX_READING) {
                problems.add("\"doc\" chỉ gồm hiragana, tối đa " + MAX_READING + " ký tự: " + reading);
            } else if (!readingFits(word, reading)) {
                problems.add("cách đọc " + reading + " không khớp phần kana của 「" + word + "」");
            }
        } else if (reading != null) {
            warnings.add(label + ": từ chỉ viết bằng kana nên bỏ qua \"doc\"");
            reading = null;
        }
        String hanViet = item.hanViet() == null ? "" : item.hanViet().strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (hasKanji) {
            if (hanViet.isEmpty()) {
                warnings.add(label + ": chưa có \"han_viet\"");
            } else if (hanViet.length() > MAX_HAN_VIET) {
                problems.add("\"han_viet\" dài quá " + MAX_HAN_VIET + " ký tự");
            } else {
                HanVietSplitter.Problem problem = HanVietSplitter.split(word, hanViet).problem();
                if (problem != null) {
                    problems.add("\"han_viet\" 「" + hanViet + "」 không tách được theo từng chữ Hán (" + problem
                            + ") - cần mỗi chữ Hán một âm");
                }
            }
        } else {
            hanViet = "";
        }
        if (!problems.isEmpty()) {
            problems.forEach(problem -> errors.add(label + ": " + problem));
            return null;
        }
        return new Entry(label, word, reading, hanViet, item.meaning().strip(), strip(item.exampleSentence()),
                item.strokeCount(), Boolean.TRUE.equals(item.forceNew()));
    }

    /**
     * Dòng trong kho là cùng từ: cùng cách viết và cùng cách đọc; từ kana thì cùng cách viết (cảnh báo vì có thể khác
     * nghĩa). Cùng cách viết mà khác cách đọc thì cảnh báo từ đồng tự và trả null (thêm dòng mới).
     */
    private static Kanji match(Entry entry, List<Kanji> sameWord, List<String> warnings) {
        if (sameWord.isEmpty()) {
            return null;
        }
        if (entry.reading == null) {
            Kanji first = sameWord.get(0);
            warnings.add(entry.label + ": kho đã có từ này (nghĩa 「" + first.getMeaning() + "」) nên chỉ gắn vào bài"
                    + " - nếu là nghĩa khác thì ghi \"tao_moi\": true");
            return first;
        }
        for (Kanji kanji : sameWord) {
            if (kanji.getReading() != null && ReadingMatcher.displayReading(kanji.getReading()).equals(entry.reading)) {
                return kanji;
            }
        }
        warnings.add(entry.label + ": kho đã có 「" + entry.word + "」 đọc " + sameWord.stream()
                .map(kanji -> kanji.getReading() == null ? "(kana)" : ReadingMatcher.displayReading(kanji.getReading()))
                .distinct().collect(Collectors.joining(", "))
                + " - từ đồng tự, thêm dòng riêng đọc " + entry.reading);
        return null;
    }

    private static Kanji build(Entry entry, JlptLevel level, Map<String, Integer> strokes, List<String> errors) {
        StrokeCounter.Count count = StrokeCounter.count(entry.word, strokes);
        Integer strokeCount = count.known() ? Integer.valueOf(count.total()) : entry.strokeCount;
        if (strokeCount == null) {
            errors.add(entry.label + ": chưa có số nét của chữ " + String.join(", ", count.unknown())
                    + " - ghi \"so_net\" của cả từ");
            return null;
        }
        Kanji kanji = Kanji.builder()
                .character(entry.word)
                .reading(entry.reading)
                .hanViet(entry.hanViet)
                .meaning(entry.meaning)
                .exampleSentence(entry.exampleSentence)
                .exampleSentenceSource(entry.exampleSentence == null ? null : ExampleSentenceSource.MANUAL)
                .strokeCount(strokeCount)
                .jlptLevel(level)
                .tags(new HashSet<>())
                .build();
        kanji.replaceCharacterParts(HanVietSplitter.split(entry.word, entry.hanViet).parts());
        return kanji;
    }

    /**
     * Các dòng trong kho theo cách viết của từ trong file. Tìm cả theo phần chữ Hán, vì các từ cũ có thể ghi 見 + み(る)
     * thay vì 見る.
     */
    private Map<String, List<Kanji>> stored(List<Entry> entries) {
        Set<String> keys = new HashSet<>();
        entries.forEach(entry -> {
            keys.add(entry.word);
            keys.add(TRAILING_HIRAGANA.matcher(entry.word).replaceFirst(""));
        });
        keys.remove("");
        Map<String, List<Kanji>> byWord = new HashMap<>();
        Set<String> words = entries.stream().map(entry -> entry.word).collect(Collectors.toSet());
        for (Kanji kanji : kanjiRepository.findAllByCharacterIn(keys)) {
            String written = ReadingMatcher.writtenForm(kanji.getCharacter(), kanji.getReading());
            for (String word : new LinkedHashSet<>(List.of(kanji.getCharacter(), written))) {
                if (words.contains(word)) {
                    List<Kanji> list = byWord.computeIfAbsent(word, key -> new ArrayList<>());
                    if (!list.contains(kanji)) {
                        list.add(kanji);
                    }
                }
            }
        }
        byWord.values().forEach(list -> list.sort((a, b) -> Long.compare(a.getId(), b.getId())));
        return byWord;
    }

    private static boolean inLesson(Kanji kanji, String lesson) {
        return kanji.getTags().stream().anyMatch(tag -> lesson.equals(tag.getName()));
    }

    /** Phần kana của cách viết phải có trong cách đọc, đúng chỗ: 張り切る đọc はり…きる. */
    static boolean readingFits(String word, String reading) {
        StringBuilder regex = new StringBuilder("^");
        boolean inKanji = false;
        for (int codePoint : word.codePoints().toArray()) {
            if (QuizDistractorGenerator.isKanji(codePoint)) {
                if (!inKanji) {
                    regex.append("(.+)");
                    inKanji = true;
                }
            } else {
                inKanji = false;
                regex.append(Pattern.quote(Character.toString(toHiragana(codePoint))));
            }
        }
        return Pattern.compile(regex.append('$').toString()).matcher(reading).matches();
    }

    private static int toHiragana(int codePoint) {
        return codePoint >= 'ァ' && codePoint <= 'ヶ' ? codePoint - 0x60 : codePoint;
    }

    private static boolean isNaAdjective(String partOfSpeech) {
        return partOfSpeech != null && (partOfSpeech.contains("形容詞") || partOfSpeech.contains("形容動詞")
                || partOfSpeech.toLowerCase(Locale.ROOT).contains("tính từ"));
    }

    private static String strip(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    /** Một từ đã đọc và chuẩn hoá từ file. */
    private record Entry(String label, String word, String reading, String hanViet, String meaning, String exampleSentence,
                         Integer strokeCount, boolean forceNew) {
    }
}
