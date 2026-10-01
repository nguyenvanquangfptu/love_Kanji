package com.kanjimastery.backend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void unknownPath_shouldAnswer404_notServerError() throws Exception {
        mockMvc.perform(get("/khong-ton-tai"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/khong-ton-tai"));
    }

    @Test
    void unsupportedMethod_shouldAnswer405() throws Exception {
        mockMvc.perform(delete("/items/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void pathVariableOfWrongType_shouldAnswer400() throws Exception {
        mockMvc.perform(get("/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu đầu vào không hợp lệ"));
    }

    @Test
    void malformedJsonBody_shouldAnswer400() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedFailure_shouldStillAnswer500() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"));
    }

    @RestController
    static class SampleController {

        @GetMapping("/items/{id}")
        Long item(@PathVariable Long id) {
            return id;
        }

        @PostMapping("/items")
        Map<String, String> create(@RequestBody Map<String, String> body) {
            return body;
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("lỗi bất ngờ");
        }
    }
}
