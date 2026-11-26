package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.exception.ResourceNotFoundException;
import com.kanjimastery.backend.dto.KanjiRequest;
import com.kanjimastery.backend.dto.KanjiResponse;
import com.kanjimastery.backend.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import com.kanjimastery.backend.model.ExampleSentenceSource;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.TagRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KanjiService {

    private final KanjiRepository kanjiRepository;
    private final TagRepository tagRepository;
    private final StrokeCounter strokeCounter;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<KanjiResponse> search(String level, String keyword, Long tagId, Pageable pageable) {
        JlptLevel normalizedLevel = Levels.optional(level);
        String pattern = StringUtils.hasText(keyword) ? "%" + keyword.trim().toLowerCase() + "%" : null;
        return kanjiRepository.search(normalizedLevel, pattern, tagId, pageable)
                .map(this::toResponseWithTags);
    }

    @Cacheable(value = "kanji", key = "#id")
    @Transactional(readOnly = true)
    public KanjiResponse getById(Long id) {
        Kanji kanji = kanjiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Kanji với id: " + id));
        return toResponseWithTags(kanji);
    }

    @Transactional
    public KanjiResponse create(KanjiRequest request) {
        ensureNotDuplicatedInSameLesson(request, null);
        Kanji kanji = new Kanji();
        kanji.setCreatedAt(LocalDateTime.now(clock));
        applyRequest(kanji, request);
        return toResponseWithTags(kanjiRepository.save(kanji));
    }

    @CacheEvict(value = "kanji", key = "#id")
    @Transactional
    public KanjiResponse update(Long id, KanjiRequest request) {
        Kanji kanji = kanjiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Kanji với id: " + id));
        ensureNotDuplicatedInSameLesson(request, id);
        applyRequest(kanji, request);
        return toResponseWithTags(kanjiRepository.save(kanji));
    }

    @CacheEvict(value = "kanji", key = "#id")
    public void delete(Long id) {
        if (!kanjiRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy Kanji với id: " + id);
        }
        kanjiRepository.deleteById(id);
    }

    /**
     * Cùng một từ được có nhiều dòng (mỗi nghĩa một dòng, vd. 出る "ra" và 出る "có [thưởng]") nếu nằm ở các bài khác nhau.
     * Từ không gắn bài nào thì không được trùng, vì khi đó không phân biệt được với dòng đã có.
     */
    private void ensureNotDuplicatedInSameLesson(KanjiRequest request, Long selfId) {
        Set<Long> tagIds = CollectionUtils.isEmpty(request.getTagIds()) ? Set.of() : new HashSet<>(request.getTagIds());
        for (Kanji other : kanjiRepository.findAllByCharacter(request.getCharacter())) {
            if (other.getId().equals(selfId)) {
                continue;
            }
            if (tagIds.isEmpty()) {
                throw new BadRequestException("Từ '" + request.getCharacter() + "' đã tồn tại - nếu đây là nghĩa khác, hãy gán nó vào bài");
            }
            other.getTags().stream()
                    .filter(tag -> tagIds.contains(tag.getId()))
                    .findFirst()
                    .ifPresent(tag -> {
                        throw new BadRequestException("Từ '" + request.getCharacter() + "' đã có trong bài " + tag.getName());
                    });
        }
    }

    private void applyRequest(Kanji kanji, KanjiRequest request) {
        kanji.setCharacter(request.getCharacter());
        kanji.setHanViet(StringUtils.hasText(request.getHanViet()) ? request.getHanViet() : "");
        // Không tách được (số âm khác số chữ Hán...) thì bỏ các chữ cũ, để không hiện âm của cách viết trước.
        kanji.replaceCharacterParts(HanVietSplitter.split(kanji.getCharacter(), kanji.getHanViet()).parts());
        kanji.setReading(StringUtils.hasText(request.getReading()) ? request.getReading().trim() : null);
        kanji.setStrokeCount(strokeCount(kanji.getCharacter(), request.getStrokeCount()));
        kanji.setJlptLevel(Levels.require(request.getJlptLevel()));
        kanji.setMeaning(request.getMeaning());
        String sentence = StringUtils.hasText(request.getExampleSentence()) ? request.getExampleSentence().trim() : null;
        // Người quản trị sửa câu thì câu là soạn tay; giữ nguyên câu (kể cả câu AI viết) thì giữ nguồn cũ.
        if (!Objects.equals(sentence, kanji.getExampleSentence())) {
            kanji.setExampleSentence(sentence);
            kanji.setExampleSentenceSource(sentence == null ? null : ExampleSentenceSource.MANUAL);
        }
        if (!CollectionUtils.isEmpty(request.getTagIds())) {
            Set<Tag> tags = new HashSet<>(tagRepository.findAllById(request.getTagIds()));
            kanji.setTags(tags);
        } else {
            kanji.setTags(new HashSet<>());
        }
    }

    /**
     * Số nét tính từ bảng số nét từng chữ ({@link StrokeCounter}); số nhập tay chỉ dùng khi từ có chữ chưa có trong bảng.
     */
    private int strokeCount(String word, Integer entered) {
        StrokeCounter.Count count = strokeCounter.count(word);
        if (count.known()) {
            return count.total();
        }
        if (entered == null) {
            throw new BadRequestException("Chưa có số nét của chữ " + String.join(", ", count.unknown())
                    + " - hãy nhập số nét của cả từ");
        }
        return entered;
    }

    /**
     * Sửa riêng âm Hán Việt của một từ (từ báo cáo chất lượng kho từ) và tách lại từng chữ. Không tách được (số âm khác số
     * chữ Hán...) thì báo lỗi, không ghi.
     */
    @CacheEvict(value = "kanji", key = "#id")
    @Transactional
    public KanjiResponse updateHanViet(Long id, String hanViet) {
        Kanji kanji = kanjiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Kanji với id: " + id));
        String normalized = hanViet == null ? "" : hanViet.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        HanVietSplitter.Split split = HanVietSplitter.split(kanji.getCharacter(), normalized);
        if (split.problem() != null && split.problem() != HanVietSplitter.Problem.NO_KANJI) {
            throw new BadRequestException("Âm Hán Việt 「" + normalized + "」 không tách được theo từng chữ của "
                    + kanji.getCharacter() + " (" + split.problem() + ")");
        }
        kanji.setHanViet(normalized);
        kanji.replaceCharacterParts(split.parts());
        return toResponseWithTags(kanjiRepository.save(kanji));
    }

    /** Chỉ gọi bên trong @Transactional - kanji.getTags() là quan hệ LAZY. */
    private KanjiResponse toResponseWithTags(Kanji kanji) {
        KanjiResponse response = KanjiResponse.from(kanji);
        response.setTags(kanji.getTags().stream().map(TagResponse::from).toList());
        return response;
    }
}
