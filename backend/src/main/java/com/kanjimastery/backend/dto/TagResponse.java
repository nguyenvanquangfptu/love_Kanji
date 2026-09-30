package com.kanjimastery.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kanjimastery.backend.model.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TagResponse {
    private Long id;
    private String name;
    /** Chỉ có ở GET /tags - null (và bị ẩn khỏi JSON) khi tag nằm lồng trong KanjiResponse. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long wordCount;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build();
    }
}
