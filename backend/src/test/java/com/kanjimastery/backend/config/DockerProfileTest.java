package com.kanjimastery.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Reads the real application.yml: the docker profile, which runs on servers, hides the API docs unless asked to. */
class DockerProfileTest {

    @Configuration
    static class Empty {
    }

    @Test
    void dockerProfile_shouldTurnTheApiDocsOff_byDefault() {
        assumeTrue(System.getenv("API_DOCS_ENABLED") == null, "máy đang đặt sẵn API_DOCS_ENABLED");

        try (ConfigurableApplicationContext context = start("--spring.profiles.active=docker")) {
            assertThat(apiDocsSwitches(context.getEnvironment())).containsOnly(false);
        }
    }

    @Test
    void dockerProfile_shouldTurnTheApiDocsOn_whenAskedTo() {
        try (ConfigurableApplicationContext context = start("--spring.profiles.active=docker", "--API_DOCS_ENABLED=true")) {
            assertThat(apiDocsSwitches(context.getEnvironment())).containsOnly(true);
        }
    }

    @Test
    void devProfile_shouldKeepTheApiDocs() {
        try (ConfigurableApplicationContext context = start("--spring.profiles.active=dev")) {
            // Not set: springdoc's own default, which is on.
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled")).isNull();
            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled")).isNull();
        }
    }

    private static Boolean[] apiDocsSwitches(Environment environment) {
        return new Boolean[]{
                environment.getProperty("springdoc.api-docs.enabled", Boolean.class),
                environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)};
    }

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(Empty.class).web(WebApplicationType.NONE).run(args);
    }
}
