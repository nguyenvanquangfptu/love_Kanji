package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.model.User;
import com.kanjimastery.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quản trị viên cấp tài khoản người học qua HTTP: chỉ ADMIN gọi được, tài khoản luôn là ROLE_USER. */
@AutoConfigureMockMvc
class AdminUserControllerIT extends AbstractIntegrationTest {

    private final String username = "cap-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        userRepository.findByUsername(username).ifPresent(userRepository::delete);
    }

    @Test
    @WithMockUser(roles = "USER")
    void learners_shouldNotCreateOrListAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, username + "@example.com")))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admins_shouldCreateALearnerWithATemporaryPassword_andSeeItInTheList() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Trường "role" lạ bị bỏ qua: API không có cách nào cấp quyền quản trị.
                        .content(objectMapper.writeValueAsString(Map.of("username", username,
                                "email", username + "@example.com", "role", "ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value(username))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String temporaryPassword = objectMapper.readTree(response).get("temporaryPassword").asText();
        User saved = userRepository.findByUsername(username).orElseThrow();
        assertThat(saved.getRole()).isEqualTo("ROLE_USER");
        assertThat(saved.getPasswordHash()).isNotEqualTo(temporaryPassword);
        assertThat(passwordEncoder.matches(temporaryPassword, saved.getPasswordHash())).isTrue();

        mockMvc.perform(get("/api/v1/admin/users").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.username == '%s')]".formatted(username)).exists())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void creating_shouldRefuseAnInvalidEmailOrATakenUsername() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, "khong-phai-email")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, username + "@example.com")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, "khac-" + username + "@example.com")))
                .andExpect(status().isBadRequest());
    }

    private String body(String username, String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "email", email));
    }
}
