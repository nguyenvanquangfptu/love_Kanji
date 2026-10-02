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
    /** Múi giờ của "ngày học": giới hạn từ mới tính theo ngày ở đây, không theo giờ máy chủ (container chạy UTC). */
    private String dayZone = "Asia/Ho_Chi_Minh";
    /** Ngày học mới bắt đầu lúc mấy giờ (như Anki): học quá nửa đêm vẫn tính vào hôm trước. */
    private int dayStartHour = 4;
    /** Số phút ôn mỗi ngày khi người học chưa đặt mục tiêu. */
    private int defaultDailyMinutes = 20;
    /** Số từ mới mỗi ngày khi người học chưa đặt mục tiêu. */
    private int defaultNewWordsPerDay = 10;
}
