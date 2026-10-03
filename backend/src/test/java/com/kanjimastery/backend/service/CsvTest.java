package com.kanjimastery.backend.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void parse_shouldHandleQuotedCommasQuotesAndLineBreaks_skippingBlankLines() {
        String bom = String.valueOf((char) 0xFEFF);
        String text = bom + "cap_do,bai,mau,nghia\r\n"
                + "N5,N5-01,〜は〜です,\"A là B, dùng để giới thiệu\"\r\n"
                + "\r\n"
                + "N4,,〜てから,\"Sau khi làm \"\"A\"\" thì\nlàm B\"\n"
                + "N4,N4-26,〜んです,Giải thích lý do";

        assertThat(Csv.parse(text)).containsExactly(
                List.of("cap_do", "bai", "mau", "nghia"),
                List.of("N5", "N5-01", "〜は〜です", "A là B, dùng để giới thiệu"),
                List.of("N4", "", "〜てから", "Sau khi làm \"A\" thì\nlàm B"),
                List.of("N4", "N4-26", "〜んです", "Giải thích lý do"));
    }

    @Test
    void line_shouldQuoteOnlyWhenNeeded_soParseReadsItBack() {
        List<String> fields = Arrays.asList("N3", null, "〜わけではない", "Không phải là, \"hoàn toàn\"");

        String line = Csv.line(fields);

        assertThat(line).isEqualTo("N3,,〜わけではない,\"Không phải là, \"\"hoàn toàn\"\"\"");
        assertThat(Csv.parse(line)).containsExactly(List.of("N3", "", "〜わけではない", "Không phải là, \"hoàn toàn\""));
    }
}
