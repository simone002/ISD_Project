package com.example.ISDProject.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ISDProject.dto.BatchInsightsDTO;
import com.example.ISDProject.dto.DailyReportDTO;
import com.example.ISDProject.service.EnergyService;
import com.example.ISDProject.session.UserSession;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

    private final EnergyService energyService;
    private final UserSession userSession;

    public EnergyController(EnergyService energyService, UserSession userSession) {
        this.energyService = energyService;
        this.userSession = userSession;
    }

    @PostMapping("/session/filter")
    public String setSessionFilter(@RequestParam String start, @RequestParam String end) {
        try {
            userSession.setRange(LocalDate.parse(start), LocalDate.parse(end));
            return "Filtro sessione attivato: dal " + start + " al " + end;
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

    @GetMapping("/smart-analysis")
    public String getSmartAnalysis(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return energyService.getSmartAnalysis(start, end);
    }
}
