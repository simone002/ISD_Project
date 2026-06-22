package com.example.ISDProject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MonthlySummaryDTO {
    private String month;
    private Double totalProduction;
    private Double avgDailyProduction;
    private Double avgTemperature;
}
