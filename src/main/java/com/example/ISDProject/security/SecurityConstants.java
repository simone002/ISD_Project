package com.example.ISDProject.security;

/**
 * Fonte unica dei path pubblici (non autenticati).
 * Usata sia da {@link SecurityConfig} (permitAll) sia da {@link JwtFilter} (skip del filtro),
 * così le due liste non possono divergere. Pattern in stile Ant.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    public static final String[] PUBLIC_PATHS = {
        "/",
        "/index.html",
        "/styles.css",
        "/app.js",
        "/favicon.ico",
        "/error",
        "/api/auth/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs/**"
    };
}
