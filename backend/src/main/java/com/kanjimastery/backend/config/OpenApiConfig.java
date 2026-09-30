package com.kanjimastery.backend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.kanjimastery.backend.model.Kanji;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER,
        description = "Dán Access Token nhận được từ /api/v1/auth/login (không cần gõ chữ 'Bearer')"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI kanjiMasteryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Smart Kanji Mastery & JLPT Mock Exam Platform API")
                        .description("""
                                Backend cho hệ thống ôn luyện Kanji (SuperMemo SM-2) và thi thử JLPT.
                                Đăng nhập qua /api/v1/auth/login để lấy Access Token, bấm nút Authorize
                                phía trên và dán token vào để gọi thử các API cần xác thực.
                                """)
                        .version("v0.1.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
