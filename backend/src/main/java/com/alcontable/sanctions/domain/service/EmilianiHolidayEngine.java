package com.alcontable.sanctions.domain.service;

import com.alcontable.shared.domain.exceptions.DomainValidationException;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Motor matemático determinístico de Días Festivos en Colombia (Ley 51 de 1983 — Ley Emiliani).
 * Calcula todos los festivos fijos, los trasladables al lunes siguiente y los religiosos móviles
 * basados en el Domingo de Pascua (Algoritmo astronómico de Meeus/Jones/Butcher),
 * con memoización concurrente O(1) por año para alta concurrencia.
 */
@Component
public class EmilianiHolidayEngine {

    private final ConcurrentMap<Integer, List<ColombianHoliday>> holidayCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Set<LocalDate>> holidayDateSetCache = new ConcurrentHashMap<>();

    public record ColombianHoliday(
            LocalDate observedDate,
            LocalDate originalHistoricalDate,
            String name,
            String category,
            boolean movedByEmilianiLaw,
            String dayOfWeekNameEs
    ) {}

    public List<ColombianHoliday> getHolidaysForYear(int year) {
        if (year < 1984 || year > 2100) {
            throw new DomainValidationException("El año debe estar entre 1984 (vigencia Ley Emiliani) y 2100.");
        }
        return holidayCache.computeIfAbsent(year, this::computeHolidaysForYear);
    }

    public boolean isHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        int year = date.getYear();
        Set<LocalDate> dateSet = holidayDateSetCache.computeIfAbsent(year, y ->
                getHolidaysForYear(y).stream()
                        .map(ColombianHoliday::observedDate)
                        .collect(Collectors.toUnmodifiableSet())
        );
        return dateSet.contains(date);
    }

    private List<ColombianHoliday> computeHolidaysForYear(int year) {
        List<ColombianHoliday> list = new ArrayList<>();
        LocalDate easterSunday = computeEasterSunday(year);

        // 1. Festivos de fecha fija inamovible
        addFixed(list, LocalDate.of(year, 1, 1), "Año Nuevo");
        addFixed(list, LocalDate.of(year, 5, 1), "Día del Trabajo");
        addFixed(list, LocalDate.of(year, 7, 20), "Día de la Independencia de Colombia");
        addFixed(list, LocalDate.of(year, 8, 7), "Batalla de Boyacá");
        addFixed(list, LocalDate.of(year, 12, 8), "Día de la Inmaculada Concepción");
        addFixed(list, LocalDate.of(year, 12, 25), "Navidad");

        // 2. Festivos trasladables al siguiente lunes (Ley 51 de 1983 - Ley Emiliani)
        addEmiliani(list, LocalDate.of(year, 1, 6), "Día de los Reyes Magos");
        addEmiliani(list, LocalDate.of(year, 3, 19), "Día de San José");
        addEmiliani(list, LocalDate.of(year, 6, 29), "San Pedro y San Pablo");
        addEmiliani(list, LocalDate.of(year, 8, 15), "La Asunción de la Virgen");
        addEmiliani(list, LocalDate.of(year, 10, 12), "Día de la Raza");
        addEmiliani(list, LocalDate.of(year, 11, 1), "Todos los Santos");
        addEmiliani(list, LocalDate.of(year, 11, 11), "Independencia de Cartagena");

        // 3. Festivos móviles respecto al Domingo de Pascua
        addEasterRelative(list, easterSunday.minusDays(3), "Jueves Santo", false);
        addEasterRelative(list, easterSunday.minusDays(2), "Viernes Santo", false);
        addEasterRelative(list, easterSunday.plusDays(39), "Ascensión del Señor", true);
        addEasterRelative(list, easterSunday.plusDays(60), "Corpus Christi", true);
        addEasterRelative(list, easterSunday.plusDays(68), "Sagrado Corazón de Jesús", true);

        list.sort(Comparator.comparing(ColombianHoliday::observedDate));
        return List.copyOf(list);
    }

    /**
     * Algoritmo de Meeus/Jones/Butcher para determinar el Domingo de Pascua Gregoriano.
     */
    public LocalDate computeEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private void addFixed(List<ColombianHoliday> list, LocalDate date, String name) {
        list.add(new ColombianHoliday(
                date,
                date,
                name,
                "FIJO_INAMOVIBLE",
                false,
                dayNameEs(date.getDayOfWeek())
        ));
    }

    private void addEmiliani(List<ColombianHoliday> list, LocalDate originalDate, String name) {
        LocalDate observed = moveToNextMondayIfNeeded(originalDate);
        list.add(new ColombianHoliday(
                observed,
                originalDate,
                name,
                "LEY_EMILIANI_LUNES",
                !observed.equals(originalDate),
                dayNameEs(observed.getDayOfWeek())
        ));
    }

    private void addEasterRelative(List<ColombianHoliday> list, LocalDate originalDate, String name, boolean moveEmiliani) {
        LocalDate observed = moveEmiliani ? moveToNextMondayIfNeeded(originalDate) : originalDate;
        list.add(new ColombianHoliday(
                observed,
                originalDate,
                name,
                moveEmiliani ? "PASCUA_EMILIANI" : "SEMANA_SANTA",
                !observed.equals(originalDate),
                dayNameEs(observed.getDayOfWeek())
        ));
    }

    private LocalDate moveToNextMondayIfNeeded(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.MONDAY) {
            return date;
        }
        return date.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private String dayNameEs(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}
