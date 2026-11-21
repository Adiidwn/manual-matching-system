package com.example.reconciliation.middleware;

/**
 * Request-scoped identity resolved once by {@link ActorMiddleware}.
 * Controllers and interceptors read it instead of parsing headers themselves.
 */
public record Actor(String user, String role) {
    /** Servlet request attribute name. Constant so it can be used in annotations. */
    public static final String REQUEST_ATTRIBUTE = "reconciliation.actor";
}
