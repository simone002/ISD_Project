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
            return new BatchInsightsDTO("N/A", 0.0, "N/A", 0.0, "No Data", 0.0);
        }

        RenewableData best = allData.stream()
                .max(Comparator.comparing(RenewableData::getSystemProduction))
                .orElse(null);

        RenewableData worst = allData.stream()
                .filter(d -> d.getSystemProduction() > 0)
                .min(Comparator.comparing(RenewableData::getSystemProduction))
                .orElse(null);

        double totalSum = allData.stream().mapToDouble(RenewableData::getSystemProduction).sum();

        String anomalyMsg = "Nessuna anomalia rilevata.";
        RenewableData anomaly = allData.stream()
                .filter(d -> d.getRadiation() != null && d.getRadiation() > 100 && d.getSystemProduction() < 50)
                .findFirst()
                .orElse(null);

        if (anomaly != null) {
            anomalyMsg = "ALERT: Il giorno " + anomaly.getDateTime().toLocalDate() +
                         " c'era sole (Rad: " + anomaly.getRadiation() +
                         ") ma produzione quasi nulla (" + anomaly.getSystemProduction() + "). Controllare guasti.";
        }

        return new BatchInsightsDTO(
                best != null ? best.getDateTime().toLocalDate().toString() : "N/A",
                best != null ? best.getSystemProduction() : 0.0,
                worst != null ? worst.getDateTime().toLocalDate().toString() : "N/A",
                worst != null ? worst.getSystemProduction() : 0.0,
                anomalyMsg,
                Math.round(totalSum * 100.0) / 100.0
        );
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

    public String getForecast(String start, String end) {
        List<RenewableData> data = getFilteredData(start, end);
        if (data.isEmpty()) return "Dati insufficienti.";

        double forecast = data.stream()
                .filter(d -> d.getSystemProduction() > 0.1)
                .mapToDouble(RenewableData::getSystemProduction)
                .average().orElse(0.0);

        return "SIMULAZIONE PREVISIONE:\n" +
               "Basandosi sul periodo selezionato (" + data.size() + " record utili),\n" +
               "la produzione giornaliera attesa è: " + Math.round(forecast * 100.0) / 100.0 + " kWh";
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
