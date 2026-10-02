package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.KanjiRequest;
import com.kanjimastery.backend.dto.KanjiResponse;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.Kanji;
import com.kanjimastery.backend.model.Tag;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KanjiServiceTest {

    @Mock
    private KanjiRepository kanjiRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private KanjiService kanjiService;

    private final Tag lesson29 = Tag.builder().id(29L).name("N4-29").build();
    private final Tag lesson27 = Tag.builder().id(27L).name("N4-27").build();
    // 開く (ひらく) đã có ở bài 27.
    private final Kanji open = Kanji.builder().id(1L).character("開く").reading("ひらく").meaning("Mở [lớp]")
            .hanViet("KHAI").strokeCount(12).jlptLevel("N4").tags(new HashSet<>(Set.of(lesson27))).build();

    @Test
    void create_shouldAllowSameWordWithAnotherMeaning_whenItBelongsToAnotherLesson() {
        when(kanjiRepository.findAllByCharacter("開く")).thenReturn(List.of(open));
        when(tagRepository.findAllById(List.of(29L))).thenReturn(List.of(lesson29));
        when(kanjiRepository.save(any(Kanji.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KanjiResponse created = kanjiService.create(request("開く", "あく", 29L));

        assertThat(created.getReading()).isEqualTo("あく");
        assertThat(created.getTags()).extracting("name").containsExactly("N4-29");
    }

    @Test
    void create_shouldReject_whenSameWordIsAlreadyInThatLesson() {
        when(kanjiRepository.findAllByCharacter("開く")).thenReturn(List.of(open));

        assertThatThrownBy(() -> kanjiService.create(request("開く", "あく", 27L)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("N4-27");
        verify(kanjiRepository, never()).save(any());
    }

    @Test
    void create_shouldReject_whenDuplicateHasNoLesson() {
        when(kanjiRepository.findAllByCharacter("開く")).thenReturn(List.of(open));

        assertThatThrownBy(() -> kanjiService.create(request("開く", "あく")))
                .isInstanceOf(BadRequestException.class);
        verify(kanjiRepository, never()).save(any());
    }

    @Test
    void update_shouldNotConflictWithItself() {
        when(kanjiRepository.findById(1L)).thenReturn(Optional.of(open));
        when(kanjiRepository.findAllByCharacter("開く")).thenReturn(List.of(open));
        when(tagRepository.findAllById(List.of(27L))).thenReturn(List.of(lesson27));
        when(kanjiRepository.save(any(Kanji.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KanjiResponse updated = kanjiService.update(1L, request("開く", "ひらく", 27L));

        assertThat(updated.getReading()).isEqualTo("ひらく");
    }

    private static KanjiRequest request(String character, String reading, Long... tagIds) {
        KanjiRequest request = new KanjiRequest();
        request.setCharacter(character);
        request.setReading(reading);
        request.setHanViet("KHAI");
        request.setStrokeCount(12);
        request.setJlptLevel("N4");
        request.setMeaning("Mở");
        request.setTagIds(List.of(tagIds));
        return request;
    }
}
