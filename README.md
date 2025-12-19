# ISDProjectHelios

**ISDProjectHelios** è un'applicazione Spring Boot per l'analisi e la gestione dei dati di energia rinnovabile. L'applicazione fornisce API RESTful per monitorare, filtrare e generare report su dati energetici da fonti rinnovabili.

---

## 📋 Descrizione del Progetto

Il progetto è un sistema di gestione dati energetici che consente di:
- **Autenticazione**: Accesso sicuro tramite JWT (JSON Web Tokens)
- **Gestione Dati**: Caricamento e memorizzazione dati di energia rinnovabile
- **Analisi**: Report giornalieri e insights batch sui dati energetici
- **Filtri Sessione**: Filtraggio dati per intervalli di date specifici
- **Integrazione LLM**: Analisi avanzata tramite Large Language Models

---

## 🛠️ Tecnologie Utilizzate

- **Java**: 21
- **Spring Boot**: 3.5.8
- **Build Tool**: Maven
- **Database**: H2 (in-memory)
- **ORM**: JPA/Hibernate
- **Sicurezza**: Spring Security + JWT (JJWT 0.11.5)
- **Parsing CSV**: OpenCSV 5.9
- **Lombok**: Per ridurre boilerplate

### Dipendenze Principali

```xml
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-security
- spring-boot-starter-test
- jjwt (jsonwebtoken)
- h2database
- opencsv
- lombok
```

---

## 📁 Struttura del Progetto

```
ISDProject/
├── src/
│   ├── main/
│   │   ├── java/com/example/ISDProject/
│   │   │   ├── controller/          # API REST endpoints
│   │   │   │   ├── AuthController   # Autenticazione
│   │   │   │   └── EnergyController # Gestione dati energetici
│   │   │   ├── service/             # Logica di business
│   │   │   │   ├── LlmService       # Integrazione LLM
│   │   │   │   └── UserSession      # Gestione sessione utente
│   │   │   ├── model/               # Entità JPA
│   │   │   │   └── RenewableData    # Dati energia rinnovabile
│   │   │   ├── repository/          # Data access layer
│   │   │   │   └── RenewableRepository
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   │   ├── BatchInsightsDTO
│   │   │   │   └── DailyReportDTO
│   │   │   ├── security/            # Configurazione sicurezza
│   │   │   │   ├── SecurityConfig   # Configurazione Spring Security
│   │   │   │   ├── JwtUtil          # Utilità JWT
│   │   │   │   └── JwtFilter        # Filtro autenticazione
│   │   │   ├── client/              # Client HTTP
│   │   │   │   └── HeliosClient     # Client per servizi Helios
│   │   │   ├── util/                # Utilità
│   │   │   │   └── DataLoader       # Caricamento dati
│   │   │   └── IsdProjectHeliosApplication.java  # Main application
│   │   └── resources/
│   │       └── application.properties # Configurazione
│   └── test/                         # Test unitari
├── pom.xml                           # Configurazione Maven
├── data.csv                          # File dati di esempio
└── README.md                         # Questo file
```

---

## 🚀 Avvio Rapido

### Prerequisiti
- Java 21 JDK installato
- Maven 3.6+ installato

### Installazione

1. **Clonare il repository** (se disponibile su Git):
   ```bash
   git clone <repository-url>
   cd ISDProject
   ```

2. **Compilare il progetto**:
   ```bash
   mvn clean install
   ```

3. **Avviare l'applicazione**:
   ```bash
   mvn spring-boot:run
   ```

L'applicazione sarà disponibile su `http://localhost:8080`

---

## 📡 API Endpoints

### Autenticazione

#### Login
- **Endpoint**: `POST /api/auth/login`
- **Parametri**:
  - `username` (String): Nome utente (default: "admin")
  - `password` (String): Password (default: "password")
