package com.example.ISDProject.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

import com.example.ISDProject.resilience.CircuitBreaker;

/**
 * Proxy verso la Groq API (LLM cloud). Si occupa solo della comunicazione:
 * costruzione della richiesta, autenticazione, fallback tra modelli e parsing della risposta.
 * La resilienza (apertura/chiusura del circuito) è delegata al {@link CircuitBreaker}.
 */
@Service
public class LlmService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // Fallback in ordine: qualità → velocità. Su quota esaurita (429) si passa al successivo.
    private static final String[] MODELS = {
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "mixtral-8x7b-32768"
    };

    // Istruzioni di sistema: ruolo, regole anti-allucinazione e formato di output.
    // Tenute separate dai dati (messaggio "user") per non confondere comandi e contenuto.
    private static final String SYSTEM_PROMPT = """
            Sei un ingegnere specializzato nella manutenzione di impianti fotovoltaici industriali. \
            Ricevi una serie di letture orarie dei sensori e devi diagnosticare lo stato dell'impianto.

            Regole di analisi:
            - Basati ESCLUSIVAMENTE sui dati forniti: non inventare valori, date o periodi non presenti.
            - Anomalia chiave: radiazione solare elevata (sole presente) ma produzione nulla o molto \
            bassa indica un probabile guasto (inverter, disconnessione, ombreggiamento o moduli sporchi).
            - Produzione nulla con radiazione nulla (notte o maltempo) è NORMALE: non segnalarla come guasto.
            - Un vento sostenuto raffredda i moduli e tende a migliorarne il rendimento.

            Formato della risposta (in italiano, massimo 200 parole, tono tecnico e diretto):
            1. DIAGNOSI: stato generale dell'impianto nel periodo osservato.
            2. CRITICITÀ: anomalie rilevate, oppure "Nessuna anomalia" se tutto è regolare.
            3. RACCOMANDAZIONE: una sola azione concreta e prioritaria.""";

    @Value("${groq.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;

    public LlmService(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restTemplate = new RestTemplate(factory);
    }

    public String askAi(String promptData) {
        if (apiKey == null || apiKey.isBlank()) {
            return "⚠️ API key Groq non configurata. Impostare GROQ_API_KEY.";
        }

        if (!circuitBreaker.allowRequest()) {
            return "⚠️ Servizio AI temporaneamente sospeso (Circuit Breaker OPEN). Riprova tra qualche minuto.";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        for (String model : MODELS) {
            try {
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("model", model);
                body.put("messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", promptData)));
                body.put("temperature", 0.3); // bassa: diagnosi fattuale, non testo creativo
                body.put("max_tokens", 400);

                HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
                Map<?, ?> response = restTemplate.postForObject(GROQ_URL, req, Map.class);

                if (response != null && response.containsKey("choices")) {
                    List<?> choices = (List<?>) response.get("choices");
                    if (!choices.isEmpty()) {
                        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
                        if (message != null) {
                            circuitBreaker.recordSuccess();
                            return message.get("content").toString().trim();
                        }
                    }
                }
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                System.err.println("Groq [" + model + "] HTTP " + status + ": " + e.getResponseBodyAsString());
                if (status == 429) continue; // rate limit: prova il modello successivo
                circuitBreaker.recordFailure();
                return "Errore API Groq (" + status + "): " + e.getResponseBodyAsString();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                System.err.println("Groq [" + model + "] error: " + e.getMessage());
                // errore transitorio: prova il modello successivo
            }
        }

        circuitBreaker.recordFailure();
        return "⚠️ Tutti i modelli Groq hanno quota esaurita. Riprova tra qualche minuto.";
    }
}
