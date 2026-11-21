package com.example.reconciliation.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a request correlation id and the demo user to the logging MDC.
 * The same id is returned in the X-Request-Id response header.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "requestId";
    public static final String USER = "user";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_USER = "X-User";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank() || requestId.length() > 100) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(REQUEST_ID, requestId);
        String user = request.getHeader(HEADER_USER);
        if (user != null && !user.isBlank()) {
            MDC.put(USER, user.trim());
        }
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID);
            MDC.remove(USER);
        }
    }
}
