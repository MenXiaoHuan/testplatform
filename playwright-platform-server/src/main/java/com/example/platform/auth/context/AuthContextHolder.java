package com.example.platform.auth.context;

public final class AuthContextHolder {
    private static final ThreadLocal<AuthContext> CONTEXT = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(AuthContext context) {
        CONTEXT.set(context);
    }

    public static AuthContext get() {
        return CONTEXT.get();
    }

    public static AuthContext require() {
        AuthContext context = CONTEXT.get();
        if (context == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "login required");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
