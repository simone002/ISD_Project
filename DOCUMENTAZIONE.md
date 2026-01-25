# 📘 Documentazione HELIOS - Sistema di Gestione Energia Rinnovabile

## 🎯 Panoramica Progetto

**HELIOS** è un'applicazione enterprise Spring Boot per il monitoraggio e l'analisi intelligente di dati energetici provenienti da fonti rinnovabili (pannelli solari, turbine eoliche, ecc.). Il sistema offre analisi in tempo reale, report personalizzati e insight basati su AI tramite integrazione con modelli di linguaggio naturale (LLM).

---

## 🏗️ Architettura

### Stack Tecnologico

- **Framework**: Spring Boot 3.5.8
- **Linguaggio**: Java 21
- **Database**: H2 (in-memory)
- **ORM**: Spring Data JPA
- **Security**: JWT (JSON Web Token) - Spring Security
- **AI/LLM**: Ollama (modello llama3.2)
- **Build Tool**: Maven
- **Librerie Principali**:
  - Lombok (riduzione boilerplate)
  - OpenCSV (lettura dati CSV)
  - JJWT (gestione token JWT)

### Pattern Architetturale

L'applicazione segue il pattern **MVC** (Model-View-Controller) con una chiara separazione dei livelli:

```
┌─────────────────────────────────────────┐
│  CLIENT (HeliosClient.java - CLI)      │
└───────────────┬─────────────────────────┘
                │ HTTP/REST
                ▼
┌─────────────────────────────────────────┐
│  CONTROLLER LAYER                       │
│  - AuthController (autenticazione)      │
│  - EnergyController (business logic)    │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  SERVICE LAYER                          │
│  - LlmService (AI integration)          │
│  - UserSession (gestione sessione)      │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  REPOSITORY LAYER (JPA)                 │
│  - RenewableRepository                  │
└───────────────┬─────────────────────────┘
                │
                ▼
┌─────────────────────────────────────────┐
│  DATABASE (H2)                          │
│  - renewable_data (tabella)             │
└─────────────────────────────────────────┘
```

---

## 🔐 Sistema di Sicurezza

### Autenticazione JWT

L'applicazione implementa un sistema di autenticazione basato su **JSON Web Token**:

1. **Login** (`/api/auth/login`):
   - Credenziali: `username=admin`, `password=password`
   - Risposta: token JWT con scadenza configurabile
   
2. **Protezione Endpoint**:
   - Filtro `JwtFilter` intercetta ogni richiesta
   - Valida il token nel header `Authorization: Bearer <token>`
   - Estrae e autentica l'utente

3. **Configurazione Sicurezza**:
   - Endpoint pubblici: `/api/auth/**`
   - Endpoint protetti: `/api/energy/**` (richiedono autenticazione)

### Componenti Security

- **JwtUtil**: Generazione e validazione token
- **JwtFilter**: Intercettazione richieste HTTP
- **SecurityConfig**: Configurazione Spring Security

---

## 📊 Modello Dati

### Entità `RenewableData`

Rappresenta un record di produzione energetica con parametri ambientali:

```java
{
  "id": Long,                        // ID univoco
  "dateTime": LocalDateTime,         // Timestamp misurazione
  "windSpeed": Double,               // Velocità vento (m/s)
  "sunshine": Integer,               // Ore di sole
  "airPressure": Double,             // Pressione atmosferica (hPa)
  "radiation": Double,               // Radiazione solare (W/m²)
  "airTemperature": Double,          // Temperatura (°C)
  "relativeAirHumidity": Integer,    // Umidità relativa (%)
  "systemProduction": Double         // Produzione sistema (kWh)
}
```

### Caricamento Dati

Il componente **DataLoader** importa automaticamente i dati da `data.csv` all'avvio dell'applicazione, popolando il database H2.

---

## 🔌 API REST - Endpoints

### 🔓 Autenticazione

#### `POST /api/auth/login`
**Descrizione**: Effettua il login e ottiene un token JWT.

