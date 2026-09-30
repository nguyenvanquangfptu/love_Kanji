package com.kanjimastery.backend.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.kanjimastery.backend.job.ExamReconciliationJob;
import com.kanjimastery.backend.model.ExamAttemptStatus;
import com.kanjimastery.backend.service.ExamFinalizationService;

/**
 * Lớp bảo vệ CHÍNH (realtime): lắng nghe Redis Keyspace Notification khi key
 * marker {@code exam:timeout:{attemptId}} hết hạn, rồi gọi finalize() ngay.
 *
 * Đây chỉ là cơ chế "best-effort" (Redis có thể bỏ lỡ event nếu server bận
 * hoặc client mất kết nối tạm thời) - {@link ExamReconciliationJob} là lớp
 * dự phòng bù cho giới hạn này.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExamTimeoutListener implements MessageListener {

    private static final Pattern TIMEOUT_KEY_PATTERN = Pattern.compile("^exam:timeout:(\\d+)$");

    private final ExamFinalizationService examFinalizationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
        Matcher matcher = TIMEOUT_KEY_PATTERN.matcher(expiredKey);
        if (!matcher.matches()) {
            return;
        }

        Long attemptId = Long.valueOf(matcher.group(1));
        log.debug("Nhận Keyspace Expired Event cho attempt {}, tiến hành finalize (TIMEOUT).", attemptId);
        try {
            examFinalizationService.finalize(attemptId, ExamAttemptStatus.TIMEOUT);
        } catch (Exception e) {
            log.error("Lỗi khi finalize attempt {} từ Keyspace Event - Reconciliation Job sẽ xử lý bù ở lượt quét sau.",
                    attemptId, e);
        }
    }
}