- **Risposta**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```

### Gestione Energia

#### Impostare Filtro Sessione
- **Endpoint**: `POST /api/energy/session/filter`
- **Parametri**:
  - `start` (String): Data inizio (formato: YYYY-MM-DD)
  - `end` (String): Data fine (formato: YYYY-MM-DD)
- **Esempio**: `/api/energy/session/filter?start=2017-01-01&end=2017-01-31`

#### Report Giornaliero Sessione
- **Endpoint**: `GET /api/energy/daily-report-session`
- **Descrizione**: Genera report giornaliero per l'intervallo di date impostato nella sessione

#### Insights Batch
- **Endpoint**: `GET /api/energy/batch-insights`
- **Descrizione**: Restituisce analisi batch e insights sui dati energetici

---

## 🔐 Sicurezza

- **Autenticazione**: JWT Token-based
- **Autorizzazione**: Spring Security con filtri JWT
- **Validazione Token**: Verifica firma e scadenza token
- **Credenziali Default**:
  - Username: `admin`
  - Password: `password`

⚠️ **Nota**: In produzione, cambiare le credenziali e configurare un provider di autenticazione più robusto.

---

## 💾 Database

L'applicazione utilizza **H2 Database** (in-memory) per lo sviluppo e il testing.

### Configurazione
```properties
spring.datasource.url=jdbc:h2:mem:heliosdb
spring.datasource.driverClassName=org.h2.Driver
spring.h2.console.enabled=true
```

### Console H2
Accedere a: `http://localhost:8080/h2-console`

---

## 🧪 Test

Per eseguire i test unitari:

```bash
mvn test
```

I test coprono:
- `AuthControllerTest`: Test autenticazione e JWT
- `EnergyControllerTest`: Test gestione dati energetici
- `IsdProjectHeliosApplicationTests`: Test avvio applicazione

---

## 📊 Caricamento Dati

Il progetto include un loader di dati (`DataLoader`) che carica automaticamente i dati dal file `data.csv` al startup dell'applicazione.

Il file `data.csv` deve contenere i dati di energia rinnovabile con la seguente struttura:
```csv
timestamp,source,power_output,efficiency
```


## 📝 Configurazione

File: `src/main/resources/application.properties`

```properties
spring.application.name=ISDProjectHelios
spring.datasource.url=jdbc:h2:mem:heliosdb
spring.jpa.show-sql=true
spring.jackson.date-format=yyyy-MM-dd HH:mm:ss
spring.jackson.time-zone=UTC
```

---

## 🛠️ Build e Deployment

### Build JAR
```bash
mvn clean package
```

L'eseguibile JAR sarà in: `target/ISDProjectHelios-0.0.1-SNAPSHOT.jar`

### Esecuzione JAR
```bash
java -jar target/ISDProjectHelios-0.0.1-SNAPSHOT.jar
```

---

## 📚 Moduli Principali

### Service - LlmService
Gestisce l'integrazione con Large Language Models per analisi avanzate dei dati energetici.

### Service - UserSession
Mantiene lo stato della sessione utente, inclusi i filtri di data attivi.

### Model - RenewableData
Entità JPA che rappresenta i record di dati energetici rinnovabili.

### Security - JwtUtil
Utilità per generazione e validazione token JWT.

---

## 🐛 Troubleshooting

### Errore di formato data
Se ricevi l'errore "Errore formato data", assicurati di usare il formato **YYYY-MM-DD**.

### Token non valido
Verifica che:
- Il token sia stato incluso nell'header Authorization
- Il token non sia scaduto
- Il token sia stato generato con le credenziali corrette

### Database H2 non disponibile
Verifica che `spring.h2.console.enabled=true` in `application.properties`.

---

## 📄 Licenza

Questo progetto è distribuito sotto licenza non specificata. Consultare il file LICENSE per i dettagli.

---

## 👤 Autore

Progetto sviluppato come parte del corso di "Ingegneria dei Sistemi Distribuiti".

---

## 📞 Supporto

Per problemi o domande, contattare il team di sviluppo o consultare la documentazione ufficiale di Spring Boot.

---

## 🔗 Risorse Utili

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Security](https://spring.io/projects/spring-security)
- [JJWT - JWT Library](https://github.com/jwtk/jjwt)
- [H2 Database](http://www.h2database.com/)

---

**Ultimo aggiornamento**: Dicembre 2025