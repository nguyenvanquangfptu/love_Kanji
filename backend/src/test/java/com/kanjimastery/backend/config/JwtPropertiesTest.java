package com.kanjimastery.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Đọc application.yml thật: chỉ profile dev được dùng khoá dev có sẵn trong repo. */
class JwtPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtOnly {
    }

    @Test
    void withoutJwtSecret_shouldRefuseToStart() {
        assumeTrue(System.getenv("JWT_SECRET") == null, "máy đang đặt sẵn JWT_SECRET");

        assertThatThrownBy(() -> start())
                .rootCause().hasMessageContaining("JWT_SECRET");
    }

    @Test
    void dockerProfile_withoutJwtSecret_shouldRefuseToStart() {
        assumeTrue(System.getenv("JWT_SECRET") == null, "máy đang đặt sẵn JWT_SECRET");

        assertThatThrownBy(() -> start("--spring.profiles.active=docker"))
                .rootCause().hasMessageContaining("JWT_SECRET");
    }

    @Test
    void weakJwtSecret_shouldRefuseToStart() {
        assertThatThrownBy(() -> start("--app.jwt.secret=too-short"))
                .rootCause().hasMessageContaining("ít nhất 32 ký tự");
    }

    @Test
    void devProfile_shouldStartWithTheDevKey() {
        assumeTrue(System.getenv("JWT_SECRET") == null, "máy đang đặt sẵn JWT_SECRET");

        try (ConfigurableApplicationContext context = start("--spring.profiles.active=dev")) {
            assertThat(context.getBean(JwtProperties.class).getSecret()).startsWith("dev-only-");
        }
    }

    @Test
    void realJwtSecret_shouldBeUsed() {
        String secret = "a-real-deployment-secret-of-48-characters-long!!";

        try (ConfigurableApplicationContext context = start("--app.jwt.secret=" + secret)) {
            assertThat(context.getBean(JwtProperties.class).getSecret()).isEqualTo(secret);
        }
    }

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(JwtOnly.class).web(WebApplicationType.NONE).run(args);
    }
}
