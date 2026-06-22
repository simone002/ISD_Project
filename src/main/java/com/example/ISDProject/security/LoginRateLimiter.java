package com.example.ISDProject.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * Rate Limiting sul login: blocca un IP dopo MAX_ATTEMPTS tentativi falliti
 * nell'arco di WINDOW_MS millisecondi. Protegge da brute-force.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000; // finestra di 1 minuto

    // Mappa per tracciare i tentativi di login per ogni IP
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> attempts =
            new ConcurrentHashMap<>();

    // Controlla se l'IP è bloccato
    public boolean isBlocked(String ip) {
        CopyOnWriteArrayList<Long> timestamps =
                attempts.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>());
        long now = System.currentTimeMillis();
        timestamps.removeIf(t -> now - t > WINDOW_MS);
        return timestamps.size() >= MAX_ATTEMPTS;
    }

    // Registra un tentativo fallito per l'IP specificato
    public void recordFailure(String ip) {
        attempts.computeIfAbsent(ip, k -> new CopyOnWriteArrayList<>())
                .add(System.currentTimeMillis());
    }

    // Resetta il contatore dei tentativi per l'IP specificato
    // dopo un login riuscito
    public void reset(String ip) {
        attempts.remove(ip);
    }
}
