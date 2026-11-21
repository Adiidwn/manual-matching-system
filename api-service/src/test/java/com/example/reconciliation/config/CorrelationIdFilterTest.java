package com.example.reconciliation.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesRequestIdAndKeepsItForTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestId = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> requestId.set(MDC.get("requestId")));

        assertThat(requestId.get()).isNotBlank();
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(requestId.get());
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void keepsIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
        request.addHeader(CorrelationIdFilter.HEADER_REQUEST_ID, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get("requestId")).isEqualTo("req-123"));

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
    }

    @Test
    void replacesOversizedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
        request.addHeader(CorrelationIdFilter.HEADER_REQUEST_ID, "x".repeat(101));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("X-Request-Id")).isNotBlank().isNotEqualTo("x".repeat(101));
    }
}
