package com.example.ISDProject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DailyReportDTO {
    private String date;
    private Double totalProduction;
    private Double avgTemperature;
    private String status; 
}