package com.kanjimastery.backend.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Lưu {@link ReviewRating} bằng số 1-4 của nó, không bằng thứ tự khai báo (bắt đầu từ 0). */
@Converter
public class ReviewRatingConverter implements AttributeConverter<ReviewRating, Short> {

    @Override
    public Short convertToDatabaseColumn(ReviewRating rating) {
        return rating == null ? null : (short) rating.value();
    }

    @Override
    public ReviewRating convertToEntityAttribute(Short value) {
        return value == null ? null : ReviewRating.fromValue(value);
    }
}
