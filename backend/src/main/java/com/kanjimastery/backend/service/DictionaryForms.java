package com.kanjimastery.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đoán dạng từ điển của một từ như nó xuất hiện trong câu (怒って, 貧しかった, 外出して, 楽だ...) - để gắn câu thi với từ
 * trong kho khi phần gạch chân / đáp án là dạng đã chia. Chỉ đưa ra ứng viên: một ứng viên chỉ được dùng khi kho có
 * đúng từ đó, nên ứng viên thừa (怒って -> 怒う, 怒つ, 怒る) không hại gì.
 * <p>
 * Ứng viên chia theo {@link Tier}, xét lần lượt: trong cùng một tầng mà kho có hơn một dạng (行って -> 行う và 行く) thì là
 * nhập nhằng, người gọi không nên đoán. Mỗi {@link Form} gồm các cách viết của cùng một từ - kho lưu danh từ する lúc
 * kèm する lúc không (連絡する / 外出), tính từ đuôi な lúc kèm な lúc không (楽 / 楽な).
 */
final class DictionaryForms {

    /**
     * Thứ tự xét: tầng trước chắc hơn tầng sau - 話して là 話す trước khi là danh từ 話 + して, こいで là 漕ぐ trước khi là
     * 濃い + で.
     */
    enum Tier { VERB, SURU, I_ADJECTIVE, COPULA }

    record Form(Tier tier, List<String> spellings) {
    }

    private static final List<String> SURU_ENDINGS = List.of("していました", "していた", "している", "していて", "しています",
            "しなくても", "しなかった", "しないで", "しなくて", "しました", "しません", "しない", "します", "される", "された",
            "させる", "させた", "しよう", "して", "した", "する");
    private static final List<String> I_ADJECTIVE_ENDINGS = List.of("くなかった", "くない", "かった", "くて", "ければ", "く",
            "さ");
    private static final List<String> COPULA_ENDINGS = List.of("になった", "になって", "になる", "だった", "でした", "です",
            "だ", "な", "に", "で", "の");
    /** Trợ động từ sau thể て/た: bỏ đi còn thể て/た (書いている -> 書いて). */
    private static final Map<String, String> AFTER_TE = Map.ofEntries(
            Map.entry("ていました", "て"), Map.entry("ています", "て"), Map.entry("ている", "て"), Map.entry("ていた", "た"),
            Map.entry("ていて", "て"), Map.entry("ていない", "て"), Map.entry("ておいて", "て"), Map.entry("ておく", "て"),
            Map.entry("てしまった", "た"), Map.entry("てしまう", "て"), Map.entry("てある", "て"),
            Map.entry("でいました", "で"), Map.entry("でいます", "で"), Map.entry("でいる", "で"), Map.entry("でいた", "だ"),
            Map.entry("でいて", "で"), Map.entry("でしまった", "だ"), Map.entry("でしまう", "で"));
    /** Đuôi thể て/た của động từ nhóm I và các đuôi nguyên thể có thể có (って: 払う / 待つ / 減る). */
    private static final Map<String, List<String>> TE_TA = Map.ofEntries(
            Map.entry("って", List.of("う", "つ", "る")), Map.entry("った", List.of("う", "つ", "る")),
            Map.entry("んで", List.of("む", "ぶ", "ぬ")), Map.entry("んだ", List.of("む", "ぶ", "ぬ")),
            Map.entry("いて", List.of("く")), Map.entry("いた", List.of("く")),
            Map.entry("いで", List.of("ぐ")), Map.entry("いだ", List.of("ぐ")),
            Map.entry("して", List.of("す")), Map.entry("した", List.of("す")));
    private static final List<String> NEGATIVE_ENDINGS = List.of("なかった", "なくても", "なくて", "なければ", "ないで",
            "ない");
    private static final List<String> POLITE_ENDINGS = List.of("ませんでした", "ました", "ません", "ましょう", "ます");
    /** Cột あ / い -> cột う của động từ nhóm I (守ら -> 守る, 省き -> 省く). */
    private static final Map<Character, Character> A_TO_U = Map.of('わ', 'う', 'か', 'く', 'が', 'ぐ', 'さ', 'す', 'た', 'つ',
            'な', 'ぬ', 'ば', 'ぶ', 'ま', 'む', 'ら', 'る');
    private static final Map<Character, Character> I_TO_U = Map.of('い', 'う', 'き', 'く', 'ぎ', 'ぐ', 'し', 'す', 'ち', 'つ',
            'に', 'ぬ', 'び', 'ぶ', 'み', 'む', 'り', 'る');

