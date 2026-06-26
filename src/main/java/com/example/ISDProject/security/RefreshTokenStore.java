package com.example.ISDProject.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Store server-side dei refresh token, con rotazione.
 *
 * I refresh token sono OPACHI (stringhe casuali, non JWT): a differenza di un JWT stateless,
 * un token opaco memorizzato sul server può essere revocato e ruotato. A ogni rinnovo il vecchio
 * token viene consumato e ne viene emesso uno nuovo; se un token già ruotato viene ripresentato
 * (es. perché rubato e riusato), lo store non lo riconosce più e il rinnovo fallisce.
 */
@Component
public class RefreshTokenStore {

    private final long ttlMs;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    public RefreshTokenStore(@Value("${jwt.refresh-expiration-ms:604800000}") long ttlMs) { // 7 giorni
        this.ttlMs = ttlMs;
    }

    private record Entry(String username, Instant expiresAt) {}

    /** Esito di una rotazione: l'utente associato e il nuovo refresh token emesso. */
    public record RotationResult(String username, String refreshToken) {}

    /** Emette un nuovo refresh token opaco per l'utente e lo registra. */
    public String issue(String username) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.put(token, new Entry(username, Instant.now().plusMillis(ttlMs)));
        return token;
    }

    /**
     * Ruota un refresh token: se è valido lo invalida ed emette un nuovo token per lo stesso utente.
     * @return l'esito (utente + nuovo token), oppure empty se il token è sconosciuto, scaduto o già usato.
     */
    public synchronized Optional<RotationResult> rotate(String oldToken) {
        Entry entry = store.remove(oldToken); // consuma il vecchio token (one-time use)
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        String newToken = issue(entry.username());
        return Optional.of(new RotationResult(entry.username(), newToken));
    }

    public void revoke(String token) {
        store.remove(token);
    }
}
