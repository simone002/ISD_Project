package com.example.ISDProject.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;

public class HeliosClient {

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final HttpClient client = HttpClient.newHttpClient();
    
    private static String sessionCookie = null; 
    private static String jwtToken = null;      

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("==========================================");
        System.out.println("   HELIOS CLIENT - SECURE ENTERPRISE      ");
        System.out.println("==========================================");

        while (running) {
            printMenu();
            System.out.print("> Scegli operazione: ");
            String choice = scanner.nextLine();

            switch (choice) {
                case "0":
                    performLogin(scanner);
                    break;
                case "1":
                    sendGetRequest("/energy/daily-report-session"); 
                    break;
                case "2":
                    sendGetRequest("/energy/batch-suggestions");
                    break;
                case "3":
                    setSessionFilter(scanner);
                    break;
                case "4":
                    sendGetRequest("/energy/daily-report-session");
                    break;
                case "5":
                    System.out.println("\n--- ANALISI VENTO ---");
                    handleRequestWithDates(scanner, "/energy/wind-impact");
                    break;
                case "6":
                    System.out.println("\n--- PREVISIONE ---");
                    handleRequestWithDates(scanner, "/energy/forecast");
                    break;
                case "7":
                    System.out.println("\n--- ECONOMIA ---");
                    handleRequestWithDates(scanner, "/energy/financial-report");
                    break;
                case "8":
                    sendGetRequest("/energy/peak-hours");
                    break;
                case "9":
                    System.out.println("\n--- INTELLIGENZA ARTIFICIALE ---");
                    handleRequestWithDates(scanner, "/energy/smart-analysis");
                    break;
                case "q":
                    running = false;
                    System.out.println("Uscita...");
                    break;
                default:
                    System.out.println("Comando non valido.");
            }
            if(running) {
                System.out.println("\nPremi INVIO per continuare...");
                scanner.nextLine();
            }
        }
    }

    private static void printMenu() {
        System.out.println("\nMENU PRINCIPALE:");
        if (jwtToken != null) System.out.println("✅ LOGGATO (Token Attivo)");
        else System.out.println("⛔ NON LOGGATO (Accesso Limitato)");
        
        System.out.println("0. LOGIN (Richiesto!)");
        System.out.println("1. Report Semplice");
        System.out.println("2. Analisi Batch");
        System.out.println("3. Imposta Filtro (Sessione)");
        System.out.println("4. Vedi Filtro");
        System.out.println("5. Analisi Vento");
        System.out.println("6. Previsione");
        System.out.println("7. Report Economico");
        System.out.println("8. Ore di Picco");
        System.out.println("9. CHIEDI ALL'IA (Ollama)");
        System.out.println("q. Esci");
    }

    private static void performLogin(Scanner scanner) {
        System.out.print("Username (admin): ");
        String user = scanner.nextLine();
        System.out.print("Password (password): ");
        String pass = scanner.nextLine();

        String formData = "username=" + URLEncoder.encode(user, StandardCharsets.UTF_8) + 
                          "&password=" + URLEncoder.encode(pass, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                if(body.contains(":")) {
                    jwtToken = body.split(":")[1].replace("\"", "").replace("}", "").trim();
                    System.out.println("✅ LOGIN RIUSCITO! Token salvato.");
                }
            } else {
                System.out.println("❌ Login Fallito: " + response.statusCode());
            }
        } catch (Exception e) {
            System.out.println("Errore Login: " + e.getMessage());
        }
    }

    private static void setSessionFilter(Scanner scanner) {
        if(jwtToken == null) { System.out.println("⚠️ LOGIN RICHIESTO!"); return; }

        System.out.print("Inserisci Data Inizio (YYYY-MM-DD): ");
        String start = scanner.nextLine();
        System.out.print("Inserisci Data Fine (YYYY-MM-DD): ");
        String end = scanner.nextLine();

        String url = BASE_URL + "/energy/session/filter?start=" + start + "&end=" + end;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
            .header("Authorization", "Bearer " + jwtToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            Optional<String> cookieHeader = response.headers().firstValue("Set-Cookie");
            if (cookieHeader.isPresent()) {
                sessionCookie = cookieHeader.get();
                if(sessionCookie.contains(";")) {
                    sessionCookie = sessionCookie.split(";")[0];
                }
                System.out.println("[CLIENT] Cookie di sessione salvato: " + sessionCookie);
            }

            System.out.println("Risposta Server: " + response.body());
        } catch (Exception e) {
            System.err.println("Errore: " + e.getMessage());
        }
    }

    private static void sendGetRequest(String endpoint) {
        if(jwtToken == null) { 
            System.out.println("⛔ ERRORE: Devi fare il LOGIN (Opzione 0) prima!"); 
            return; 
        }

        String fullUrl = BASE_URL + endpoint;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
            .header("Authorization", "Bearer " + jwtToken)
                .GET();

        if (sessionCookie != null) {
            builder.header("Cookie", sessionCookie);
        }

        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                System.out.println("Dati Ricevuti (JSON/Text):");
                System.out.println(response.body());
            } else {
                System.out.println("Errore Server (" + response.statusCode() + "): Accesso Negato.");
            }
            
        } catch (Exception e) {
            System.err.println("Errore connessione: " + e.getMessage());
        }
    }

    private static void handleRequestWithDates(Scanner scanner, String endpointBase) {
        if(jwtToken == null) { System.out.println("⚠️ LOGIN RICHIESTO!"); return; }

        System.out.println("Premi INVIO per analizzare TUTTO lo storico,");
        System.out.println("oppure inserisci le date per un periodo specifico.");
        
        System.out.print("Data Inizio (YYYY-MM-DD): ");
        String start = scanner.nextLine();
        
        String finalEndpoint = endpointBase;

        if (!start.isEmpty()) {
            System.out.print("Data Fine   (YYYY-MM-DD): ");
            String end = scanner.nextLine();
            finalEndpoint += "?start=" + start + "&end=" + end;
            System.out.println(">> Analisi limitata al periodo: " + start + " / " + end);
        } else {
            System.out.println(">> Analisi su TUTTO il dataset.");
        }

        sendGetRequest(finalEndpoint);
    }
}