package com.kanjimastery.backend.service;

import com.kanjimastery.backend.dto.AddSrsCardsResponse;
import com.kanjimastery.backend.dto.SrsTagStatusResponse;
import com.kanjimastery.backend.repository.KanjiRepository;
import com.kanjimastery.backend.repository.UserKanjiSrsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SrsServiceTest {

    @Mock
    private UserKanjiSrsRepository srsRepository;

    @Mock
    private KanjiRepository kanjiRepository;

    @Mock
    private SrsCalculatorService srsCalculatorService;

    @InjectMocks
    private SrsService srsService;

    @Test
    void addCards_shouldDeduplicateIdsAndReportAlreadyInReview() {
        Set<Long> uniqueIds = Set.of(1L, 2L, 3L, 999L);
        // id 999 không tồn tại -> chỉ 3 từ hợp lệ; trong đó 1 từ đã có trong lịch ôn nên chỉ thêm được 2.
        when(kanjiRepository.countByIdIn(uniqueIds)).thenReturn(3L);
        when(srsRepository.insertCardsIfAbsent(eq(7L), eq(uniqueIds), any())).thenReturn(2);

        AddSrsCardsResponse result = srsService.addCards(7L, List.of(1L, 2L, 2L, 3L, 999L));

        assertThat(result.getAdded()).isEqualTo(2);
        assertThat(result.getAlreadyInReview()).isEqualTo(1);
        verify(srsRepository).insertCardsIfAbsent(eq(7L), eq(uniqueIds), any());
    }

    @Test
    void addCards_shouldReportAllExisting_whenEveryWordIsAlreadyInReview() {
        when(kanjiRepository.countByIdIn(Set.of(5L, 6L))).thenReturn(2L);
        when(srsRepository.insertCardsIfAbsent(eq(7L), eq(Set.of(5L, 6L)), any())).thenReturn(0);

        AddSrsCardsResponse result = srsService.addCards(7L, List.of(5L, 6L));

        assertThat(result.getAdded()).isZero();
        assertThat(result.getAlreadyInReview()).isEqualTo(2);
    }

    @Test
    void getTagStatus_shouldReturnWordCountAndCardsInReview() {
        when(kanjiRepository.countByTags_Id(4L)).thenReturn(43L);
        when(srsRepository.countInReviewByTag(7L, 4L)).thenReturn(12L);

        SrsTagStatusResponse result = srsService.getTagStatus(7L, 4L);

        assertThat(result.getTotalWords()).isEqualTo(43);
        assertThat(result.getInReview()).isEqualTo(12);
    }
}
