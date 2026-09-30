package com.kanjimastery.backend.config;

import com.kanjimastery.backend.listener.ExamTimeoutListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class RedisListenerConfig {

    private final ExamTimeoutListener examTimeoutListener;

    /**
     * Đăng ký lắng nghe kênh keyevent "expired" của Redis (DB index 0, mặc định).
     * Yêu cầu Redis bật {@code notify-keyspace-events Ex} (đã cấu hình trong docker-compose.yml).
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(examTimeoutListener, new PatternTopic("__keyevent@0__:expired"));
        return container;
    }
}
