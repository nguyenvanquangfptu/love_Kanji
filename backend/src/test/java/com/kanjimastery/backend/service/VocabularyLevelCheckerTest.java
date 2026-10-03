package com.kanjimastery.backend.service;

import com.kanjimastery.backend.repository.KanjiRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyLevelCheckerTest {

    @Mock
    private KanjiRepository kanjiRepository;

    @InjectMocks
    private VocabularyLevelChecker checker;

    @Test
    void check_shouldListWordsAboveTheLevelAndUnknownWords_butOnlyDistrustManyWordsAboveTheLevel() {
        when(kanjiRepository.findAllWordLevels()).thenReturn(List.of(
                new Word("毎朝", "まいあさ", "N5"), new Word("新聞", "しんぶん", "N5"), new Word("読む", "よ(む)", "N5"),
                new Word("政治", "せいじ", "N4"), new Word("記事", "きじ", "N3"), new Word("勉強", "べんきょう", "N5"),
                new Word("論文", "ろんぶん", "N3"), new Word("統計", "とうけい", "N3"), new Word("調べる", "しらべる", "N5"),
                // Cùng từ có ở cả N3 và N5 thì tính theo cấp dễ nhất.
                new Word("新聞", "しんぶん", "N3")));
        VocabularyLevelChecker.Session session = checker.open("N4");

        // Trợ từ, số, động từ cơ bản する không tính; 勉強する tra được qua 勉強.
        VocabularyLevelChecker.Result easy = session.check("毎朝、新聞を読んで、三時間勉強します。");
        assertThat(easy.aboveLevel()).isEmpty();
        assertThat(easy.unknown()).isEmpty();
        assertThat(easy.suspicious()).isFalse();

        // Một từ vượt cấp, hay từ không có trong kho (kho chỉ có từ của các bài): chỉ ghi chú, chưa đáng ngờ.
        VocabularyLevelChecker.Result oneHardWord = session.check("A「新聞の政治の記事を読む。」");
        assertThat(oneHardWord.aboveLevel()).containsExactly("記事");
        assertThat(oneHardWord.unknown()).isEmpty();
        assertThat(oneHardWord.suspicious()).isFalse();
        assertThat(oneHardWord.describe()).isEqualTo("Từ vượt cấp độ: 記事.");
        VocabularyLevelChecker.Result unknown = session.check("経済と環境と技術の問題。");
        assertThat(unknown.unknown()).containsExactly("経済", "環境", "技術", "問題");
        assertThat(unknown.suspicious()).isFalse();

        VocabularyLevelChecker.Result hard = session.check("記事と論文の統計を調べる。");
        assertThat(hard.aboveLevel()).containsExactly("記事", "論文", "統計");
        assertThat(hard.suspicious()).isTrue();
    }

    private record Word(String getCharacter, String getReading, String getJlptLevel)
            implements KanjiRepository.WordLevel {
    }
}
