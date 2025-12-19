package com.example.ISDProject.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "renewable_data")

public class RenewableData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dateTime;

    private Double windSpeed;

    private Integer sunshine;

    private Double airPressure;

    private Double radiation;

    private Double airTemperature;

    private Integer relativeAirHumidity;

    private Double systemProduction;
}