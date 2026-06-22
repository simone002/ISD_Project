# ISDProjectHelios

**Helios** è un sistema distribuito Spring Boot per il monitoraggio e l'analisi di un impianto
fotovoltaico industriale. Espone un'API REST protetta da JWT, serve una dashboard web e arricchisce
le analisi con commenti generati da un LLM tramite la **Groq API**. Realizzato per il corso di
*Ingegneria dei Sistemi Distribuiti*.

---

## 📋 Cosa fa

- **Autenticazione** sicura tramite JWT, con rate limiting anti brute-force sul login
- **Report giornalieri e mensili** aggregati dai dati grezzi dei sensori
- **Rilevamento guasti**: identifica fermi impianto raggruppando i giorni consecutivi a produzione nulla
- **Analisi**: impatto del vento, previsione produzione (media mobile esponenziale), ore di picco
- **Report economico ed ecologico**: valore in € e CO₂ risparmiata
- **Analisi AI**: interrogazione di un LLM (Groq) per valutazioni tecnico-narrative sui dati
- **Resilienza**: Circuit Breaker a protezione della dipendenza esterna (Groq)

---

## 🛠️ Stack Tecnologico

| Componente | Tecnologia |
|------------|------------|
| Backend | Java 21 + Spring Boot 3.5.8 |
| Sicurezza | Spring Security + JWT (JJWT 0.11.5, HS256) |
| Persistenza | JPA/Hibernate + PostgreSQL 16 (Docker) |
| Database (test) | H2 in-memory |
| LLM | Groq API (endpoint OpenAI-compatible) |
| Cache | Spring Cache (`@Cacheable`) |
| Documentazione API | Springdoc OpenAPI / Swagger UI |
| Frontend | Dashboard HTML/CSS/JS + Chart.js |
| Parsing CSV | OpenCSV 5.9 |
| Boilerplate | Lombok |
| Containerizzazione | Docker + Docker Compose |

---

## 📁 Struttura del Progetto

```
src/main/java/com/example/ISDProject/
├── controller/        # Strato REST
│   ├── AuthController        # Login + emissione token JWT
│   └── EnergyController      # Endpoint analitici (+ Idempotent Receiver)
├── service/           # Logica di business
│   ├── EnergyService         # Analisi, report, rilevamento guasti
│   └── LlmService            # Proxy verso la Groq API
├── security/          # Autenticazione e autorizzazione
│   ├── SecurityConfig        # Regole Spring Security
│   ├── JwtFilter             # Protection Proxy / Reference Monitor
│   ├── JwtUtil               # Generazione/validazione token (HS256)
│   ├── SecurityConstants     # Fonte unica dei path pubblici
│   └── LoginRateLimiter      # Rate limiting anti brute-force
├── resilience/        # Resilienza
│   └── CircuitBreaker        # Macchina a stati CLOSED/OPEN/HALF_OPEN
├── session/           # Stato di sessione
│   └── UserSession           # Filtro temporale (@SessionScope)
├── repository/        # Accesso ai dati
│   └── RenewableRepository   # JpaRepository
├── model/             # Entità JPA
│   └── RenewableData         # Una misurazione oraria
├── dto/               # Data Transfer Object
│   ├── DailyReportDTO
│   ├── BatchInsightsDTO
│   └── MonthlySummaryDTO
├── config/            # Bootstrap
│   └── DataLoader            # Import CSV all'avvio
└── IsdProjectHeliosApplication.java

src/main/resources/
├── application.properties    # Configurazione (override via env)
└── static/                   # Dashboard SPA (index.html, app.js, styles.css)
```

---

## 🚀 Avvio Rapido

### Con Docker (consigliato)

Avvia PostgreSQL + backend con un comando:

```bash
docker compose up --build
```

