package com.example.ISDProject.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Store server-side dei refresh token, con ROTAZIONE e REUSE DETECTION.
 *
 * I refresh token sono OPACHI (stringhe casuali, non JWT): essendo memorizzati sul server
 * possono essere revocati, cosa impossibile con un JWT stateless.
 *
 * Ogni login apre una "famiglia" di token (familyId). Ogni rotazione consuma il token corrente
 * ed emette un figlio che eredita la stessa famiglia. Se viene ripresentato un token GIÀ CONSUMATO,
 * significa che due soggetti ne possedevano una copia: è la prova di un furto. In quel caso si
 * revoca l'INTERA famiglia, così anche il ladro perde l'accesso e solo chi conosce la password
 * (rifacendo il login) può rientrare.
 */
@Component
public class RefreshTokenStore {

    private final long ttlMs;
    private final SecureRandom random = new SecureRandom();

    /** Token attualmente validi. */
    private final ConcurrentHashMap<String, Entry> active = new ConcurrentHashMap<>();

    /** Token già consumati da una rotazione: servono solo a rilevare il riuso (furto). */
    private final ConcurrentHashMap<String, Entry> consumed = new ConcurrentHashMap<>(); 

    public RefreshTokenStore(@Value("${jwt.refresh-expiration-ms:604800000}") long ttlMs) { // 7 giorni
        this.ttlMs = ttlMs;
    }

    private record Entry(String username, String familyId, Instant expiresAt) {}

    /** Esito di una rotazione: l'utente associato e il nuovo refresh token emesso. */
    public record RotationResult(String username, String refreshToken) {}

    /** Login: apre una nuova famiglia di token. */
    public String issue(String username) {
        return issueInFamily(username, UUID.randomUUID().toString());
    }
    
    //** Emette un nuovo token all'interno di una famiglia esistente. */
    private String issueInFamily(String username, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        active.put(token, new Entry(username, familyId, Instant.now().plusMillis(ttlMs)));
        return token;
    }

    /**
     * Ruota un refresh token. Il token presentato viene consumato e sostituito da un figlio
     * della stessa famiglia. Se il token era già stato consumato, scatta la reuse detection.
     *
     * @return l'esito, oppure empty se il token è sconosciuto, scaduto o già usato.
     */
    public synchronized Optional<RotationResult> rotate(String oldToken) {
        Entry entry = active.remove(oldToken);

        if (entry == null) {
            // Non è attivo. Era già stato consumato da una rotazione precedente?
            // Se sì, due soggetti avevano lo stesso token: FURTO. Revoca dell'intera famiglia.
            Entry reused = consumed.get(oldToken);
            if (reused != null) {
                System.err.println("[RefreshTokenStore] RIUSO RILEVATO (utente '" + reused.username()
                        + "'): il token era già stato consumato. Revoca dell'intera famiglia.");
                revokeFamily(reused.familyId());
            }
            return Optional.empty(); // token sconosciuto oppure riusato: in entrambi i casi si rifiuta
        }

        // La correttezza NON dipende dal purge: la scadenza è sempre verificata qui.
        if (entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }

        consumed.put(oldToken, entry); // da ora in poi, ripresentarlo = riuso
        String newToken = issueInFamily(entry.username(), entry.familyId());
        return Optional.of(new RotationResult(entry.username(), newToken));
    }

    /** Logout: revoca il token e l'intera famiglia a cui appartiene. */
    public synchronized void revoke(String token) {
        Entry entry = active.get(token);
        if (entry != null) {
            revokeFamily(entry.familyId());
        }
    }

    /** Invalida tutti i token (attivi e consumati) di una famiglia. */
    private void revokeFamily(String familyId) {
        active.entrySet().removeIf(e -> e.getValue().familyId().equals(familyId));
        consumed.entrySet().removeIf(e -> e.getValue().familyId().equals(familyId));
    }

    /**
     * Igiene della memoria: rimuove le entry scadute. Un token consumato è utile solo finché
     * sarebbe potuto essere ancora valido; oltre la scadenza verrebbe comunque rifiutato,
     * quindi ricordarselo non serve più. Non è un requisito di sicurezza, solo di memoria.
     */
    @Scheduled(fixedDelay = 3_600_000) // ogni ora
    public void purgeExpired() {
        Instant now = Instant.now();
        active.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        consumed.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
