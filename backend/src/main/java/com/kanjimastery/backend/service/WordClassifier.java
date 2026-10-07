package com.kanjimastery.backend.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;

import java.util.List;

/**
 * Từ loại của một từ vựng (dạng từ điển) theo bộ phân tích hình thái Kuromoji (từ điển IPADIC), lấy theo phần cuối
 * của từ: 長生きする, ご覧になる là động từ; お年寄り là danh từ; 急に là phó từ. Dùng để chọn đáp án nhiễu cùng từ
 * loại cho câu 文脈規定.
 * <p>
 * Nạp từ điển mất khoảng 0,2 giây và vài chục MB bộ nhớ, nên chỉ tạo khi cần (một lượt sinh câu) rồi bỏ.
 */
final class WordClassifier {

    enum PartOfSpeech { NOUN, VERB, I_ADJECTIVE, NA_ADJECTIVE, ADVERB, CONJUNCTION, PRENOUN, OTHER }

    private final Tokenizer tokenizer = new Tokenizer();

    PartOfSpeech classify(String word) {
        List<Token> tokens = tokenizer.tokenize(word);
        if (tokens.isEmpty()) {
            return PartOfSpeech.OTHER;
        }
        Token last = tokens.get(tokens.size() - 1);
        return switch (last.getPartOfSpeechLevel1()) {
            case "動詞" -> PartOfSpeech.VERB;
            case "形容詞" -> PartOfSpeech.I_ADJECTIVE;
            case "副詞" -> PartOfSpeech.ADVERB;
            case "接続詞" -> PartOfSpeech.CONJUNCTION;
            case "連体詞" -> PartOfSpeech.PRENOUN;
            // 急に: danh từ + に biến thành phó từ.
            case "助詞" -> "副詞化".equals(last.getPartOfSpeechLevel2()) ? PartOfSpeech.ADVERB : PartOfSpeech.OTHER;
            case "名詞" -> "形容動詞語幹".equals(last.getPartOfSpeechLevel2())
                    ? PartOfSpeech.NA_ADJECTIVE
                    : PartOfSpeech.NOUN;
            default -> PartOfSpeech.OTHER;
        };
    }
}
