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

@Service
public class LlmService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // Fallback in ordine: qualità → velocità
    private static final String[] MODELS = {
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "mixtral-8x7b-32768"
    };

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
            return "⚠️ API key Groq non configurata. Ottienila gratuitamente su console.groq.com e impostala come GROQ_API_KEY.";
        }

        String fullPrompt = "Sei un ingegnere energetico esperto. Analizza questi dati brevemente e dai un consiglio tecnico in italiano (max 200 parole):\n" + promptData;

        Map<String, Object> requestBody = Map.of(
            "messages", List.of(Map.of("role", "user", "content", fullPrompt)),
            "max_tokens", 400
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        for (String model : MODELS) {
            try {
                Map<String, Object> body = new java.util.HashMap<>(requestBody);
                body.put("model", model);
                HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

                Map<?, ?> response = restTemplate.postForObject(GROQ_URL, req, Map.class);

                if (response != null && response.containsKey("choices")) {
                    List<?> choices = (List<?>) response.get("choices");
                    if (!choices.isEmpty()) {
                        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
                        if (message != null) {
                            return message.get("content").toString().trim();
                        }
                    }
                }
            } catch (HttpClientErrorException e) {
                int status = e.getStatusCode().value();
                System.err.println("Groq [" + model + "] HTTP " + status + ": " + e.getResponseBodyAsString());
                if (status != 429) {
                    return "Errore API Groq (" + status + "): " + e.getResponseBodyAsString();
                }
            } catch (HttpServerErrorException | ResourceAccessException e) {
                System.err.println("Groq [" + model + "] error: " + e.getMessage());
            }
        }

        return "⚠️ Tutti i modelli Groq hanno quota esaurita. Riprova tra qualche minuto.";
    }
}
