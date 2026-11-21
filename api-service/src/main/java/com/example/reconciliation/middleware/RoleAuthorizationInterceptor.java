package com.example.reconciliation.middleware;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authorization middleware: turns {@link RequireRole} into a 403 before the
 * controller method is invoked. No controller-level {@code requireRole} helper.
 */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole required = method.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            return true;
        }
        Actor actor = (Actor) request.getAttribute(Actor.REQUEST_ATTRIBUTE);
        if (actor == null || actor.role() == null || !required.value().equalsIgnoreCase(actor.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This action requires role " + required.value());
        }
        return true;
    }
}
