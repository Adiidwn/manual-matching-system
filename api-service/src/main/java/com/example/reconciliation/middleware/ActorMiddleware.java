package com.example.reconciliation.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Identity middleware (simulated auth).
 *
 * <p>Reads {@code X-User}/{@code X-Role}, validates them once and stores the
 * result as a request attribute plus MDC fields. Downstream code never reads
 * the raw headers again.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ActorMiddleware extends OncePerRequestFilter {
    public static final String HEADER_USER = "X-User";
    public static final String HEADER_ROLE = "X-Role";
    public static final String MDC_USER = "user";
    public static final String MDC_ROLE = "role";

    private static final Set<String> ROLES = Set.of("MAKER", "CHECKER", "SIGNER");
    private static final int MAX_USER_LENGTH = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String user = trimToNull(request.getHeader(HEADER_USER));
        String role = trimToNull(request.getHeader(HEADER_ROLE));

        if (user != null && user.length() > MAX_USER_LENGTH) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "X-User must be at most " + MAX_USER_LENGTH + " characters");
            return;
        }
        if (role != null && !ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Role must be one of MAKER, CHECKER, SIGNER");
            return;
        }
        if (role != null && user == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-User is required when X-Role is sent");
            return;
        }

        Actor actor = user == null ? null : new Actor(user, role == null ? null : role.toUpperCase(Locale.ROOT));
        if (actor != null) {
            request.setAttribute(Actor.REQUEST_ATTRIBUTE, actor);
            MDC.put(MDC_USER, actor.user());
            if (actor.role() != null) {
                MDC.put(MDC_ROLE, actor.role());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_USER);
            MDC.remove(MDC_ROLE);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
