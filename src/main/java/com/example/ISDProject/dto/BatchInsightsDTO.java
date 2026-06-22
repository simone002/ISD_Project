package com.example.ISDProject.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchInsightsDTO {

    private String bestDayDate;
    private Double bestDayProduction;

    private String worstDayDate;
    private Double worstDayProduction;

    private List<String> anomalies;

    private Double totalYearlyProduction;
}
