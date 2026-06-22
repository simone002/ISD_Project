package com.example.ISDProject.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.example.ISDProject.dto.BatchInsightsDTO;
import com.example.ISDProject.dto.DailyReportDTO;
import com.example.ISDProject.dto.MonthlySummaryDTO;
import com.example.ISDProject.model.RenewableData;
import com.example.ISDProject.repository.RenewableRepository;
import com.example.ISDProject.session.UserSession;

@Service
public class EnergyService {

    private final RenewableRepository repository;
    private final UserSession userSession;
    private final LlmService llmService;

    public EnergyService(RenewableRepository repository, UserSession userSession, LlmService llmService) {
        this.repository = repository;
        this.userSession = userSession;
        this.llmService = llmService;
    }

    public List<DailyReportDTO> getDailyReports() {
        List<RenewableData> allData = repository.findAll();

        if (userSession.isFilterActive()) {
            allData = allData.stream()
                    .filter(d -> !d.getDateTime().toLocalDate().isBefore(userSession.getStart()) &&
                                 !d.getDateTime().toLocalDate().isAfter(userSession.getEnd()))
                    .collect(Collectors.toList());
        }

        Map<LocalDate, List<RenewableData>> groupedByDay = allData.stream()
                .collect(Collectors.groupingBy(d -> d.getDateTime().toLocalDate()));

        return groupedByDay.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<RenewableData> dailyData = entry.getValue();

                    double totalProd = Math.round(dailyData.stream().mapToDouble(RenewableData::getSystemProduction).sum() * 100.0) / 100.0;
                    double avgTemp = Math.round(dailyData.stream().mapToDouble(RenewableData::getAirTemperature).average().orElse(0.0) * 100.0) / 100.0;

                    String status = "NORMAL";
                    if (totalProd > 2000) status = "HIGH_PERFORMANCE";
                    else if (totalProd < 500) status = "LOW_GENERATION";

                    return new DailyReportDTO(date.toString(), totalProd, avgTemp, status);
                })
                .sorted(Comparator.comparing(DailyReportDTO::getDate))
                .collect(Collectors.toList());
    }

    @Cacheable("batchInsights")
    public BatchInsightsDTO getBatchInsights() {
        List<RenewableData> allData = repository.findAll();

        if (allData.isEmpty()) {
            return new BatchInsightsDTO("N/A", 0.0, "N/A", 0.0, List.of("Nessun dato disponibile."), 0.0);
        }

        RenewableData best = allData.stream()
                .max(Comparator.comparing(RenewableData::getSystemProduction))
                .orElse(null);

        RenewableData worst = allData.stream()
                .filter(d -> d.getSystemProduction() > 0)
                .min(Comparator.comparing(RenewableData::getSystemProduction))
                .orElse(null);

        double totalSum = allData.stream().mapToDouble(RenewableData::getSystemProduction).sum();

        List<String> anomalies = detectOutagePeriods(allData);

        return new BatchInsightsDTO(
                best != null ? best.getDateTime().toLocalDate().toString() : "N/A",
                best != null ? best.getSystemProduction() : 0.0,
                worst != null ? worst.getDateTime().toLocalDate().toString() : "N/A",
                worst != null ? worst.getSystemProduction() : 0.0,
                anomalies,
                Math.round(totalSum * 100.0) / 100.0
        );
    }

    /**
     * Rileva i fermi impianto raggruppando giorni CONSECUTIVI a produzione quasi nulla.
     * Un blackout di 21 giorni è un solo evento, non 21 alert separati.
     * Segnala solo i periodi in cui c'era radiazione sfruttabile (picco > 300 W/m²):
     * un giorno fermo ma nuvoloso non è necessariamente un guasto.
     */
    private List<String> detectOutagePeriods(List<RenewableData> allData) {
        // Aggrega per giorno: produzione totale e picco di radiazione
        Map<LocalDate, Double> dailyProd = new java.util.TreeMap<>();
        Map<LocalDate, Double> dailyMaxRad = new java.util.TreeMap<>();
        for (RenewableData d : allData) {
            LocalDate day = d.getDateTime().toLocalDate();
            dailyProd.merge(day, d.getSystemProduction(), Double::sum);
            double rad = d.getRadiation() != null ? d.getRadiation() : 0.0;
            dailyMaxRad.merge(day, rad, Math::max);
        }

        List<String> alerts = new java.util.ArrayList<>();
        LocalDate runStart = null;
        LocalDate prevDay = null;
        double runPeakRad = 0.0;

        for (Map.Entry<LocalDate, Double> entry : dailyProd.entrySet()) {
            LocalDate day = entry.getKey();
            boolean isDown = entry.getValue() < 50.0;

            if (isDown) {
                if (runStart == null) {
                    runStart = day;
                    runPeakRad = 0.0;
                } else if (!prevDay.plusDays(1).equals(day)) {
                    // c'è un buco di calendario: chiudi il run precedente e aprine uno nuovo
                    flushOutage(alerts, runStart, prevDay, runPeakRad);
                    runStart = day;
                    runPeakRad = 0.0;
                }
                runPeakRad = Math.max(runPeakRad, dailyMaxRad.getOrDefault(day, 0.0));
                prevDay = day;
            } else if (runStart != null) {
                flushOutage(alerts, runStart, prevDay, runPeakRad);
                runStart = null;
            }
        }
        if (runStart != null) {
            flushOutage(alerts, runStart, prevDay, runPeakRad);
        }

        return alerts.isEmpty() ? List.of("Nessuna anomalia rilevata.") : alerts;
    }

    private void flushOutage(List<String> alerts, LocalDate start, LocalDate end, double peakRad) {
        // Segnala solo se durante il fermo c'era sole sfruttabile (altrimenti potrebbe essere solo maltempo)
        if (peakRad <= 300) return;

        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days == 1) {
            alerts.add(String.format(
                "ALERT %s: produzione nulla con picco radiazione %.0f W/m² — possibile guasto",
                start, peakRad));
        } else {
            alerts.add(String.format(
                "FERMO IMPIANTO dal %s al %s (%d giorni consecutivi a produzione zero, picco radiazione %.0f W/m²) — guasto prolungato",
                start, end, days, peakRad));
        }
    }

    public List<MonthlySummaryDTO> getMonthlySummary() {
        List<RenewableData> allData = repository.findAll();

        Map<String, List<RenewableData>> groupedByMonth = allData.stream()
                .collect(Collectors.groupingBy(d -> {
                    LocalDate date = d.getDateTime().toLocalDate();
                    return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
                }));

        return groupedByMonth.entrySet().stream()
                .map(entry -> {
                    String month = entry.getKey();
                    List<RenewableData> monthData = entry.getValue();

                    double totalProduction = Math.round(
                            monthData.stream().mapToDouble(RenewableData::getSystemProduction).sum() * 100.0) / 100.0;

                    long distinctDays = monthData.stream()
                            .map(d -> d.getDateTime().toLocalDate())
                            .distinct().count();

                    double avgDailyProduction = distinctDays > 0
                            ? Math.round((totalProduction / distinctDays) * 100.0) / 100.0
                            : 0.0;

                    double avgTemperature = Math.round(
                            monthData.stream().mapToDouble(RenewableData::getAirTemperature).average().orElse(0.0) * 100.0) / 100.0;

                    return new MonthlySummaryDTO(month, totalProduction, avgDailyProduction, avgTemperature);
                })
                .sorted(Comparator.comparing(MonthlySummaryDTO::getMonth))
                .collect(Collectors.toList());
    }

    public String analyzeWindImpact(String start, String end) {
        List<RenewableData> data = getFilteredData(start, end);
        if (data.isEmpty()) return "Nessun dato nel periodo selezionato.";

        double avgProdHighWind = data.stream()
                .filter(d -> d.getWindSpeed() != null && d.getWindSpeed() > 4.0)
                .mapToDouble(RenewableData::getSystemProduction).average().orElse(0.0);

        double avgProdLowWind = data.stream()
                .filter(d -> d.getWindSpeed() != null && d.getWindSpeed() < 2.0)
                .mapToDouble(RenewableData::getSystemProduction).average().orElse(0.0);

        double difference = (avgProdLowWind > 0) ? ((avgProdHighWind - avgProdLowWind) / avgProdLowWind) * 100 : 0;

        return String.format(
            "ANALISI VENTO (%d dati analizzati):\n" +
            "- Prod. Media Vento Forte: %.2f kWh\n" +
            "- Prod. Media Vento Debole: %.2f kWh\n" +
            "Differenza di resa: %.1f%%",
            data.size(), avgProdHighWind, avgProdLowWind, difference
        );
    }

    // Media mobile esponenziale sui totali giornalieri: giorni recenti pesano di più
    public String getForecast(String start, String end) {
        List<RenewableData> data = getFilteredData(start, end);
        if (data.isEmpty()) return "Dati insufficienti.";

        // Aggrega per giorno e ordina cronologicamente
        List<Double> dailyTotals = data.stream()
                .collect(Collectors.groupingBy(d -> d.getDateTime().toLocalDate(),
                        Collectors.summingDouble(RenewableData::getSystemProduction)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .filter(v -> v > 0.1)
                .collect(Collectors.toList());

        if (dailyTotals.isEmpty()) return "Dati insufficienti.";

        int n = dailyTotals.size();
        double alpha = 0.9; // fattore di decadimento: giorni recenti pesano di più

        double weightedSum = 0;
        double totalWeight = 0;
        for (int i = 0; i < n; i++) {
            double weight = Math.pow(alpha, n - 1 - i);
            weightedSum += dailyTotals.get(i) * weight;
            totalWeight += weight;
        }
        double forecast = Math.round((weightedSum / totalWeight) * 100.0) / 100.0;

        // Trend: confronta media primo terzo vs ultimo terzo del periodo
        int third = Math.max(1, n / 3);
        double avgStart = dailyTotals.subList(0, third).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double avgEnd   = dailyTotals.subList(n - third, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double trendPct = avgStart > 0 ? ((avgEnd - avgStart) / avgStart) * 100 : 0;
        String trend = trendPct > 5 ? "↑ tendenza crescente"
                     : trendPct < -5 ? "↓ tendenza decrescente"
                     : "→ tendenza stabile";

        return String.format(
            "PREVISIONE PRODUZIONE (Media Ponderata Esponenziale su %d giorni):\n" +
            "- Produzione Giornaliera Attesa: %.2f kWh\n" +
            "- Trend del periodo: %s (%.1f%%)\n" +
            "Nota: i giorni più recenti incidono maggiormente sulla stima (α=%.1f).",
            n, forecast, trend, trendPct, alpha
        );
    }

    public String getFinancialReport(String start, String end) {
        List<RenewableData> data = getFilteredData(start, end);
        if (data.isEmpty()) return "Dati insufficienti.";

        double totalKwh = data.stream().mapToDouble(RenewableData::getSystemProduction).sum();
        double energyPrice = 0.20;
        double totalValue = totalKwh * energyPrice;
        double co2Saved = totalKwh * 0.4;

        return String.format(
            "REPORT ECONOMICO & ECOLOGICO:\n" +
            "- Energia Totale Prodotta: %.2f kWh\n" +
            "- Valore Economico Generato: %.2f € (a %.2f €/kWh)\n" +
            "- CO2 Risparmiata: %.2f kg\n",
            totalKwh, totalValue, energyPrice, co2Saved
        );
    }

    @Cacheable("peakHours")
    public String getPeakHours() {
        List<RenewableData> allData = repository.findAll();

        Map<Integer, Double> avgPerHour = new HashMap<>();
        allData.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDateTime().getHour(),
                        Collectors.mapping(RenewableData::getSystemProduction, Collectors.toList())
                ))
                .forEach((hour, values) ->
                        avgPerHour.put(hour, values.stream().mapToDouble(v -> v).average().orElse(0.0)));

        int bestHour = avgPerHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);

        double maxAvg = avgPerHour.getOrDefault(bestHour, 0.0);

        return String.format(
            "ANALISI ORARIA:\n" +
            "- Ora di Picco Assoluta: %02d:00\n" +
            "- Produzione Media in quell'ora: %.2f kWh\n" +
            "Consiglio: Programmare i macchinari intorno alle %02d:00.",
            bestHour, maxAvg, bestHour
        );
    }

    public String getSmartAnalysis(String start, String end) {
        List<RenewableData> dataToAnalyze;

        if (start != null && end != null) {
            LocalDateTime startDate = LocalDate.parse(start).atStartOfDay();
            LocalDateTime endDate = LocalDate.parse(end).atTime(23, 59, 59);
            dataToAnalyze = repository.findByDateTimeBetween(startDate, endDate);
        } else {
            dataToAnalyze = repository.findAll().stream()
                    .sorted((a, b) -> b.getDateTime().compareTo(a.getDateTime()))
                    .limit(3)
                    .collect(Collectors.toList());
        }

        if (dataToAnalyze.isEmpty()) return "Nessun dato trovato nel periodo selezionato.";

        if (dataToAnalyze.size() > 15) {
            dataToAnalyze = dataToAnalyze.stream().limit(15).collect(Collectors.toList());
        }

        StringBuilder dataContext = new StringBuilder();
        for (RenewableData d : dataToAnalyze) {
            dataContext.append(String.format("- Data: %s, Prod: %.2f, Radiazione: %.1f, Temp: %.1f, Vento: %.1f\n",
                    d.getDateTime(), d.getSystemProduction(), d.getRadiation(), d.getAirTemperature(), d.getWindSpeed()));
        }

        return llmService.askAi(dataContext.toString());
    }

    private List<RenewableData> getFilteredData(String start, String end) {
        if (start != null && end != null && !start.isEmpty()) {
            try {
                LocalDateTime startDate = LocalDate.parse(start).atStartOfDay();
                LocalDateTime endDate = LocalDate.parse(end).atTime(23, 59, 59);
                return repository.findByDateTimeBetween(startDate, endDate);
            } catch (Exception e) {
                return List.of();
            }
        }
        return repository.findAll();
    }
}