**Parametri**:
- `username` (form-data): Nome utente
- `password` (form-data): Password

**Risposta**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 🔒 Gestione Energia (Autenticazione Richiesta)

#### `POST /api/energy/session/filter`
**Descrizione**: Imposta un filtro temporale per la sessione utente.

**Parametri**:
- `start`: Data inizio (formato `YYYY-MM-DD`)
- `end`: Data fine (formato `YYYY-MM-DD`)

**Esempio**: `?start=2017-01-01&end=2017-01-31`

---

#### `GET /api/energy/daily-report-session`
**Descrizione**: Report giornaliero con aggregazione e status performance.

**Risposta**:
```json
[
  {
    "date": "2017-01-15",
    "totalProduction": 1524.78,
    "avgTemperature": 15.3,
    "status": "NORMAL"  // HIGH_PERFORMANCE | NORMAL | LOW_GENERATION
  }
]
```

**Logica Status**:
- `HIGH_PERFORMANCE`: Produzione > 2000 kWh
- `LOW_GENERATION`: Produzione < 500 kWh
- `NORMAL`: Produzione tra 500-2000 kWh

---

#### `GET /api/energy/batch-suggestions`
**Descrizione**: Analisi batch con best/worst day e rilevamento anomalie.

**Risposta**:
```json
{
  "bestDay": "2017-06-21",
  "bestProduction": 3245.67,
  "worstDay": "2017-12-05",
  "worstProduction": 89.23,
  "anomalyMessage": "ALERT: Il giorno 2017-03-15 c'era sole (Rad: 650) ma produzione quasi nulla (25). Controllare guasti.",
  "totalProduction": 456789.12
}
```

**Sistema Rilevamento Anomalie**:
Identifica giorni con alta radiazione solare ma bassa produzione (possibili guasti).

---

#### `GET /api/energy/wind-impact`
**Descrizione**: Analizza l'impatto del vento sulla produzione.

**Parametri Opzionali**:
- `start`: Data inizio filtro
- `end`: Data fine filtro

**Risposta**:
```
ANALISI VENTO (1458 dati analizzati):
- Prod. Media Vento Forte: 1234.56 kWh
- Prod. Media Vento Debole: 987.65 kWh
Differenza di resa: 25.0%
```

**Classificazione Vento**:
- Vento Forte: `windSpeed > 4.0 m/s`
- Vento Debole: `windSpeed < 2.0 m/s`

---

#### `GET /api/energy/forecast`
**Descrizione**: Previsione produzione basata su media storica.

**Parametri Opzionali**:
- `start`, `end`: Periodo di analisi

**Risposta**:
```
SIMULAZIONE PREVISIONE:
Basandosi sul periodo selezionato (365 record utili),
la produzione giornaliera attesa è: 1542.34 kWh
```

---

#### `GET /api/energy/financial-report`
**Descrizione**: Report economico e ambientale della produzione.

**Risposta**:
```
REPORT ECONOMICO & ECOLOGICO:
- Energia Totale Prodotta: 45678.90 kWh
- Valore Economico Generato: 9135.78 € (a 0.20 €/kWh)
- CO2 Risparmiata: 18271.56 kg
```

**Parametri di Calcolo**:
- Prezzo energia: `€0.20/kWh`
- CO2 risparmiata: `0.4 kg/kWh`

---

#### `GET /api/energy/peak-hours`
**Descrizione**: Identifica le ore di picco produttivo.

**Risposta**:
```
ANALISI ORARIA:
- Ora di Picco Assoluta: 13:00
- Produzione Media in quell'ora: 2345.67 kWh
Consiglio: Programmare i macchinari intorno alle 13:00.
```

---

#### `GET /api/energy/smart-analysis` 🤖
**Descrizione**: Analisi intelligente tramite AI (Ollama/Llama3.2).

**Parametri Opzionali**:
- `start`, `end`: Periodo da analizzare (default: ultimi 3 record)