    private DictionaryForms() {
    }

    /** Các dạng từ điển có thể có của {@code surface}, theo thứ tự tầng; không gồm chính {@code surface}. */
    static List<Form> of(String surface) {
        String word = surface == null ? "" : surface.strip();
        List<Form> forms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        seen.add(word);
        if (word.isEmpty()) {
            return forms;
        }
        verbs(word).forEach(verb -> add(forms, seen, Tier.VERB, verb));
        for (String ending : SURU_ENDINGS) {
            String base = cut(word, ending);
            // Danh từ する có từ hai chữ (外出, にこにこ): 話して là 話す chứ không phải 話 + する.
            if (base != null && base.length() >= 2) {
                add(forms, seen, Tier.SURU, base + "する", base);
                // 〜にする / 〜をする: 留守にして -> 留守, よそ見をして -> よそ見. Xét sau tầng SURU để 気にしないで vẫn là
                // 気にする chứ không phải danh từ 気.
                if (base.endsWith("に") || base.endsWith("を")) {
                    String noun = base.substring(0, base.length() - 1);
                    add(forms, seen, Tier.COPULA, noun, noun + "な", noun + "する");
                }
            }
        }
        // Từ trần mà kho lưu kèm する / な: 連絡 - 連絡する, がっかり - がっかりする, 丁寧 - 丁寧な (な chỉ với từ tận cùng
        // bằng chữ Hán: おかし không phải おかしな)
        add(forms, seen, Tier.SURU, word + "する");
        if (!isKana(word.charAt(word.length() - 1))) {
            add(forms, seen, Tier.COPULA, word + "な");
        }
        for (String ending : I_ADJECTIVE_ENDINGS) {
            String base = cut(word, ending);
            if (base != null) {
                add(forms, seen, Tier.I_ADJECTIVE, base + "い");
            }
        }
        for (String ending : COPULA_ENDINGS) {
            String base = cut(word, ending);
            // おかしな, 大きな, 小さな là 連体詞 chứ không phải tính từ đuôi な (おかしな không phải お菓子 + な).
            boolean attributive = "な".equals(ending) && base != null && (base.endsWith("き") || base.endsWith("し"));
            if (base != null && !attributive) {
                add(forms, seen, Tier.COPULA, base, base + "な", base + "する");
            }
        }
        return forms;
    }

    /**
     * Dạng nguyên thể có thể có của một động từ đã chia: thể て/た, phủ định, lịch sự, bị động / sai khiến. Không đoán từ
     * thân ます đứng một mình: 付き合い, 向き, 届け là danh từ, gắn với 付き合う, 向く, 届ける là sai.
     */
    private static Set<String> verbs(String word) {
        Set<String> conjugated = new LinkedHashSet<>();
        conjugated.add(word);
        AFTER_TE.forEach((ending, te) -> {
            String base = cut(word, ending);
            if (base != null) {
                conjugated.add(base + te);
            }
        });
        Set<String> verbs = new LinkedHashSet<>();
        for (String form : conjugated) {
            verbs.addAll(fromTeTa(form));
            verbs.addAll(fromStem(form, NEGATIVE_ENDINGS, A_TO_U));
            verbs.addAll(fromStem(form, POLITE_ENDINGS, I_TO_U));
        }
        // 叱られた -> 叱られる -> 叱る; すべらせて -> すべらせる -> すべる; 信じられない -> 信じられる -> 信じる; và cả từ đã ở
        // thể từ điển: 行われる -> 行う
        List<String> plain = new ArrayList<>(verbs);
        plain.add(word);
        for (String verb : plain) {
            verbs.addAll(withoutPassiveOrCausative(verb));
        }
        verbs.remove(word);
        return verbs;
    }

