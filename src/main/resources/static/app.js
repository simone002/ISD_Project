const state = {
  token: localStorage.getItem("heliosToken") || "",
};

const authState = document.getElementById("authState");
const tokenState = document.getElementById("tokenState");
const output = document.getElementById("output");
const summaryCards = document.getElementById("summaryCards");

function setToken(token) {
  state.token = token || "";
  if (state.token) {
    localStorage.setItem("heliosToken", state.token);
  } else {
    localStorage.removeItem("heliosToken");
  }
  renderAuthState();
}

function renderAuthState() {
  const logged = Boolean(state.token);
  authState.textContent = logged ? "Autenticato" : "Non autenticato";
  authState.className = logged ? "status-good" : "status-warn";
  tokenState.textContent = logged ? "token attivo" : "token assente";
}

function formatValue(value) {
  if (value === null || value === undefined) return "-";
  if (typeof value === "number") return Number.isInteger(value) ? value.toString() : value.toFixed(2);
  if (typeof value === "string") return value;
  return JSON.stringify(value, null, 2);
}

function renderOutput(content) {
  output.classList.remove("empty");
  if (typeof content === "string") {
    output.textContent = content;
    summaryCards.innerHTML = "";
    return;
  }

  if (Array.isArray(content)) {
    if (content.length === 0) {
      output.textContent = "Nessun dato disponibile.";
      summaryCards.innerHTML = "";
      return;
    }

    output.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              ${Object.keys(content[0]).map((key) => `<th>${key}</th>`).join("")}
            </tr>
          </thead>
          <tbody>
            ${content.map((row) => `
              <tr>
                ${Object.values(row).map((value) => `<td>${formatValue(value)}</td>`).join("")}
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    `;
    summaryCards.innerHTML = "";
    return;
  }

  if (content && typeof content === "object") {
    summaryCards.innerHTML = Object.entries(content)
      .slice(0, 3)
      .map(([key, value]) => `
        <div class="summary-card">
          <span class="label">${key}</span>
          <strong>${formatValue(value)}</strong>
        </div>
      `)
      .join("");

    output.textContent = JSON.stringify(content, null, 2);
    return;
  }

  output.textContent = String(content);
}

async function apiFetch(endpoint, options = {}) {
  const headers = new Headers(options.headers || {});
  if (!options.body) {
    headers.set("Accept", "application/json, text/plain, */*");
  }

  if (state.token) {
    headers.set("Authorization", `Bearer ${state.token}`);
  }

  const response = await fetch(endpoint, {
    ...options,
    headers,
  });

  const contentType = response.headers.get("content-type") || "";
  const text = await response.text();

  if (!response.ok) {
    throw new Error(text || `HTTP ${response.status}`);
  }

  if (contentType.includes("application/json")) {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function login() {
  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value;
  const body = new URLSearchParams({ username, password });

  const data = await apiFetch("/api/auth/login", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  if (data && data.token) {
    setToken(data.token);
    renderOutput("Login riuscito. Token salvato nel browser.");
  } else {
    throw new Error("Token non ricevuto dal server.");
  }
}

async function setFilter() {
  const start = document.getElementById("startDate").value;
  const end = document.getElementById("endDate").value;

  if (!start || !end) {
    throw new Error("Seleziona entrambe le date.");
  }

  const data = await apiFetch(`/api/energy/session/filter?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`, {
    method: "POST",
  });

  renderOutput(data);
}

async function runAction(action) {
  const routes = {
    daily: "/api/energy/daily-report-session",
    batch: "/api/energy/batch-suggestions",
    wind: "/api/energy/wind-impact",
    forecast: "/api/energy/forecast",
    financial: "/api/energy/financial-report",
    peak: "/api/energy/peak-hours",
    smart: "/api/energy/smart-analysis",
  };

  const endpoint = routes[action];
  const start = document.getElementById("startDate").value;
  const end = document.getElementById("endDate").value;
  const hasRange = start && end;
  const finalEndpoint = (action === "smart" || action === "wind" || action === "forecast" || action === "financial") && hasRange
    ? `${endpoint}?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`
    : endpoint;

  const data = await apiFetch(finalEndpoint);
  renderOutput(data);
}

document.getElementById("loginBtn").addEventListener("click", async () => {
  try {
    await login();
  } catch (error) {
    renderOutput(`Login fallito: ${error.message}`);
  }
});

document.getElementById("filterBtn").addEventListener("click", async () => {
  try {
    await setFilter();
  } catch (error) {
    renderOutput(`Filtro non salvato: ${error.message}`);
  }
});

document.getElementById("refreshBtn").addEventListener("click", async () => {
  try {
    await runAction("daily");
  } catch (error) {
    renderOutput(`Aggiornamento fallito: ${error.message}`);
  }
});

document.querySelectorAll("[data-action]").forEach((button) => {
  button.addEventListener("click", async () => {
    try {
      await runAction(button.dataset.action);
    } catch (error) {
      renderOutput(`Errore richiesta: ${error.message}`);
    }
  });
});

renderAuthState();

if (state.token) {
  renderOutput("Token recuperato dal browser. Puoi lanciare le API protette.");
}