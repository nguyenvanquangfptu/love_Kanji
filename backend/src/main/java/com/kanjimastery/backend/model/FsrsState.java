package com.kanjimastery.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Trí nhớ FSRS của một thẻ: độ ổn định (số ngày để xác suất nhớ còn 90%) và độ khó (1-10). Thẻ chưa ôn lần nào từ khi
 * có FSRS thì cả hai cột null và Hibernate nạp trạng thái này thành null.
 */
@Embeddable
public record FsrsState(
        @Column(name = "stability") Double stability,
        @Column(name = "difficulty") Double difficulty) {
}