    private static List<String> fromTeTa(String form) {
        List<String> verbs = new ArrayList<>();
        if (form.endsWith("行って") || form.endsWith("行った")) {
            verbs.add(form.substring(0, form.length() - 2) + "く");
        }
        TE_TA.forEach((ending, dictionaryEndings) -> {
            String base = cut(form, ending);
            if (base != null) {
                dictionaryEndings.forEach(dictionaryEnding -> verbs.add(base + dictionaryEnding));
            }
        });
        // Nhóm II: 枯れて -> 枯れる, 似せた -> 似せる, 見て -> 見る - thân kết thúc bằng cột い / え hoặc chữ Hán (あて
        // không phải ある)
        for (String ending : List.of("て", "た")) {
            String base = cut(form, ending);
            char last = base == null ? 0 : base.charAt(base.length() - 1);
            if (base != null && (isEStemOrIStem(last) || !isKana(last))) {
                verbs.add(base + "る");
            }
        }
        return verbs;
    }

    /**
     * Bỏ đuôi ({@code endings}) để còn thân động từ, rồi đổi chữ cuối của thân về cột う (nhóm I) hoặc thêm る (nhóm II):
     * 守らない -> 守る, 省きます -> 省く, 信じない -> 信じる.
     */
    private static List<String> fromStem(String form, List<String> endings, Map<Character, Character> toU) {
        List<String> verbs = new ArrayList<>();
        for (String ending : endings) {
            String stem = cut(form, ending);
            if (stem == null) {
                continue;
            }
            Character last = stem.charAt(stem.length() - 1);
            Character u = toU.get(last);
            if (u != null) {
                verbs.add(stem.substring(0, stem.length() - 1) + u);
            }
            if (isEStemOrIStem(last)) {
                verbs.add(stem + "る");
            }
        }
        return verbs;
    }

    /** 叱られる -> 叱る (られる của nhóm II, hoặc れる sau cột あ của nhóm I); すべらせる -> すべる. */
    private static List<String> withoutPassiveOrCausative(String verb) {
        List<String> verbs = new ArrayList<>();
        String ichidan = cut(verb, "られる");
        if (ichidan != null) {
            verbs.add(ichidan + "る");
        }
        for (String ending : List.of("れる", "せる")) {
            String base = cut(verb, ending);
            if (base != null && !base.isEmpty()) {
                Character u = A_TO_U.get(base.charAt(base.length() - 1));
                if (u != null) {
                    verbs.add(base.substring(0, base.length() - 1) + u);
                }
            }
        }
        return verbs;
    }

    /** Thân của động từ nhóm II kết thúc bằng chữ cột い / え (信じ, 壊れ, 似せ). */
    private static boolean isEStemOrIStem(char c) {
        return "いきぎしじちぢにひびぴみりえけげせぜてでねへべぺめれ".indexOf(c) >= 0;
    }

    private static boolean isKana(char c) {
        return c >= 'ぁ' && c <= 'ヿ';
    }

    /** {@code word} bỏ đuôi {@code ending}, null nếu không có đuôi đó hoặc bỏ xong không còn gì. */
    private static String cut(String word, String ending) {
        return word.endsWith(ending) && word.length() > ending.length()
                ? word.substring(0, word.length() - ending.length()) : null;
    }

    private static void add(List<Form> forms, Set<String> seen, Tier tier, String... spellings) {
        List<String> fresh = new ArrayList<>();
        for (String spelling : spellings) {
            if (seen.add(spelling)) {
                fresh.add(spelling);
            }
        }
        if (!fresh.isEmpty()) {
            forms.add(new Form(tier, List.copyOf(fresh)));
        }
    }
}