**Funzionamento**:
1. Estrae fino a 15 record nel periodo specificato
2. Costruisce un prompt strutturato con dati energetici
3. Invia il prompt a Ollama (modello llama3.2)
4. Restituisce l'analisi generata dall'AI

**Esempio Prompt Generato**:
```
Sei un ingegnere energetico esperto. Analizza questi dati brevemente e dai un consiglio tecnico in italiano:
- Data: 2017-06-15, Prod: 2345.67, Radiazione: 850.0, Temp: 28.5, Vento: 3.2
- Data: 2017-06-16, Prod: 2198.34, Radiazione: 820.0, Temp: 27.8, Vento: 4.1
...
```

**Prerequisito**: Ollama deve essere attivo (`ollama serve`) con il modello llama3.2.

---

## 💻 Client Helios (CLI)

### Avvio

```bash
java -cp target/classes com.example.ISDProject.client.HeliosClient
```

### Menu Interattivo

```
MENU PRINCIPALE:
0. LOGIN (Richiesto!)
1. Report Semplice
2. Analisi Batch
3. Imposta Filtro (Sessione)
4. Vedi Filtro
5. Analisi Vento
6. Previsione
7. Report Economico
8. Ore di Picco
9. CHIEDI ALL'IA (Ollama)
q. Esci
```

### Caratteristiche

- **Gestione JWT**: Salva e invia automaticamente il token in ogni richiesta
- **Gestione Cookie**: Supporto per cookie di sessione
- **Interfaccia User-Friendly**: Menu numerato con messaggi chiari
- **Validazione**: Controlla lo stato di autenticazione prima delle operazioni protette

---

## 🔄 Flusso di Utilizzo Tipico

1. **Avvio Server**:
   ```bash
   mvn spring-boot:run
   ```

2. **Avvio Client CLI**:
   ```bash
   java -cp target/classes com.example.ISDProject.client.HeliosClient
   ```

3. **Autenticazione**:
   - Seleziona opzione `0`
   - Inserisci `admin` / `password`
   - Token salvato automaticamente

4. **Impostazione Filtro Temporale** (opzionale):
   - Seleziona opzione `3`
   - Inserisci range date (es. `2017-01-01` - `2017-12-31`)

5. **Analisi Dati**:
   - Report giornaliero (opzione `1`)
   - Analisi batch (opzione `2`)
   - Analisi AI (opzione `9`)

---

## 🧪 Testing

L'applicazione include test unitari per i controller principali:

- `AuthControllerTest`: Test autenticazione
- `EnergyControllerTest`: Test endpoints energetici
- `IsdProjectHeliosApplicationTests`: Test contesto applicazione

**Esecuzione Test**:
```bash
mvn test
```

---

## 🚀 Deployment

### Build Applicazione

```bash
mvn clean package
```

### Esecuzione JAR

```bash
java -jar target/ISDProjectHelios-0.0.1-SNAPSHOT.jar
```

### Configurazione Database

Modifica [application.properties](src/main/resources/application.properties):

```properties
# H2 In-Memory (default)
spring.datasource.url=jdbc:h2:mem:heliosdb

# H2 Persistente (esempio)
# spring.datasource.url=jdbc:h2:file:./data/heliosdb

# MySQL (esempio)
# spring.datasource.url=jdbc:mysql://localhost:3306/helios
# spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

---

## 🔧 Configurazione Avanzata

### JWT Personalizzazione

Modifica [JwtUtil.java](src/main/java/com/example/ISDProject/security/JwtUtil.java):

```java
private static final long EXPIRATION_TIME = 86400000; // 24 ore (default)
private static final String SECRET_KEY = "your-secret-key";
```

### Integrazione Ollama

Modifica [LlmService.java](src/main/java/com/example/ISDProject/service/LlmService.java):

```java
private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
private static final String MODEL_NAME = "llama3.2"; // o altro modello
```

**Modelli Compatibili**:
- `llama3.2` (default)
- `llama3.1`
- `mistral`
- `codellama`

---

## 📂 Struttura Pacchetti

```
com.example.ISDProject
├── client/               # Client CLI
│   └── HeliosClient.java
├── controller/           # REST Controllers
│   ├── AuthController.java
│   └── EnergyController.java
├── dto/                  # Data Transfer Objects
│   ├── BatchInsightsDTO.java
│   └── DailyReportDTO.java
├── model/                # JPA Entities
│   └── RenewableData.java
├── repository/           # Data Access Layer
│   └── RenewableRepository.java
├── security/             # Security Components
│   ├── JwtFilter.java
│   ├── JwtUtil.java
│   └── SecurityConfig.java
├── service/              # Business Logic
│   ├── LlmService.java
│   └── UserSession.java
└── util/                 # Utilities
    └── DataLoader.java
