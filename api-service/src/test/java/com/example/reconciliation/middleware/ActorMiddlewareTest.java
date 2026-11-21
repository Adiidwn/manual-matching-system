package com.example.reconciliation.middleware;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActorMiddlewareTest {
    private final ActorMiddleware middleware = new ActorMiddleware();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void resolvesActorOnceAndExposesItToTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/matches");
        request.addHeader("X-User", " maker-1 ");
        request.addHeader("X-Role", "maker");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Actor> seen = new AtomicReference<>();

        middleware.doFilter(request, response, (req, res) -> {
            seen.set((Actor) req.getAttribute(Actor.REQUEST_ATTRIBUTE));
            assertThat(MDC.get("user")).isEqualTo("maker-1");
            assertThat(MDC.get("role")).isEqualTo("MAKER");
        });

        assertThat(seen.get()).isEqualTo(new Actor("maker-1", "MAKER"));
        assertThat(MDC.get("user")).isNull();
        assertThat(MDC.get("role")).isNull();
    }

    @Test
    void allowsAnonymousRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/matches");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        middleware.doFilter(request, response, (req, res) -> called.set(true));

        assertThat(called).isTrue();
        assertThat(request.getAttribute(Actor.REQUEST_ATTRIBUTE)).isNull();
    }

    @Test
    void rejectsUnknownRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/matches");
        request.addHeader("X-User", "maker-1");
        request.addHeader("X-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        middleware.doFilter(request, response, (req, res) -> called.set(true));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(called).isFalse();
    }

    @Test
    void rejectsRoleWithoutUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/matches");
        request.addHeader("X-Role", "MAKER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        middleware.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(MDC.get("role")).isNull();
    }

    @Test
    void rejectsOversizedUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/matches");
        request.addHeader("X-User", "u".repeat(101));
        request.addHeader("X-Role", "MAKER");
        MockHttpServletResponse response = new MockHttpServletResponse();

        middleware.doFilter(request, response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(400);
    }
}
