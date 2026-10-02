package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.Kanji;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KanjiResponse implements Serializable {
    private Long id;
    private String character;
    private String hanViet;
    private String reading;
    private Integer strokeCount;
    private String jlptLevel;
    private String meaning;
    private String exampleSentence;
    /** Mẹo nhớ chung do AI sinh; null nếu chưa có. */
    private String mnemonic;
    /** Chỉ được set khi lấy qua KanjiService (đường Admin) - null ở các đường khác (vd. SRS). */
    @Builder.Default
    private List<TagResponse> tags = List.of();

    public static KanjiResponse from(Kanji kanji) {
        return KanjiResponse.builder()
                .id(kanji.getId())
                .character(kanji.getCharacter())
                .hanViet(kanji.getHanViet())
                .reading(kanji.getReading())
                .strokeCount(kanji.getStrokeCount())
                .jlptLevel(kanji.getJlptLevel())
                .meaning(kanji.getMeaning())
                .exampleSentence(kanji.getExampleSentence())
                .mnemonic(kanji.getMnemonic())
                .build();
    }
}
