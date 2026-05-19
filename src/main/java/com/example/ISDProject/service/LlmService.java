package com.example.ISDProject.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmService {

    private static final String OLLAMA_URL = "http://ollama:11434/api/generate";
    // Try neural-chat first (faster); fallback to llama3.2
    private static final String[] MODEL_NAMES = {"neural-chat", "llama3.2"};

    public String askAi(String promptData) {
        for (String model : MODEL_NAMES) {
            try {
                // RestTemplate with 5-minute timeout for LLM inference
                RestTemplate restTemplate = new RestTemplateBuilder()
                    .setConnectTimeout(java.time.Duration.ofSeconds(10))
                    .setReadTimeout(java.time.Duration.ofMinutes(5))
                    .build();

                String fullPrompt = "Sei un ingegnere energetico esperto. Analizza questi dati brevemente e dai un consiglio tecnico in italiano (max 200 parole):\n" + promptData;

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("prompt", fullPrompt);
                requestBody.put("stream", false);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                Map<String, Object> response = restTemplate.postForObject(OLLAMA_URL, entity, Map.class);

                if (response != null && response.containsKey("response")) {
                    String result = response.get("response").toString().trim();
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
            } catch (Exception e) {
                // Try next model or fallback
                System.err.println("LLM error with model " + model + ": " + e.getMessage());
            }
        }
        return generateFallbackAnalysis(promptData);
    }

    private String generateFallbackAnalysis(String promptData) {
        return "⚠️ MODALITÀ OFFLINE (Ollama non disponibile)\n\n" +
               "Analisi sintetica dei dati forniti:\n" +
               promptData + "\n\n" +
               "💡 Suggerimento: Per attivare le analisi AI, aggiungi Ollama al docker-compose:\n" +
               "  docker compose --file docker-compose.yml --file docker-compose.ollama.yml up --build\n" +
               "o avvia Ollama localmente: ollama serve";
    }
}