package com.kanjimastery.backend.dto;

/** Một chữ Hán của một từ, kèm âm Hán Việt của chữ đó trong từ ấy (勉強: 勉 MIỄN, 強 CƯỜNG). */
public record WordCharacterResponse(String character, String hanViet) {
}
