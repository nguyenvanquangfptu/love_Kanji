package com.kanjimastery.backend.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void request_shouldBeLoggedOnce_withItsStatus_andGiveItsIdBackInAHeader(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> idWhileHandling = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            idWhileHandling.set(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID));
            ((MockHttpServletResponse) res).setStatus(400);
        });

        assertThat(idWhileHandling.get()).hasSize(8);
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(idWhileHandling.get());
        assertThat(output).contains("HTTP POST /api/v1/auth/change-password -> 400");
    }

    @Test
    void request_shouldNotLeaveItsIdOrUser_forTheNextRequestOnTheSameThread() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/progress");

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> MDC.put(RequestLoggingFilter.MDC_USER, "taro"));

        assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
        assertThat(MDC.get(RequestLoggingFilter.MDC_USER)).isNull();
    }

    @Test
    void request_shouldNotLogItsQueryString(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
        request.setQueryString("q=bi-mat&token=abc123");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(output).contains("HTTP GET /api/v1/search -> 200").doesNotContain("abc123", "bi-mat");
    }

    @Test
    void request_shouldBeLoggedAs500_whenItFailsWithoutBeingHandled(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/boom");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            throw new ServletException("hỏng");
        })).isInstanceOf(ServletException.class);
        assertThat(output).contains("HTTP GET /api/v1/boom -> 500");
        assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void swaggerAndCorsPreflight_shouldNotBeLogged(CapturedOutput output) throws Exception {
        MockHttpServletResponse swagger = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"), swagger, new MockFilterChain());
        filter.doFilter(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(swagger.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNull();
        assertThat(output).doesNotContain("HTTP GET /swagger-ui", "HTTP OPTIONS");
    }
}
