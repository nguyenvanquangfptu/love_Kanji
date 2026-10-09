package com.kanjimastery.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanjimastery.backend.AbstractIntegrationTest;
import com.kanjimastery.backend.dto.AuthResponse;
import com.kanjimastery.backend.dto.RegisterRequest;
import com.kanjimastery.backend.repository.UserRepository;
import com.kanjimastery.backend.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real tokens through the real filter chain: what each token opens, and what logging out closes. */
@AutoConfigureMockMvc
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "mat-khau-ban-dau";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;

    private final List<String> usernames = new ArrayList<>();

    @AfterEach
    void tearDown() {
        usernames.forEach(username -> userRepository.findByUsername(username).ifPresent(userRepository::delete));
    }

    @Test
    void refreshToken_shouldNotOpenTheApi_evenAfterLogout() throws Exception {
        AuthResponse tokens = newLearner();

        callApi(tokens.getAccessToken()).andExpect(status().isOk());
        callApi(tokens.getRefreshToken()).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(AUTHORIZATION, "Bearer " + tokens.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tokens.getRefreshToken()))))
                .andExpect(status().isNoContent());

        callApi(tokens.getAccessToken()).andExpect(status().isUnauthorized());
        callApi(tokens.getRefreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void accessToken_shouldNotRefresh_norEndTheLearnersSessions() throws Exception {
        AuthResponse tokens = newLearner();

        refresh(tokens.getAccessToken()).andExpect(status().isUnauthorized());

        // Still a valid session: the access token was refused as the wrong type, not treated as a stolen refresh token.
        refresh(tokens.getRefreshToken()).andExpect(status().isOk());
    }

    private AuthResponse newLearner() {
        String username = "auth-" + UUID.randomUUID().toString().substring(0, 8);
        usernames.add(username);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword(PASSWORD);
        return authService.register(request);
    }

    private ResultActions callApi(String bearerToken) throws Exception {
        return mockMvc.perform(get("/api/v1/profile/learning").header(AUTHORIZATION, "Bearer " + bearerToken));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", refreshToken))));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
