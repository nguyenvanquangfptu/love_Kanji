package com.kanjimastery.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.srs")
@Getter
@Setter
public class SrsProperties {
    /** Quên một từ (sau khi đã học) chừng này lần thì thành "từ khó". */
    private int hardWordLapses = 6;
    /** Mỗi phiên ôn chỉ để chừng này từ khó ở chỗ cũ, từ khó còn lại dồn xuống cuối để người học không bị ngợp. */
    private int hardWordsUpFront = 5;
}
