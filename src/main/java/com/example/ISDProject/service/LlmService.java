package com.example.ISDProject.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL_NAME = "llama3.2";

    public String askAi(String promptData) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            String fullPrompt = "Sei un ingegnere energetico esperto. Analizza questi dati brevemente e dai un consiglio tecnico in italiano:\n" + promptData;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("prompt", fullPrompt);
            requestBody.put("stream", false); 

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            Map<String, Object> response = restTemplate.postForObject(OLLAMA_URL, entity, Map.class);

            if (response != null && response.containsKey("response")) {
                return response.get("response").toString();
            }
            return "Errore: Risposta vuota dalla LLM.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Errore di connessione con Ollama. Assicurati che sia avviato (ollama serve).";
        }
    }
}