package com.example.ISDProject.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ISDProject.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestParam String username, @RequestParam String password) {
        
        if ("admin".equals(username) && "password".equals(password)) {
            
            String token = jwtUtil.generateToken(username);
            
            return Map.of("token", token);
            
        } else {
            throw new RuntimeException("Credenziali Errate!");
        }
    }
}