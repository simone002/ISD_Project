package com.example.ISDProject.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.ISDProject.model.RenewableData;

@Repository
public interface RenewableRepository extends JpaRepository<RenewableData, Long> {
    List<RenewableData> findByDateTimeBetween(LocalDateTime start, LocalDateTime end);
}