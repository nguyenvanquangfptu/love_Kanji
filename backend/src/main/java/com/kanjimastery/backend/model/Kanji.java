package com.kanjimastery.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "kanji")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Kanji {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Không duy nhất: cùng một từ có thể có nhiều dòng (mỗi nghĩa một dòng), miễn là không chung bài nào. */
    @Column(nullable = false, length = 20)
    private String character;

    @Column(name = "han_viet", nullable = false, length = 50)
    private String hanViet;

    /** Phiên âm hiragana của cả từ; null với từ không có chữ Hán (bản thân từ đã là cách đọc). */
    @Column(length = 100)
    private String reading;

    @Column(name = "stroke_count", nullable = false)
    private Integer strokeCount;

    @Column(name = "jlpt_level", nullable = false, length = 5)
    private String jlptLevel;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String meaning;

    /** Câu ví dụ tiếng Nhật (chứa nguyên văn {@link #character}) do AI sinh, cache lại để không gọi lại API. */
    @Column(name = "example_sentence", columnDefinition = "TEXT")
    private String exampleSentence;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "kanji_tags",
            joinColumns = @JoinColumn(name = "kanji_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
