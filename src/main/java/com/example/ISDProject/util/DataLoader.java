package com.example.ISDProject.util;

import java.io.FileReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.ISDProject.model.RenewableData;
import com.example.ISDProject.repository.RenewableRepository;
import com.opencsv.CSVReader;

@Component
public class DataLoader implements CommandLineRunner {

    private final RenewableRepository repository;

    public DataLoader(RenewableRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) return; 

        System.out.println("--- INIZIO IMPORTAZIONE CSV ---");
        
        String csvFile = "data.csv"; 

        try (CSVReader reader = new CSVReader(new FileReader(csvFile))) {
            String[] line;
            reader.readNext(); 

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm");

            while ((line = reader.readNext()) != null) {
                RenewableData data = new RenewableData();
                
                data.setDateTime(LocalDateTime.parse(line[0], formatter));
                data.setWindSpeed(parseD(line[1]));
                data.setSunshine(parseI(line[2]));
                data.setAirPressure(parseD(line[3]));
                data.setRadiation(parseD(line[4]));
                data.setAirTemperature(parseD(line[5]));
                data.setRelativeAirHumidity(parseI(line[6]));
                data.setSystemProduction(parseD(line[7]));

                repository.save(data);
            }
            System.out.println("--- IMPORTAZIONE COMPLETATA: " + repository.count() + " RECORD ---");
        } catch (Exception e) {
            System.err.println("ERRORE LETTURA CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Double parseD(String val) { return (val == null || val.isEmpty()) ? 0.0 : Double.parseDouble(val); }
    private Integer parseI(String val) { return (val == null || val.isEmpty()) ? 0 : Integer.parseInt(val); }
}