package com.kanjimastery.backend.dto;

import com.kanjimastery.backend.model.JlptLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class GrammarPointResponse {
    private Long id;
    private JlptLevel jlptLevel;
    private String lesson;
    private String pattern;
    private String connection;
    private String meaningVi;
    private String explanationVi;
    /** Số câu thi gắn với điểm này: đã duyệt (vào đề được) và đang chờ duyệt. */
    private long approvedQuestions;
    private long draftQuestions;
}
