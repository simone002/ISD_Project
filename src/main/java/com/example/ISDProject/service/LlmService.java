package com.example.ISDProject.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final String[] MODELS = {
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "mixtral-8x7b-32768"
    };

    // --- Circuit Breaker ---
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private volatile State circuitState = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long openedAt = 0L;

    private static final int FAILURE_THRESHOLD = 3;
    private static final long COOLDOWN_MS = 60_000; // 60 secondi prima di rientrare in HALF_OPEN

    @Value("${groq.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public LlmService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    public String askAi(String promptData) {
        if (apiKey == null || apiKey.isBlank()) {
            return "⚠️ API key Groq non configurata. Impostare GROQ_API_KEY.";
        }

        if (!isCallAllowed()) {
            return "⚠️ Servizio AI temporaneamente sospeso (Circuit Breaker OPEN). Riprova tra qualche minuto.";
        }

        String fullPrompt = "Sei un ingegnere energetico esperto. Analizza questi dati brevemente "
                + "e dai un consiglio tecnico in italiano (max 200 parole):\n" + promptData;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        for (String model : MODELS) {
            try {
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("model", model);
                body.put("messages", List.of(Map.of("role", "user", "content", fullPrompt)));
                body.put("max_tokens", 400);

                HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
                Map<?, ?> response = restTemplate.postForObject(GROQ_URL, req, Map.class);

                if (response != null && response.containsKey("choices")) {
                    List<?> choices = (List<?>) response.get("choices");
                    if (!choices.isEmpty()) {
                        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
                        if (message != null) {
                            recordSuccess();
                            return message.get("content").toString().trim();
                        }
                    }
                }
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                System.err.println("Groq [" + model + "] HTTP " + status + ": " + e.getResponseBodyAsString());
                if (status == 429) continue; // rate limit: prova il modello successivo
                recordFailure();
                return "Errore API Groq (" + status + "): " + e.getResponseBodyAsString();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                System.err.println("Groq [" + model + "] error: " + e.getMessage());
                // errore transitorio: prova il modello successivo
            }
        }

        recordFailure();
        return "⚠️ Tutti i modelli Groq hanno quota esaurita. Riprova tra qualche minuto.";
    }

    private synchronized boolean isCallAllowed() {
        if (circuitState == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= COOLDOWN_MS) {
                circuitState = State.HALF_OPEN;
                System.out.println("[CircuitBreaker] → HALF_OPEN: tentativo di ripristino.");
            } else {
                return false;
            }
        }
        return true;
    }

    private synchronized void recordSuccess() {
        failureCount.set(0);
        if (circuitState != State.CLOSED) {
            circuitState = State.CLOSED;
            System.out.println("[CircuitBreaker] → CLOSED: servizio ripristinato.");
        }
    }

    private synchronized void recordFailure() {
        int count = failureCount.incrementAndGet();
        if (count >= FAILURE_THRESHOLD && circuitState != State.OPEN) {
            circuitState = State.OPEN;
            openedAt = System.currentTimeMillis();
            System.out.println("[CircuitBreaker] → OPEN dopo " + count
                    + " fallimenti. Cooldown: " + (COOLDOWN_MS / 1000) + "s.");
        }
    }
}
