package com.kanjimastery.backend.service;

import com.kanjimastery.backend.service.WordClassifier.PartOfSpeech;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordClassifierTest {

    private final WordClassifier classifier = new WordClassifier();

    @Test
    void classify_shouldGoByTheEndOfTheWord() {
        assertThat(classifier.classify("建てる")).isEqualTo(PartOfSpeech.VERB);
        // Danh từ + する, cụm có động từ ở cuối: vẫn là động từ.
        assertThat(classifier.classify("長生きする")).isEqualTo(PartOfSpeech.VERB);
        assertThat(classifier.classify("ご覧になる")).isEqualTo(PartOfSpeech.VERB);
        assertThat(classifier.classify("高い")).isEqualTo(PartOfSpeech.I_ADJECTIVE);
        assertThat(classifier.classify("きれい")).isEqualTo(PartOfSpeech.NA_ADJECTIVE);
        assertThat(classifier.classify("幸せ")).isEqualTo(PartOfSpeech.NA_ADJECTIVE);
        assertThat(classifier.classify("ずいぶん")).isEqualTo(PartOfSpeech.ADVERB);
        // 急 + に (biến thành phó từ).
        assertThat(classifier.classify("急に")).isEqualTo(PartOfSpeech.ADVERB);
        assertThat(classifier.classify("それで")).isEqualTo(PartOfSpeech.CONJUNCTION);
        assertThat(classifier.classify("大きな")).isEqualTo(PartOfSpeech.PRENOUN);
        assertThat(classifier.classify("お年寄り")).isEqualTo(PartOfSpeech.NOUN);
        assertThat(classifier.classify("スピーカー")).isEqualTo(PartOfSpeech.NOUN);
    }
}
