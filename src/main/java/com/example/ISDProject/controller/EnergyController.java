package com.example.ISDProject.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ISDProject.dto.BatchInsightsDTO;
import com.example.ISDProject.dto.DailyReportDTO;
import com.example.ISDProject.dto.MonthlySummaryDTO;
import com.example.ISDProject.service.EnergyService;
import com.example.ISDProject.session.UserSession;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energyService;
    private final UserSession userSession;

    // Idempotent Receiver: mappa idempotency-key → risposta già inviata
    private final ConcurrentHashMap<String, String> idempotencyCache = new ConcurrentHashMap<>();

    public EnergyController(EnergyService energyService, UserSession userSession) {
        this.energyService = energyService;
        this.userSession = userSession;
    }

    @PostMapping("/session/filter")
    public String setSessionFilter(
            @RequestParam String start,
            @RequestParam String end,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // Idempotent Receiver: se la chiave è già nota, restituisce la risposta precedente
        if (idempotencyKey != null && idempotencyCache.containsKey(idempotencyKey)) {
            return idempotencyCache.get(idempotencyKey);
        }

        try {
            userSession.setRange(LocalDate.parse(start), LocalDate.parse(end));
            String response = "Filtro sessione attivato: dal " + start + " al " + end;

            if (idempotencyKey != null && idempotencyCache.size() < 1000) {
                idempotencyCache.put(idempotencyKey, response);
            }

            return response;
        } catch (Exception e) {
            return "Errore formato data. Usa il formato YYYY-MM-DD (es. 2017-01-01)";
        }
    }

    @GetMapping("/daily-report-session")
    public List<DailyReportDTO> getDailyReportsWithSession() {
        return energyService.getDailyReports();
    }

    @GetMapping("/batch-suggestions")
    public BatchInsightsDTO getBatchSuggestions() {
        return energyService.getBatchInsights();
    }

    @GetMapping("/wind-impact")
    public String getWindImpactAnalysis(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return energyService.analyzeWindImpact(start, end);
    }

    @GetMapping("/forecast")
    public String getProductionForecast(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return energyService.getForecast(start, end);
    }

    @GetMapping("/financial-report")
    public String getFinancialReport(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return energyService.getFinancialReport(start, end);
    }

    @GetMapping("/peak-hours")
    public String getPeakHoursAnalysis() {
        return energyService.getPeakHours();
    }

    @GetMapping("/monthly-summary")
    public List<MonthlySummaryDTO> getMonthlySummary() {
        return energyService.getMonthlySummary();
    }

    @GetMapping("/smart-analysis")
    public String getSmartAnalysis(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return energyService.getSmartAnalysis(start, end);
    }
}
