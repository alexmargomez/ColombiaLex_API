package com.alcontable.sanctions.application.usecases;

import com.alcontable.sanctions.domain.service.EmilianiHolidayEngine;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CalculateBusinessDaysUseCase {

    private final EmilianiHolidayEngine holidayEngine;

    public CalculateBusinessDaysUseCase(EmilianiHolidayEngine holidayEngine) {
        this.holidayEngine = holidayEngine;
    }

    public record BusinessDaysCountResponse(
            LocalDate startDate,
            LocalDate endDate,
            boolean includeSaturdaysAsBusinessDay,
            long calendarDaysTotal,
            int businessDaysCount,
            int weekendDaysSkipped,
            int holidaysOnWeekdaysSkipped,
            List<EmilianiHolidayEngine.ColombianHoliday> holidaysInRange
    ) {}

    public record AddBusinessDaysResponse(
            LocalDate startDate,
            int businessDaysAdded,
            boolean includeSaturdaysAsBusinessDay,
            LocalDate resultingDueDate,
            long totalCalendarDaysElapsed,
            List<EmilianiHolidayEngine.ColombianHoliday> holidaysEncountered
    ) {}

    public BusinessDaysCountResponse countBetween(LocalDate startDate, LocalDate endDate, boolean includeSaturdays) {
        if (startDate == null || endDate == null) {
            throw new DomainValidationException("Las fechas inicial y final son obligatorias.");
        }
        if (endDate.isBefore(startDate)) {
            throw new DomainValidationException("La fecha final no puede ser anterior a la fecha inicial.");
        }
        long calendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (calendarDays > 3660) {
            throw new DomainValidationException("El rango máximo permitido por consulta es de 10 años.");
        }

        Map<Integer, Map<LocalDate, EmilianiHolidayEngine.ColombianHoliday>> cache = new HashMap<>();
        int businessDays = 0;
        int weekends = 0;
        int holidaysSkipped = 0;
        List<EmilianiHolidayEngine.ColombianHoliday> matchedHolidays = new ArrayList<>();

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            var yearMap = cache.computeIfAbsent(cursor.getYear(), this::buildYearHolidayMap);
            DayOfWeek dow = cursor.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SUNDAY || (!includeSaturdays && dow == DayOfWeek.SATURDAY);

            if (isWeekend) {
                weekends++;
            } else if (yearMap.containsKey(cursor)) {
                holidaysSkipped++;
                matchedHolidays.add(yearMap.get(cursor));
            } else {
                businessDays++;
            }
            cursor = cursor.plusDays(1);
        }

        return new BusinessDaysCountResponse(
                startDate,
                endDate,
                includeSaturdays,
                calendarDays,
                businessDays,
                weekends,
                holidaysSkipped,
                matchedHolidays
        );
    }

    public AddBusinessDaysResponse addBusinessDays(LocalDate startDate, int daysToAdd, boolean includeSaturdays) {
        if (startDate == null) {
            throw new DomainValidationException("La fecha de inicio es obligatoria.");
        }
        if (daysToAdd < 1 || daysToAdd > 1000) {
            throw new DomainValidationException("La cantidad de días hábiles a sumar debe estar entre 1 y 1000.");
        }

        Map<Integer, Map<LocalDate, EmilianiHolidayEngine.ColombianHoliday>> cache = new HashMap<>();
        List<EmilianiHolidayEngine.ColombianHoliday> encountered = new ArrayList<>();

        LocalDate cursor = startDate;
        int remaining = daysToAdd;

        while (remaining > 0) {
            cursor = cursor.plusDays(1);
            var yearMap = cache.computeIfAbsent(cursor.getYear(), this::buildYearHolidayMap);
            DayOfWeek dow = cursor.getDayOfWeek();
            boolean isWeekend = dow == DayOfWeek.SUNDAY || (!includeSaturdays && dow == DayOfWeek.SATURDAY);

            if (!isWeekend) {
                if (yearMap.containsKey(cursor)) {
                    encountered.add(yearMap.get(cursor));
                } else {
                    remaining--;
                }
            }
        }

        long elapsed = ChronoUnit.DAYS.between(startDate, cursor);
        return new AddBusinessDaysResponse(
                startDate,
                daysToAdd,
                includeSaturdays,
                cursor,
                elapsed,
                encountered
        );
    }

    private Map<LocalDate, EmilianiHolidayEngine.ColombianHoliday> buildYearHolidayMap(int year) {
        return holidayEngine.getHolidaysForYear(year).stream()
                .collect(Collectors.toMap(
                        EmilianiHolidayEngine.ColombianHoliday::observedDate,
                        h -> h,
                        (a, b) -> a
                ));
    }
}
