package com.alcontable.payroll.domain.strategy;

import com.alcontable.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Strategy Pattern — Liquidación de Indemnización por Despido sin Justa Causa (Art. 64 CST).
 * Separa el algoritmo legal según la modalidad de contrato y el nivel de ingresos en SMLMV.
 */
@Component
public class SeveranceIndemnificationStrategy {

    public record IndemnificationResult(
            String contractType,
            boolean dismissalWithoutJustCause,
            BigDecimal compensatedDays,
            BigDecimal amountCOP,
            String legalRuleApplied,
            String formulaExplanation
    ) {}

    public IndemnificationResult calculate(
            String contractType,
            boolean dismissalWithoutJustCause,
            MoneyCOP monthlyBaseSalary,
            MoneyCOP currentSmlmv,
            int workedDaysTotal,
            int remainingDaysFixedTerm
    ) {
        if (!dismissalWithoutJustCause) {
            return new IndemnificationResult(
                    contractType,
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "Terminación por mutuo acuerdo, expiración de plazo o justa causa (Art. 61/62 CST)",
                    "Sin indemnización ($0 COP)"
            );
        }

        MoneyCOP dailySalary = monthlyBaseSalary.divide(new BigDecimal("30"));
        String normalizedType = contractType != null ? contractType.trim().toUpperCase() : "INDEFINIDO";

        return switch (normalizedType) {
            case "FIJO", "TERMINO_FIJO" -> calculateFixedTerm(dailySalary, remainingDaysFixedTerm);
            case "OBRA_LABOR", "OBRA" -> calculateWorkOrLabor(dailySalary, remainingDaysFixedTerm);
            default -> calculateIndefiniteTerm(monthlyBaseSalary, currentSmlmv, dailySalary, workedDaysTotal);
        };
    }

    private IndemnificationResult calculateFixedTerm(MoneyCOP dailySalary, int remainingDays) {
        int effectiveDays = Math.max(15, remainingDays);
        MoneyCOP total = dailySalary.multiply(effectiveDays).roundToPesos();
        return new IndemnificationResult(
                "TERMINO_FIJO",
                true,
                BigDecimal.valueOf(effectiveDays),
                total.roundedPesosValue(),
                "Art. 64 CST — Contrato a Término Fijo (Salarios faltantes hasta el vencimiento del plazo pactado)",
                String.format("Salario Diario (%s) × %d días restantes = %s", dailySalary.roundToPesos(), effectiveDays, total)
        );
    }

    private IndemnificationResult calculateWorkOrLabor(MoneyCOP dailySalary, int remainingDays) {
        int effectiveDays = Math.max(15, remainingDays);
        MoneyCOP total = dailySalary.multiply(effectiveDays).roundToPesos();
        return new IndemnificationResult(
                "OBRA_LABOR",
                true,
                BigDecimal.valueOf(effectiveDays),
                total.roundedPesosValue(),
                "Art. 64 CST — Contrato por Duración de la Obra o Labor (Mínimo legal: 15 días de salario)",
                String.format("Salario Diario (%s) × %d días estimados para fin de obra (mín. 15) = %s",
                        dailySalary.roundToPesos(), effectiveDays, total)
        );
    }

    private IndemnificationResult calculateIndefiniteTerm(
            MoneyCOP monthlySalary,
            MoneyCOP smlmv,
            MoneyCOP dailySalary,
            int workedDaysTotal
    ) {
        MoneyCOP tenSmlmv = smlmv.multiply(10);
        boolean isHighIncome = monthlySalary.isGreaterThanOrEqualTo(tenSmlmv);

        BigDecimal firstYearDays = isHighIncome ? new BigDecimal("20") : new BigDecimal("30");
        BigDecimal additionalYearDays = isHighIncome ? new BigDecimal("15") : new BigDecimal("20");

        BigDecimal totalCompensatedDays;
        if (workedDaysTotal <= 360) {
            totalCompensatedDays = firstYearDays;
        } else {
            int extraDays = workedDaysTotal - 360;
            BigDecimal proportionalExtra = BigDecimal.valueOf(extraDays)
                    .multiply(additionalYearDays)
                    .divide(new BigDecimal("360"), 4, RoundingMode.HALF_UP);
            totalCompensatedDays = firstYearDays.add(proportionalExtra).setScale(2, RoundingMode.HALF_UP);
        }

        MoneyCOP total = dailySalary.multiply(totalCompensatedDays).roundToPesos();
        String rule = isHighIncome
                ? "Art. 64 CST — Término Indefinido (Salario >= 10 SMLMV: 20 días primer año + 15 días por año adicional o fracción)"
                : "Art. 64 CST — Término Indefinido (Salario < 10 SMLMV: 30 días primer año + 20 días por año adicional o fracción)";

        String formula = String.format("Salario Diario (%s) × %s días indemnizables (%d días laborados) = %s",
                dailySalary.roundToPesos(), totalCompensatedDays.toPlainString(), workedDaysTotal, total);

        return new IndemnificationResult(
                "INDEFINIDO",
                true,
                totalCompensatedDays,
                total.roundedPesosValue(),
                rule,
                formula
        );
    }
}
