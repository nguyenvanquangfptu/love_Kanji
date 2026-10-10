package com.kanjimastery.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Lớp cha cho các integration test thật: khởi chạy PostgreSQL + Redis trong
 * container tạm (Testcontainers) thay vì phụ thuộc docker-compose của môi
 * trường local, để có thể chạy được ở bất kỳ máy nào có Docker (kể cả CI).
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    // Singleton containers: started once for the whole test run and removed by Testcontainers (Ryuk) when the JVM
    // exits. With @Testcontainers/@Container they were stopped after every test class, while Spring reused its
    // cached context in the next class - still pointing at the ports of the stopped containers.
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("kanji_mastery_test")
            .withUsername("test_user")
            .withPassword("test_password");

    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
