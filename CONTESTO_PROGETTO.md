# Contesto Progetto - ISDProjectHelios

Documento breve da riusare nelle prossime chat per capire rapidamente come funziona il progetto e dove intervenire.

## Obiettivo del progetto

Applicazione Spring Boot per l'analisi di dati di energia rinnovabile. Espone API REST protette da JWT, carica i dati da `data.csv` all'avvio, usa PostgreSQL in produzione e include una dashboard frontend servita dallo stesso backend.

## Stack reale

- Java 21
- Spring Boot 3.5.8
- Spring Web, Spring Data JPA, Spring Security
- PostgreSQL in produzione, H2 solo nei test
- Lombok
- OpenCSV per importazione dati
- JJWT per i token
- Ollama via HTTP per le analisi AI
- Docker e Docker Compose

## Flusso di avvio

1. Parte la classe principale `src/main/java/com/example/ISDProject/IsdProjectHeliosApplication.java`.
2. `DataLoader` importa `data.csv` nel database PostgreSQL se la tabella è vuota.
3. `SecurityConfig` abilita sicurezza stateless con filtro JWT.
4. Il frontend statico viene servito da `src/main/resources/static`.
5. Il client CLI `HeliosClient` si autentica con `/api/auth/login` e poi chiama le API energia.

## Mappa dei componenti

### Entry point

- `IsdProjectHeliosApplication` avvia Spring Boot.

### Controller

- `AuthController` gestisce il login con credenziali hardcoded `admin/password` e restituisce un token JWT.
- `EnergyController` contiene tutta la logica di analisi dei dati.

### Service

- `LlmService` invia prompt a Ollama su `http://localhost:11434/api/generate` usando il modello `llama3.2`.
- `UserSession` mantiene un filtro date per sessione HTTP.

### Model e repository

- `RenewableData` è l'entità JPA con i campi energetici.
- `RenewableRepository` estende `JpaRepository` e supporta anche query per intervallo date.

### Security

- `JwtUtil` genera e valida token JWT.
- `JwtFilter` legge l'header `Authorization: Bearer ...` e blocca l'accesso senza token valido.
- `SecurityConfig` rende pubblica solo la login e protegge tutte le API energia.

### Client CLI

- `client/HeliosClient.java` è un client a riga di comando che fa login, salva il token e richiama le API.
- `src/main/resources/static/` contiene il frontend dashboard.

## Entità dati

`RenewableData` contiene:

- `dateTime`
- `windSpeed`
- `sunshine`
- `airPressure`
- `radiation`
- `airTemperature`
- `relativeAirHumidity`
- `systemProduction`

## Configurazione importante

File principale: `src/main/resources/application.properties`

- database PostgreSQL configurato via variabili d'ambiente
- H2 mantenuto solo per i test in `src/test/resources/application.properties`
- timezone JSON impostata su UTC
- formato date Jackson: `yyyy-MM-dd HH:mm:ss`

## Dati e import

`DataLoader` legge `data.csv` dalla root del progetto.

Dettaglio importante: il loader si aspetta un timestamp nel formato `dd.MM.yyyy-HH:mm` e 8 colonne dati dopo l'header.

## API realmente presenti nel controller

### Autenticazione

- `POST /api/auth/login`

### Energia

- `POST /api/energy/session/filter`
- `GET /api/energy/daily-report-session`
- `GET /api/energy/batch-suggestions`
- `GET /api/energy/wind-impact`
- `GET /api/energy/forecast`
- `GET /api/energy/smart-analysis`
- `GET /api/energy/financial-report`
- `GET /api/energy/peak-hours`

## Comportamento da ricordare

- Tutte le API sotto `/api/energy/**` richiedono JWT valido.
- Il token JWT viene generato con una chiave creata a runtime: dopo un riavvio dell'applicazione i token precedenti non sono più validi.
- Il login è semplificato e non usa un database utenti.
- `daily-report-session` applica il filtro della sessione solo se è stato impostato prima con `/api/energy/session/filter`.
- `smart-analysis` chiama Ollama; se Ollama non è attivo, la risposta torna con errore di connessione.

## Punti di attenzione per le prossime chat

Quando si apre una nuova chat, le cose più utili da citare sono:

1. quale endpoint o classe vuoi cambiare;
2. se stai lavorando sulla parte autenticazione, report, AI o frontend;
3. se il problema riguarda il client CLI, il frontend o le API REST;
4. se serve verificare il CSV, il database PostgreSQL o la sicurezza JWT.

## Discrepanze già visibili

- Il README descrive alcuni endpoint in modo non allineato al controller attuale.
- Il client CLI mostra opzioni che non sempre corrispondono 1:1 alla documentazione esistente.

## File chiave da aprire subito

- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/java/com/example/ISDProject/controller/EnergyController.java`
- `src/main/java/com/example/ISDProject/controller/AuthController.java`
- `src/main/java/com/example/ISDProject/security/SecurityConfig.java`
- `src/main/java/com/example/ISDProject/security/JwtFilter.java`
- `src/main/java/com/example/ISDProject/service/LlmService.java`
- `src/main/java/com/example/ISDProject/util/DataLoader.java`
- `src/main/java/com/example/ISDProject/client/HeliosClient.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/resources/static/app.js`
- `Dockerfile`
- `docker-compose.yml`
