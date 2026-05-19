# Come Avviare ISDProjectHelios

## Opzione 1: Backend + Postgres (senza AI/Ollama)

La soluzione più semplice. L'app gira, i dati funzionano, ma la feature AI torna offline.

```powershell
docker compose up --build
```

Poi accedi a: **http://localhost:8080/**

Credenziali:
- Username: `admin`
- Password: `password`

## Opzione 2: Backend + Postgres + Ollama (con AI)

Se vuoi la feature AI completa con analisi intelligenti.

**Primo avvio (scarica il modello ~4GB)**:

```powershell
docker compose -f docker-compose.yml -f docker-compose.ollama.yml up --build
```

Aspetta che l'output mostri `ollama | # Load model`. Poi dai CTRL+C e rilancia:

```powershell
docker compose -f docker-compose.yml -f docker-compose.ollama.yml pull ollama
docker compose -f docker-compose.yml -f docker-compose.ollama.yml run ollama ollama pull llama3.2
docker compose -f docker-compose.yml -f docker-compose.ollama.yml up
```

Poi accedi a: **http://localhost:8080/**

## Opzione 3: Backend in locale + Postgres in Docker

Se preferisci debuggare il backend localmente:

```powershell
# Primo terminal: accendi solo Postgres
docker compose up postgres

# Secondo terminal: accendi il backend
.\mvnw.cmd spring-boot:run

# E anche Ollama se lo vuoi (terzo terminal):
ollama serve
```

Poi accedi a: **http://localhost:8080/**

---

## Comandi utili

Fermare tutto:
```powershell
docker compose down
```

Fermare e pulire volumi (azzera il database):
```powershell
docker compose down -v
```

Vedere i log in tempo reale:
```powershell
docker compose logs -f app
docker compose logs -f postgres
docker compose logs -f ollama
```

Entrare in un container:
```powershell
docker compose exec postgres psql -U admin -d heliosdb
docker compose exec app bash
```

---

## Troubleshooting

**"Connection refused" da Ollama**: Normale se non lo hai aggiunto al compose. L'app funziona comunque in offline mode.

**Database "heliosdb" non esiste**: Il compose lo crea automaticamente. Se persiste, elimina i volumi: `docker compose down -v`

**Frontend bianco/non carica**: Attendi 5-10 secondi dal boot del container, poi ricarica il browser.

**Porta 8080 già in uso**: Cambia nel docker-compose a `8081:8080` e accedi a `http://localhost:8081/`
