package com.example.ISDProject.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.ISDProject.security.JwtUtil;
import com.example.ISDProject.security.LoginRateLimiter;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final LoginRateLimiter rateLimiter;

    public AuthController(JwtUtil jwtUtil, LoginRateLimiter rateLimiter) {
        this.jwtUtil = jwtUtil;
        this.rateLimiter = rateLimiter;
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
            return Map.of("token", jwtUtil.generateToken(username));
        }

        rateLimiter.recordFailure(ip);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenziali errate.");
    }
}
