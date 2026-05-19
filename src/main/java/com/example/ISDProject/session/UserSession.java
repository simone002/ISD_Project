package com.example.ISDProject.session;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
public class UserSession {
    private LocalDate startDateFilter;
    private LocalDate endDateFilter;

    public boolean isFilterActive() {
        return startDateFilter != null && endDateFilter != null;
    }

    public void setRange(LocalDate start, LocalDate end) {
        this.startDateFilter = start;
        this.endDateFilter = end;
    }

    public LocalDate getStart() { return startDateFilter; }
    public LocalDate getEnd() { return endDateFilter; }
}
