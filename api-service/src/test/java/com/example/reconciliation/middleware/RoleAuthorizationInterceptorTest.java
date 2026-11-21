package com.example.reconciliation.middleware;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleAuthorizationInterceptorTest {
    private final RoleAuthorizationInterceptor interceptor = new RoleAuthorizationInterceptor();

    static class Handler {
        @RequireRole("CHECKER")
        public void checker() { }

        public void open() { }
    }

    @Test
    void allowsMatchingRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(Actor.REQUEST_ATTRIBUTE, new Actor("checker-1", "CHECKER"));

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), handler("checker"));

        assertThat(allowed).isTrue();
    }

    @Test
    void rejectsMissingRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("checker")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void rejectsWrongRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(Actor.REQUEST_ATTRIBUTE, new Actor("maker-1", "MAKER"));

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handler("checker")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void ignoresHandlersWithoutAnnotation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), handler("open"));

        assertThat(allowed).isTrue();
    }

    private HandlerMethod handler(String method) throws NoSuchMethodException {
        return new HandlerMethod(new Handler(), Handler.class.getMethod(method));
    }
}
