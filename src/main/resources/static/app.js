const state = {
  token: localStorage.getItem("heliosToken") || "",
  refreshToken: localStorage.getItem("heliosRefreshToken") || "",
  pagedData: [],
  currentPage: 0,
};

const PAGE_SIZE = 10;

const authState = document.getElementById("authState");
const tokenState = document.getElementById("tokenState");
const output = document.getElementById("output");
const summaryCards = document.getElementById("summaryCards");
const chartContainer = document.getElementById("chartContainer");
const chartCanvas = document.getElementById("chartCanvas");

let activeChart = null;

function setToken(token) {
  state.token = token || "";
  if (state.token) {
    localStorage.setItem("heliosToken", state.token);
  } else {
    localStorage.removeItem("heliosToken");
  }
  renderAuthState();
}

function setRefreshToken(refreshToken) {
  state.refreshToken = refreshToken || "";
  if (state.refreshToken) {
    localStorage.setItem("heliosRefreshToken", state.refreshToken);
  } else {
    localStorage.removeItem("heliosRefreshToken");
  }
}

// Tenta di rinnovare l'access token scaduto usando il refresh token (che viene ruotato).
async function tryRefresh() {
  if (!state.refreshToken) return false;
  try {
    const resp = await fetch("/api/auth/refresh", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ refreshToken: state.refreshToken }),
    });
    if (!resp.ok) return false;
    const data = await resp.json();
    if (data && data.token) {
      setToken(data.token);
      setRefreshToken(data.refreshToken);
      return true;
    }
  } catch {
    /* rete non disponibile: tratta come refresh fallito */
  }
  return false;
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

function hideChart() {
  chartContainer.style.display = "none";
  if (activeChart) {
    activeChart.destroy();
    activeChart = null;
  }
}

function renderChart(data) {
  const hasProduction = data.length > 0 && "totalProduction" in data[0];
  if (!hasProduction) { hideChart(); return; }

  const isMonthly = "month" in data[0];

  chartContainer.style.display = "block";
  if (activeChart) { activeChart.destroy(); }

  const labels = data.map((r) => r.month || r.date || r.Date || Object.values(r)[0]);
  const values = data.map((r) => r.totalProduction ?? r.TotalProduction ?? 0);

  activeChart = new Chart(chartCanvas, {
    type: isMonthly ? "bar" : "line",
    data: {
      labels,
      datasets: [{
        label: isMonthly ? "Produzione mensile (kWh)" : "Produzione giornaliera (kWh)",
        data: values,
        borderColor: "#76f7c5",
        backgroundColor: isMonthly ? "rgba(118,247,197,0.55)" : "rgba(118,247,197,0.08)",
        borderWidth: isMonthly ? 1 : 2,
        pointRadius: data.length > 60 ? 0 : 3,
        pointHoverRadius: 5,
        tension: 0.3,
        fill: !isMonthly,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: "#9fb0c8", font: { size: 12 } } },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${ctx.parsed.y.toFixed(2)} kWh`,
          },
        },
      },
      scales: {
        x: {
          ticks: {
            color: "#9fb0c8",
            maxTicksLimit: 12,
            maxRotation: 45,
          },
          grid: { color: "rgba(255,255,255,0.05)" },
        },
        y: {
          ticks: { color: "#9fb0c8" },
          grid: { color: "rgba(255,255,255,0.05)" },
          title: { display: true, text: "kWh", color: "#9fb0c8" },
        },
      },
    },
  });
}

function renderOutput(content) {
  output.classList.remove("empty");
  if (typeof content === "string") {
    output.textContent = content;
    summaryCards.innerHTML = "";
    hideChart();
    return;
  }

  if (Array.isArray(content)) {
    if (content.length === 0) {
      output.textContent = "Nessun dato disponibile.";
      summaryCards.innerHTML = "";
      hideChart();
      return;
    }

    state.pagedData = content;
    state.currentPage = 0;
    renderPage(0);
    renderChart(content);
    summaryCards.innerHTML = "";
    return;
  }

  if (content && typeof content === "object") {
    hideChart();
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

function renderPage(page) {
  const data = state.pagedData;
  const totalPages = Math.ceil(data.length / PAGE_SIZE);
  const start = page * PAGE_SIZE;
  const slice = data.slice(start, start + PAGE_SIZE);
  const headers = Object.keys(data[0]);

  output.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>${headers.map((k) => `<th>${k}</th>`).join("")}</tr>
        </thead>
        <tbody>
          ${slice.map((row) => `
            <tr>${Object.values(row).map((v) => `<td>${formatValue(v)}</td>`).join("")}</tr>
          `).join("")}
        </tbody>
      </table>
    </div>
    <div class="pagination">
      <button class="page-btn" onclick="goPage(${page - 1})" ${page === 0 ? "disabled" : ""}>&#8592; Prec</button>
      <span class="page-info">Pagina ${page + 1} di ${totalPages} &nbsp;·&nbsp; ${data.length} record totali</span>
      <button class="page-btn" onclick="goPage(${page + 1})" ${page >= totalPages - 1 ? "disabled" : ""}>Succ &#8594;</button>
    </div>
  `;
}

function goPage(page) {
  const totalPages = Math.ceil(state.pagedData.length / PAGE_SIZE);
  if (page < 0 || page >= totalPages) return;
  state.currentPage = page;
  renderPage(page);
}

async function apiFetch(endpoint, options = {}, isRetry = false) {
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

  // Access token scaduto: prova un refresh trasparente (una sola volta) e ripeti la richiesta.
  if (response.status === 401 && !isRetry && !endpoint.startsWith("/api/auth/")) {
    if (await tryRefresh()) {
      return apiFetch(endpoint, options, true);
    }
    setToken("");
    setRefreshToken(""); // refresh fallito: sessione scaduta, serve un nuovo login
  }

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
    setRefreshToken(data.refreshToken);
    renderOutput("Login riuscito. Token salvato nel browser.");
  } else {
    throw new Error("Token non ricevuto dal server.");
  }
}

async function logout() {
  const refreshToken = state.refreshToken;
  // Pulisce subito i token locali; poi avvisa il server di revocare il refresh token.
  setToken("");
  setRefreshToken("");
  if (refreshToken) {
    try {
      await fetch("/api/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ refreshToken }),
      });
    } catch {
      /* anche se la revoca lato server fallisce, i token locali sono già stati rimossi */
    }
  }
  renderOutput("Logout effettuato.");
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
    monthly: "/api/energy/monthly-summary",
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

document.getElementById("logoutBtn").addEventListener("click", async () => {
  try {
    await logout();
  } catch (error) {
    renderOutput(`Logout: ${error.message}`);
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
      const action = button.dataset.action;
      if (action === "smart") {
        renderOutput("⏳ Analisi IA in corso... (può richiedere 30-60 secondi)");
      }
      await runAction(action);
    } catch (error) {
      renderOutput(`Errore richiesta: ${error.message}`);
    }
  });
});

renderAuthState();

if (state.token) {
  renderOutput("Token recuperato dal browser. Puoi lanciare le API protette.");
}