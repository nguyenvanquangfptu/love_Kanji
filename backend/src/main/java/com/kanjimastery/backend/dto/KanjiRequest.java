package com.kanjimastery.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KanjiRequest {

    @NotBlank(message = "character không được để trống")
    @Size(max = 20, message = "character tối đa 20 ký tự")
    private String character;

    @Size(max = 50, message = "hanViet tối đa 50 ký tự")
    private String hanViet;

    @Size(max = 100, message = "reading tối đa 100 ký tự")
    private String reading;

    @NotNull(message = "strokeCount không được để trống")
    @Min(value = 0, message = "strokeCount phải >= 0")
    private Integer strokeCount;

    @NotBlank(message = "jlptLevel không được để trống")
    @Size(max = 5, message = "jlptLevel tối đa 5 ký tự")
    private String jlptLevel;

    @NotBlank(message = "meaning không được để trống")
    private String meaning;

    /** Để trống thì AI sẽ tự sinh lại câu ví dụ ở lần tạo trắc nghiệm kế tiếp. */
    @Size(max = 500, message = "exampleSentence tối đa 500 ký tự")
    private String exampleSentence;

    /** Danh sách id tag gán cho Kanji này - null/rỗng nghĩa là không gán tag nào. */
    private List<Long> tagIds;
}
