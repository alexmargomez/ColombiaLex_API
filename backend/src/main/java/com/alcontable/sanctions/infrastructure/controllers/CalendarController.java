package com.alcontable.sanctions.infrastructure.controllers;

import com.alcontable.sanctions.application.usecases.CalculateBusinessDaysUseCase;
import com.alcontable.sanctions.domain.service.EmilianiHolidayEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "3. Calendario de Festivos (Ley Emiliani) & Días Hábiles", description = "Cálculo matemático de festivos en Colombia (Ley 51 de 1983) y calculadora de términos en días hábiles")
public class CalendarController {

    private final EmilianiHolidayEngine holidayEngine;
    private final CalculateBusinessDaysUseCase businessDaysUseCase;

    public CalendarController(
            EmilianiHolidayEngine holidayEngine,
            CalculateBusinessDaysUseCase businessDaysUseCase
    ) {
        this.holidayEngine = holidayEngine;
        this.businessDaysUseCase = businessDaysUseCase;
    }

    @GetMapping("/holidays/{year}")
    @Operation(summary = "Obtener los 18 días festivos oficiales de Colombia para cualquier año (1984-2100) aplicando Ley Emiliani")
    public Map<String, Object> getHolidaysByYear(@PathVariable int year) {
        var holidays = holidayEngine.getHolidaysForYear(year);
        return Map.of(
                "year", year,
                "easterSunday", holidayEngine.computeEasterSunday(year),
                "totalHolidays", holidays.size(),
                "holidays", holidays
        );
    }

    @GetMapping("/business-days/between")
    @Operation(summary = "Contar días hábiles laborales/administrativos entre dos fechas descontando fines de semana y festivos")
    public CalculateBusinessDaysUseCase.BusinessDaysCountResponse countBusinessDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeSaturdays
    ) {
        return businessDaysUseCase.countBetween(from, to, includeSaturdays);
    }

    @GetMapping("/business-days/add")
    @Operation(summary = "Calcular fecha de vencimiento sumando N días hábiles a una fecha inicial (PQRSD, términos judiciales, facturas)")
    public CalculateBusinessDaysUseCase.AddBusinessDaysResponse addBusinessDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(defaultValue = "15") int days,
            @RequestParam(defaultValue = "false") boolean includeSaturdays
    ) {
        return businessDaysUseCase.addBusinessDays(startDate, days, includeSaturdays);
    }
}
