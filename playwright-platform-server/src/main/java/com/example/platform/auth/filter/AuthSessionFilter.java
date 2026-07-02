package com.example.platform.auth.filter;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.context.AuthContext;
import com.example.platform.auth.context.AuthContextHolder;
import com.example.platform.auth.model.AuthSession;
import com.example.platform.auth.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthSessionFilter extends OncePerRequestFilter {
    private final AuthService authService;
    private final AuthProperties authProperties;

    public AuthSessionFilter(AuthService authService, AuthProperties authProperties) {
        this.authService = authService;
        this.authProperties = authProperties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            authService.findSession(readSessionId(request)).ifPresent(this::bindContext);
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
        }
    }

    private void bindContext(AuthSession session) {
        AuthContextHolder.set(new AuthContext(
                session.userId(),
                session.username(),
                session.nickname(),
                session.avatarObjectKey(),
                session.lastSpaceId()));
    }

    private String readSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (authProperties.getCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
