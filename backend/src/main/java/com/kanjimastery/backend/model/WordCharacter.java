package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Một chữ Hán trong từ, kèm âm Hán Việt của chữ đó trong chính từ ấy: 行 là HÀNG trong 銀行 nhưng là HÀNH trong 行動.
 *
 * @param character một chữ Hán (một code point, có thể là hai char Java như 𠮟)
 * @param hanViet   âm Hán Việt viết hoa, vd. NGÂN
 */
@Embeddable
public record WordCharacter(
        @Column(nullable = false, length = 1) String character,
        @Column(name = "han_viet", nullable = false, length = 20) String hanViet) {
}
