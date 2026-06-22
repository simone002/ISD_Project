package com.example.ISDProject.resilience;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Circuit Breaker generico e riutilizzabile.
 * Isola un servizio esterno inaffidabile: dopo troppi fallimenti consecutivi "apre il circuito"
 * e fa fallire subito le chiamate, evitando di bloccare i thread su una dipendenza morta
 * (cascade failure). Dopo un cooldown tenta un ripristino controllato (HALF_OPEN).
 *
 * Responsabilità unica: gestire la macchina a stati della resilienza. Non sa nulla di HTTP,
 * di Groq o di cosa venga protetto — il chiamante segnala solo successi e fallimenti.
 */
@Component
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long cooldownMs;

    private volatile State state = State.CLOSED;
    private int failureCount = 0;
    private long openedAt = 0L;

    public CircuitBreaker(
            @Value("${circuitbreaker.failure-threshold:3}") int failureThreshold,
            @Value("${circuitbreaker.cooldown-ms:60000}") long cooldownMs) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
    }

    /**
     * @return true se la chiamata è permessa. In stato OPEN ritorna false finché non scade
     *         il cooldown, dopodiché passa a HALF_OPEN e concede un tentativo di sonda.
     */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= cooldownMs) {
                state = State.HALF_OPEN;
                System.out.println("[CircuitBreaker] → HALF_OPEN: tentativo di ripristino.");
                return true;
            }
            return false;
        }
        return true; // CLOSED o HALF_OPEN
    }

    /** Una chiamata è andata a buon fine: il circuito torna (o resta) chiuso. */
    public synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            System.out.println("[CircuitBreaker] → CLOSED: servizio ripristinato.");
        }
        failureCount = 0;
        state = State.CLOSED;
    }

    /** Una chiamata è fallita: apre il circuito se si supera la soglia o se la sonda fallisce. */
    public synchronized void recordFailure() {
        failureCount++;
        if (state != State.OPEN && (state == State.HALF_OPEN || failureCount >= failureThreshold)) {
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
            System.out.println("[CircuitBreaker] → OPEN dopo " + failureCount
                    + " fallimenti. Cooldown: " + (cooldownMs / 1000) + "s.");
        }
    }

    public State getState() {
        return state;
    }
}
