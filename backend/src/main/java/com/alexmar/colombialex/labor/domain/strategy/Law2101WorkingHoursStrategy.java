package com.alexmar.colombialex.labor.domain.strategy;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Strategy Pattern — Reducción Gradual de la Jornada Laboral en Colombia (Ley 2101 de 2021).
 * Determina automáticamente las horas semanales máximas y el divisor mensual de horas ordinarias
 * según la fecha de causación sin disminuir el salario del trabajador.
 */
@Component
public class Law2101WorkingHoursStrategy {

    public record WorkingHoursResolution(
            LocalDate referenceDate,
            int maxWeeklyHours,
            BigDecimal monthlyHoursDivisor,
            String legalStageDescription
    ) {}

    public WorkingHoursResolution resolveForDate(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();

        if (effectiveDate.isBefore(LocalDate.of(2023, 7, 15))) {
            return new WorkingHoursResolution(
                    effectiveDate,
                    48,
                    new BigDecimal("240"),
                    "Jornada tradicional Art. 161 CST (48 horas semanales / 240 horas al mes)"
            );
        }
        if (effectiveDate.isBefore(LocalDate.of(2024, 7, 15))) {
            return new WorkingHoursResolution(
                    effectiveDate,
                    47,
                    new BigDecimal("235"),
                    "Ley 2101 de 2021 — Etapa 1 (Desde 15 Jul 2023: 47 horas semanales / 235 horas al mes)"
            );
        }
        if (effectiveDate.isBefore(LocalDate.of(2025, 7, 15))) {
            return new WorkingHoursResolution(
                    effectiveDate,
                    46,
                    new BigDecimal("230"),
                    "Ley 2101 de 2021 — Etapa 2 (Desde 15 Jul 2024: 46 horas semanales / 230 horas al mes)"
            );
        }
        if (effectiveDate.isBefore(LocalDate.of(2026, 7, 15))) {
            return new WorkingHoursResolution(
                    effectiveDate,
                    44,
                    new BigDecimal("220"),
                    "Ley 2101 de 2021 — Etapa 3 (Desde 15 Jul 2025: 44 horas semanales / 220 horas al mes)"
            );
        }
        return new WorkingHoursResolution(
                effectiveDate,
                42,
                new BigDecimal("210"),
                "Ley 2101 de 2021 — Etapa Final (Desde 15 Jul 2026: 42 horas semanales / 210 horas al mes)"
        );
    }
}
