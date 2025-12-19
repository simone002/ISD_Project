package com.example.ISDProject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchInsightsDTO {

    private String bestDayDate;
    private Double bestDayProduction;

    
    private String worstDayDate;
    private Double worstDayProduction;

    private String anomalyDetection; 
    
    private Double totalYearlyProduction;
}