I segreti (`GROQ_API_KEY`, `JWT_SECRET`) vengono letti automaticamente dal file `.env` nella root
(vedi sezione [Configurazione](#-configurazione)). Poi apri:

```
http://localhost:8080/
```

### Senza Docker

Serve un PostgreSQL raggiungibile su `localhost:5432` (db `heliosdb`, utente/pass `admin`/`admin`),
poi:

```bash
# bash
GROQ_API_KEY=gsk_... ./mvnw spring-boot:run

# PowerShell
$env:GROQ_API_KEY="gsk_..."; ./mvnw spring-boot:run
```

**Credenziali di accesso:** `admin` / `password`

Al primo avvio, `DataLoader` importa automaticamente `data.csv` (formato data `dd.MM.yyyy-HH:mm`,
8 colonne). I riavvii successivi saltano l'import se il database è già popolato.

---

## 📡 API Endpoints

Tutti gli endpoint sotto `/api/energy/**` richiedono l'header `Authorization: Bearer <token>`.

### Autenticazione

| Metodo | Endpoint | Descrizione |
|--------|----------|-------------|
| POST | `/api/auth/login` | Verifica credenziali, restituisce un token JWT. Rate-limited (5 tentativi/min per IP → 429). |

### Energia

| Metodo | Endpoint | Descrizione |
|--------|----------|-------------|
| POST | `/api/energy/session/filter` | Imposta il filtro temporale di sessione (`start`, `end` in `YYYY-MM-DD`). Supporta header `Idempotency-Key`. |
| GET | `/api/energy/daily-report-session` | Report giornaliero aggregato (usa il filtro di sessione). |
| GET | `/api/energy/batch-suggestions` | Insights batch: giorno migliore/peggiore, totale, fermi impianto. *(cached)* |
| GET | `/api/energy/monthly-summary` | Riepilogo mensile: produzione totale, media giornaliera, temperatura. |
| GET | `/api/energy/wind-impact` | Impatto del vento sulla produzione (`start`/`end` opzionali). |
| GET | `/api/energy/forecast` | Previsione con media mobile esponenziale + indicatore di tendenza. |
| GET | `/api/energy/financial-report` | Valore economico (€) e CO₂ risparmiata. |
| GET | `/api/energy/peak-hours` | Fascia oraria di massima produzione media. *(cached)* |
| GET | `/api/energy/smart-analysis` | Analisi AI dei dati tramite Groq LLM. |

Documentazione interattiva: **Swagger UI** su `/swagger-ui.html`.

---

## 🎨 Design Pattern Implementati

| Pattern | Dove | Scopo |
|---------|------|-------|
| **Remote Facade** | `EnergyController` / `EnergyService` | API a grana grossa, meno round-trip |
| **DTO** | `dto/` | Disaccoppiare API e schema DB |
| **Server Session State** | `UserSession` (`@SessionScope`) | Filtro temporale persistente per sessione |
| **Request Batch** | `/batch-suggestions` | Più analisi in una sola chiamata |
| **Remote Proxy** | `LlmService` | Nascondere la rete verso la Groq API |
| **Protection Proxy** + **Reference Monitor** | `JwtFilter` + `SecurityConfig` | Punto di controllo unico e obbligatorio |
| **Token Authentication** | `JwtUtil` (JWT, HS256) | Autenticazione stateless |
| **Rate Limiting** | `LoginRateLimiter` | Difesa brute-force / DoS sul login |
| **Circuit Breaker** | `CircuitBreaker` (`resilience/`) | Resilienza verso il servizio AI |
| **Idempotent Receiver** | `/session/filter` | Retry sicuri senza effetti doppi |

---

## 🔐 Sicurezza

- **Autenticazione stateless** via JWT firmato con HMAC-SHA256, scadenza 24h.
- La **chiave di firma** è derivata da `JWT_SECRET` (configurabile), così i token restano validi
  tra riavvii e tra istanze.
- `JwtFilter` valida ogni richiesta protetta e popola il `SecurityContext`; i path pubblici sono
  definiti una sola volta in `SecurityConstants`.
- **Rate limiting** sul login: 5 tentativi falliti per IP in 60 secondi → HTTP 429.
- **Credenziali** hardcoded per scopo didattico (`admin`/`password`): in produzione vanno sostituite
  con un provider reale.

---

## ⚙️ Configurazione

File: `src/main/resources/application.properties`. Tutti i valori sono sovrascrivibili da variabili
d'ambiente.

| Variabile | Descrizione | Default |
|-----------|-------------|---------|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Connessione PostgreSQL | `jdbc:postgresql://localhost:5432/heliosdb`, `admin`/`admin` |
| `GROQ_API_KEY` | Chiave Groq per `/smart-analysis`. Senza chiave l'endpoint restituisce un avviso. | *(vuoto)* |
| `JWT_SECRET` | Secret di firma JWT (min. 32 caratteri). **Impostare un valore forte in produzione.** | placeholder |
| `circuitbreaker.failure-threshold` | Fallimenti consecutivi prima di aprire il circuito | `3` |
| `circuitbreaker.cooldown-ms` | Durata dello stato OPEN prima del tentativo di ripristino | `60000` |

### Gestione dei segreti

Le chiavi **non sono committate**. In locale, crea un file `.env` nella root (già in `.gitignore`):

```bash
GROQ_API_KEY=gsk_la_tua_chiave
JWT_SECRET=un-secret-forte-di-almeno-32-caratteri
```

Docker Compose lo legge automaticamente all'avvio.

---

## 🧪 Test

Esiste un profilo H2 in-memory dedicato
([src/test/resources/application.properties](src/test/resources/application.properties)), ma al
momento **non sono presenti classi di test** in `src/test/java`. Quando verranno aggiunte:

```bash
./mvnw test
```

---

## 🛠️ Build

```bash
./mvnw clean package            # build del jar -> target/ISDProjectHelios-0.0.1-SNAPSHOT.jar
./mvnw -DskipTests package      # build senza test (come fa il Dockerfile)
java -jar target/ISDProjectHelios-0.0.1-SNAPSHOT.jar
```

---

## 🐛 Troubleshooting

**Errore formato data** — usa il formato `YYYY-MM-DD` per i parametri `start`/`end`.

**Token non valido (401)** — verifica che l'header `Authorization: Bearer <token>` sia presente e
che il token non sia scaduto (validità 24h).

**`/smart-analysis` risponde con un avviso** — manca `GROQ_API_KEY`, oppure il Circuit Breaker è in
stato OPEN dopo ripetuti fallimenti (riprova dopo il cooldown).

**Database non disponibile** — verifica che il container `postgres` sia attivo (`docker compose ps`)
e che `SPRING_DATASOURCE_URL` punti all'host corretto (`localhost` in locale, `postgres` dentro Docker).

---

## 👤 Autore

Simone Battiato — Corso di *Ingegneria dei Sistemi Distribuiti*, A.A. 2025/2026.