```

---

## 🛠️ Dipendenze Maven

| Dipendenza | Versione | Scopo |
|-----------|----------|-------|
| Spring Boot Starter Web | 3.5.8 | REST API |
| Spring Boot Starter Data JPA | 3.5.8 | ORM |
| Spring Boot Starter Security | 3.5.8 | Sicurezza |
| H2 Database | runtime | Database in-memory |
| Lombok | 1.18.x | Riduzione boilerplate |
| JJWT | 0.11.5 | JWT token |
| OpenCSV | 5.9 | Parsing CSV |

---

## 📈 Funzionalità Chiave

### 1. **Gestione Sessione Utente**
- Filtri temporali persistenti nella sessione HTTP
- Cookie-based session management

### 2. **Analytics Avanzate**
- Aggregazioni giornaliere
- Rilevamento anomalie produttive
- Correlazioni ambientali (vento, temperatura)

### 3. **AI-Powered Insights**
- Integrazione con LLM open-source
- Prompt engineering ottimizzato per dati energetici
- Risposte in linguaggio naturale (italiano)

### 4. **Sicurezza Enterprise**
- Autenticazione stateless (JWT)
- Token-based authorization
- CORS configurabile

### 5. **Report Multipli**
- Report giornalieri
- Analisi batch
- Previsioni
- Report economici/ambientali
- Analisi orarie

---

## 🐛 Troubleshooting

### Problema: "Login Fallito"
**Soluzione**: Verifica credenziali (`admin`/`password`) e che il server sia attivo.

### Problema: "Errore di connessione con Ollama"
**Soluzione**: 
1. Verifica Ollama sia in esecuzione: `ollama serve`
2. Scarica il modello: `ollama pull llama3.2`

### Problema: "Nessun dato nel periodo selezionato"
**Soluzione**: Controlla che `data.csv` sia presente e popolato, oppure modifica il range date.

### Problema: Token scaduto
**Soluzione**: Rieffettua il login (opzione `0` nel client).

---

## 📝 Note di Sviluppo

- **Database**: H2 in-memory si resetta ad ogni riavvio. Usa H2 file-based per persistenza.
- **CORS**: Configurato per consentire tutte le origini in sviluppo (modificare in produzione).
- **Console H2**: Accessibile su `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:heliosdb`).

---

## 🎓 Casi d'Uso

### Caso 1: Analisi Periodo Specifico
```
1. Login
2. Imposta filtro: 2017-06-01 - 2017-06-30
3. Richiedi report giornaliero
4. Analizza con AI
```

### Caso 2: Rilevamento Guasti
```
1. Login
2. Richiedi analisi batch
3. Controlla campo "anomalyMessage"
4. Verifica giorni con alta radiazione/bassa produzione
```

### Caso 3: Ottimizzazione Consumi
```
1. Login
2. Richiedi analisi ore di picco
3. Pianifica operazioni energy-intensive negli orari suggeriti
```

---

## 📞 Supporto

Per domande o problemi:
- **Email**: [inserire email]
- **Repository**: [inserire URL GitHub]
- **Documentazione API**: Swagger (se configurato)

---

## 📜 Licenza

[Specificare licenza del progetto]

---

**Versione Documentazione**: 1.0  
**Ultimo Aggiornamento**: Gennaio 2026  
**Compatibilità**: Java 21, Spring Boot 3.5.8
