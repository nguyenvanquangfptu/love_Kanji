package com.kanjimastery.backend.service;

import com.kanjimastery.backend.model.JlptLevel;
import com.kanjimastery.backend.dto.GrammarImportResult;
import com.kanjimastery.backend.dto.GrammarPointRequest;
import com.kanjimastery.backend.exception.BadRequestException;
import com.kanjimastery.backend.model.GrammarPoint;
import com.kanjimastery.backend.repository.GrammarPointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrammarPointServiceTest {

    @Mock
    private GrammarPointRepository repository;

    @InjectMocks
    private GrammarPointService service;

    @Test
    void importCsv_shouldCreateNewPoints_updateChangedOnes_andReportBadLines() {
        GrammarPoint same = point(JlptLevel.N4, "N4-26", "〜んです", "Giải thích lý do", null);
        GrammarPoint changed = point(JlptLevel.N4, "N4-34", "〜とおりに", "Làm theo", null);
        when(repository.findByJlptLevelAndPattern(JlptLevel.N4, "〜んです")).thenReturn(Optional.of(same));
        when(repository.findByJlptLevelAndPattern(JlptLevel.N4, "〜とおりに")).thenReturn(Optional.of(changed));
        when(repository.findByJlptLevelAndPattern(JlptLevel.N4, "〜てから")).thenReturn(Optional.empty());
        String csv = """
                cap_do,bai,mau,nghia,cach_noi
                n4,N4-26,〜んです,Giải thích lý do
                N4,N4-34,~とおりに,"Làm đúng như, theo như",Vた + とおりに
                N4,N4-16,～てから,Sau khi,Vて + から
                N6,N6-01,〜です,Là
                N4,N4-20,,Thiếu mẫu
                N4,N4-21
                """;

        GrammarImportResult result = service.importCsv(csv);

        // Dấu sóng ~ ～ đều thống nhất thành 〜 nên khớp với điểm đã có.
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(result.errors()).containsExactly(
                "Dòng 5: cấp độ \"N6\" không hợp lệ (N1-N5)",
                "Dòng 6: thiếu mẫu ngữ pháp",
                "Dòng 7: cần ít nhất 4 cột (cấp độ, bài, mẫu, nghĩa)");
        assertThat(changed.getMeaningVi()).isEqualTo("Làm đúng như, theo như");
        assertThat(changed.getConnection()).isEqualTo("Vた + とおりに");
        ArgumentCaptor<GrammarPoint> saved = ArgumentCaptor.forClass(GrammarPoint.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getPattern()).isEqualTo("〜てから");
        assertThat(saved.getValue().getLesson()).isEqualTo("N4-16");
        assertThat(saved.getValue().getConnection()).isEqualTo("Vて + から");
    }

    @Test
    void exportCsv_shouldWriteTheImportFormat_soItCanBeImportedBack() {
        when(repository.findByJlptLevelOrderByLessonAscIdAsc(JlptLevel.N3)).thenReturn(List.of(
                point(JlptLevel.N3, null, "〜わけではない", "Không phải là, không hẳn", "普通形 + わけではない")));

        String csv = service.exportCsv("n3");

        assertThat(csv).isEqualTo("cap_do,bai,mau,nghia,cach_noi,giai_thich\r\n"
                + "N3,,〜わけではない,\"Không phải là, không hẳn\",普通形 + わけではない,\r\n");
    }

    @Test
    void create_shouldRefuseAPatternTheLevelAlreadyHas() {
        when(repository.findByJlptLevelAndPattern(JlptLevel.N4, "〜てから"))
                .thenReturn(Optional.of(point(JlptLevel.N4, null, "〜てから", "Sau khi", null)));
        GrammarPointRequest request = new GrammarPointRequest();
        request.setJlptLevel("N4");
        request.setPattern("~てから");
        request.setMeaningVi("Sau khi");

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BadRequestException.class);
        verify(repository, times(0)).save(any());
    }

    private static GrammarPoint point(JlptLevel level, String lesson, String pattern, String meaning, String connection) {
        return GrammarPoint.builder().id(1L).jlptLevel(level).lesson(lesson).pattern(pattern).meaningVi(meaning)
                .connection(connection).build();
    }
}
