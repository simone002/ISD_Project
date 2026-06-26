package com.example.ISDProject.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.ISDProject.security.JwtUtil;
import com.example.ISDProject.security.LoginRateLimiter;
import com.example.ISDProject.security.RefreshTokenStore;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final List<String> ADMIN_ROLES = List.of("ROLE_ADMIN");

    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;
    private final RefreshTokenStore refreshTokenStore;

    public AuthController(JwtUtil jwtUtil, LoginRateLimiter rateLimiter, RefreshTokenStore refreshTokenStore) {
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
        this.refreshTokenStore = refreshTokenStore;
    }

    @PostMapping("/login")
    public Map<String, String> login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (rateLimiter.isBlocked(ip)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Troppi tentativi di login. Riprova tra un minuto.");
        }

        if ("admin".equals(username) && "password".equals(password)) {
            rateLimiter.reset(ip);
            return Map.of(
                    "token", jwtUtil.generateAccessToken(username, ADMIN_ROLES),
                    "refreshToken", refreshTokenStore.issue(username));
        }

        rateLimiter.recordFailure(ip);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali errate.");
    }

    /**
     * Scambia un refresh token valido con un nuovo access token a vita breve.
     * Il refresh token viene RUOTATO: quello usato è invalidato e ne viene restituito uno nuovo.
     */
    //viene chiamato quando il token di accesso è scaduto, ma il refresh token è ancora valido.
    //  Il client può quindi ottenere un nuovo access token senza dover effettuare nuovamente il login.
    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestParam String refreshToken) {
        return refreshTokenStore.rotate(refreshToken)
                .map(result -> Map.of(
                        "token", jwtUtil.generateAccessToken(result.username(), ADMIN_ROLES),
                        "refreshToken", result.refreshToken()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Refresh token non valido o scaduto. Effettua di nuovo il login."));
    }

    /**
     * Logout: revoca il refresh token lato server, così non può più essere usato per ottenere
     * nuovi access token (a differenza di un JWT stateless, qui la revoca è effettiva).
     */
    @PostMapping("/logout")
    public Map<String, String> logout(@RequestParam String refreshToken) {
        refreshTokenStore.revoke(refreshToken);
        return Map.of("message", "Logout effettuato.");
    }
}
