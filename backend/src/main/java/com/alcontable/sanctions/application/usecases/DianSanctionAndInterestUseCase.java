package com.alcontable.sanctions.application.usecases;

import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class DianSanctionAndInterestUseCase {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

    public record SanctionLiquidationRequest(
            Integer year,
            String declarationType, // RENTA_110, IVA_300, RETEFUENTE_350, EXOGENA_1001
            String sanctionConduct, // EXTEMPORANEIDAD_641, EXTEMPORANEIDAD_EMPLAZAMIENTO_642, CORRECCION_VOLUNTARIA_644, CORRECCION_EMPLAZAMIENTO_644
            LocalDate dueDate,
            LocalDate filingOrPaymentDate,
            BigDecimal taxPayableOrDeltaCOP,
            BigDecimal grossIncomePeriodCOP,
            BigDecimal netWorthPreviousYearCOP,
            Boolean voluntaryLiquidationByTaxpayer,
            Integer yearsWithoutSameInfraction, // >=2 -> reduce al 50% (Art. 640); >=1 -> reduce al 75%; 0 -> 100%
            BigDecimal annualUsuryRatePercent
    ) {}

    public record SanctionLiquidationResponse(
            int year,
            BigDecimal uvtValueCOP,
            BigDecimal minimumSanction10UvtRawCOP,
            BigDecimal minimumSanction10UvtRoundedCOP,
            String declarationType,
            String sanctionConduct,
            String legalBasisArticle,
            LocalDate dueDate,
            LocalDate filingOrPaymentDate,
            long calendarDaysOverdue,
            long monthsOrFractionOfMonthOverdue,
            BigDecimal calculationBaseCOP,
            String calculationBaseType,
            BigDecimal preliminaryFullSanctionCOP,
            String art640GradualityApplied,
            BigDecimal sanctionAfterArt640BeforeFloorCOP,
            boolean minimumSanctionFloorEnforced,
            BigDecimal finalSanctionExactCOP,
            BigDecimal finalSanctionRoundedArt868COP,
            BigDecimal savingsFromArt640COP,
            BigDecimal effectiveMoratoryAnnualRatePercent,
            BigDecimal moratoryInterestsExactCOP,
            BigDecimal moratoryInterestsRoundedArt868COP,
            BigDecimal form490CapitalRowCOP,
            BigDecimal form490SanctionRowCOP,
            BigDecimal form490InterestRowCOP,
            BigDecimal form490TotalPaymentCOP,
            LocalDate generalFirmnessDateArt714ET,
            String forensicDictum
    ) {}

    public SanctionLiquidationResponse execute(SanctionLiquidationRequest req) {
        if (req == null) {
            throw new DomainValidationException("Los datos para liquidar la sanción e intereses DIAN son requeridos.");
        }
        int year = req.year() != null ? req.year() : 2026;
        BigDecimal uvt = LaborLegalConstants.forYear(year).uvtCOP();

        // Piso Inquebrantable de Sanción Mínima (Art. 639 E.T. = 10 UVT)
        BigDecimal minSanctionRaw = uvt.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP);
        BigDecimal minSanctionRounded = MoneyCOP.roundToThousandArt868(minSanctionRaw);

        LocalDate due = req.dueDate() != null ? req.dueDate() : LocalDate.now().minusDays(45);
        LocalDate filing = req.filingOrPaymentDate() != null ? req.filingOrPaymentDate() : LocalDate.now();

        BigDecimal taxPayable = MoneyCOP.requireNonNegative(req.taxPayableOrDeltaCOP(), "taxPayableOrDeltaCOP");
        BigDecimal grossIncome = MoneyCOP.requireNonNegative(req.grossIncomePeriodCOP(), "grossIncomePeriodCOP");
        BigDecimal netWorth = MoneyCOP.requireNonNegative(req.netWorthPreviousYearCOP(), "netWorthPreviousYearCOP");

        String conduct = req.sanctionConduct() != null && !req.sanctionConduct().isBlank()
                ? req.sanctionConduct().trim().toUpperCase()
                : "EXTEMPORANEIDAD_641";
        String declarationType = req.declarationType() != null && !req.declarationType().isBlank()
                ? req.declarationType().trim()
                : "RETEFUENTE_350";

        BigDecimal usuryRate = req.annualUsuryRatePercent() != null && req.annualUsuryRatePercent().compareTo(new BigDecimal("5.0")) > 0
                ? req.annualUsuryRatePercent()
                : new BigDecimal("28.50");
        BigDecimal moratoryRate = usuryRate.subtract(new BigDecimal("2.00")).max(BigDecimal.ZERO);
        BigDecimal roundedCapital = MoneyCOP.roundToThousandArt868(taxPayable);

        long rawDaysBetween = ChronoUnit.DAYS.between(due, filing);
        boolean isTimely = rawDaysBetween <= 0;

        // Caso 1: Declaración oportuna o anticipada en conductas de Extemporaneidad -> $0 Sanción y $0 Intereses
        if (isTimely && conduct.startsWith("EXTEMPORANEIDAD")) {
            LocalDate firmnessDate = due.plusYears(3);
            return new SanctionLiquidationResponse(
                    year,
                    uvt,
                    minSanctionRaw,
                    minSanctionRounded,
                    declarationType,
                    conduct,
                    "Declaración Oportuna (Presentada dentro del plazo legal — Sin Sanción Art. 641/642 E.T.)",
                    due,
                    filing,
                    0L,
                    0L,
                    taxPayable,
                    "PRESENTACION_OPORTUNA",
                    BigDecimal.ZERO,
                    "NO_APLICA_OPORTUNA",
                    BigDecimal.ZERO,
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    moratoryRate,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    roundedCapital,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    roundedCapital,
                    firmnessDate,
                    "Declaración presentada oportunamente dentro del vencimiento legal. Sanción por extemporaneidad: $0 COP. Intereses de mora: $0 COP."
            );
        }

        // Caso 2: Corrección sin mayor valor a pagar ni menor saldo a favor (Art. 588 / Ley 962 Art. 43) -> $0 Sanción
        if (conduct.startsWith("CORRECCION") && taxPayable.compareTo(BigDecimal.ZERO) == 0) {
            LocalDate firmnessDate = filing.plusYears(3);
            return new SanctionLiquidationResponse(
                    year,
                    uvt,
                    minSanctionRaw,
                    minSanctionRounded,
                    declarationType,
                    conduct,
                    "Art. 588 E.T. y Art. 43 Ley 962 de 2005 (Corrección sin variación de valor a pagar)",
                    due,
                    filing,
                    Math.max(0L, rawDaysBetween),
                    0L,
                    BigDecimal.ZERO,
                    "DELTA_IMPUESTO_CERO_SIN_SANCION",
                    BigDecimal.ZERO,
                    "NO_CAUSA_SANCION",
                    BigDecimal.ZERO,
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    moratoryRate,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    firmnessDate,
                    "Corrección que no incrementa el impuesto a cargo ni disminuye el saldo a favor (Delta $0 COP). No genera sanción por corrección ni sanción mínima."
            );
        }

        long daysOverdue = Math.max(0L, rawDaysBetween);
        long monthsOrFraction = daysOverdue == 0 ? 0L : (long) Math.ceil(daysOverdue / 30.0);

        BigDecimal prelimSanction;
        BigDecimal baseUsed;
        String baseType;
        String legalArticle;

        switch (conduct) {
            case "EXTEMPORANEIDAD_EMPLAZAMIENTO_642" -> {
                legalArticle = "Art. 642 E.T. (Extemporaneidad posterior a Emplazamiento para Declarar)";
                if (taxPayable.compareTo(BigDecimal.ZERO) > 0) {
                    baseUsed = taxPayable;
                    baseType = "IMPUESTO_A_CARGO (10% por mes/fracción, tope 200%)";
                    BigDecimal raw = MoneyCOP.percentage(taxPayable, new BigDecimal("10.0").multiply(BigDecimal.valueOf(monthsOrFraction)));
                    prelimSanction = raw.min(taxPayable.multiply(new BigDecimal("2")));
                } else if (grossIncome.compareTo(BigDecimal.ZERO) > 0) {
                    baseUsed = grossIncome;
                    baseType = "INGRESOS_BRUTOS (1% por mes/fracción, tope 10% / 5.000 UVT)";
                    BigDecimal raw = MoneyCOP.percentage(grossIncome, BigDecimal.ONE.multiply(BigDecimal.valueOf(monthsOrFraction)));
                    prelimSanction = raw.min(MoneyCOP.percentage(grossIncome, BigDecimal.TEN)).min(uvt.multiply(new BigDecimal("5000")));
                } else {
                    baseUsed = netWorth;
                    baseType = "PATRIMONIO_LIQUIDO (2% por mes/fracción, tope 20% / 5.000 UVT)";
                    BigDecimal raw = MoneyCOP.percentage(netWorth, new BigDecimal("2.0").multiply(BigDecimal.valueOf(monthsOrFraction)));
                    prelimSanction = raw.min(MoneyCOP.percentage(netWorth, new BigDecimal("20.0"))).min(uvt.multiply(new BigDecimal("5000")));
                }
            }
            case "CORRECCION_VOLUNTARIA_644" -> {
                legalArticle = "Art. 644 num. 1 E.T. (Sanción por Corrección antes de emplazamiento — 10%)";
                baseUsed = taxPayable;
                baseType = "MAYOR_VALOR_IMPUESTO_O_MENOR_SALDO_A_FAVOR (10%)";
                prelimSanction = MoneyCOP.percentage(taxPayable, BigDecimal.TEN);
            }
            case "CORRECCION_EMPLAZAMIENTO_644" -> {
                legalArticle = "Art. 644 num. 2 E.T. (Sanción por Corrección después de emplazamiento — 20%)";
                baseUsed = taxPayable;
                baseType = "MAYOR_VALOR_IMPUESTO_O_MENOR_SALDO_A_FAVOR (20%)";
                prelimSanction = MoneyCOP.percentage(taxPayable, new BigDecimal("20.0"));
            }
            default -> {
                legalArticle = "Art. 641 E.T. (Extemporaneidad antes de Emplazamiento para Declarar)";
                if (taxPayable.compareTo(BigDecimal.ZERO) > 0) {
                    baseUsed = taxPayable;
                    baseType = "IMPUESTO_A_CARGO (5% por mes o fracción, tope 100%)";
                    BigDecimal raw = MoneyCOP.percentage(taxPayable, new BigDecimal("5.0").multiply(BigDecimal.valueOf(monthsOrFraction)));
                    prelimSanction = raw.min(taxPayable);
                } else if (grossIncome.compareTo(BigDecimal.ZERO) > 0) {
                    baseUsed = grossIncome;
                    baseType = "INGRESOS_BRUTOS (0.5% por mes o fracción, tope 5% / 2.500 UVT)";
                    BigDecimal raw = MoneyCOP.percentage(grossIncome, new BigDecimal("0.5").multiply(BigDecimal.valueOf(monthsOrFraction)));
                    prelimSanction = raw.min(MoneyCOP.percentage(grossIncome, new BigDecimal("5.0"))).min(uvt.multiply(new BigDecimal("2500")));
                } else {
                    baseUsed = netWorth;
                    baseType = "PATRIMONIO_LIQUIDO (1% por mes o fracción, tope 10% / 2.500 UVT)";
                    BigDecimal raw = MoneyCOP.percentage(netWorth, BigDecimal.ONE.multiply(BigDecimal.valueOf(monthsOrFraction)));
                    prelimSanction = raw.min(MoneyCOP.percentage(netWorth, BigDecimal.TEN)).min(uvt.multiply(new BigDecimal("2500")));
                }
            }
        }

        // Principio de Favorabilidad y Gradualidad Art. 640 E.T.
        boolean voluntary = req.voluntaryLiquidationByTaxpayer() == null || req.voluntaryLiquidationByTaxpayer();
        int cleanYears = req.yearsWithoutSameInfraction() != null ? req.yearsWithoutSameInfraction() : 2;

        String gradualityLabel;
        BigDecimal after640BeforeFloor;
        if (voluntary && cleanYears >= 2) {
            gradualityLabel = "REDUCCION_50_PORCIENTO (Art. 640 num. 1 E.T. — Sin antecedente en 2 años)";
            after640BeforeFloor = MoneyCOP.percentage(prelimSanction, new BigDecimal("50.0"));
        } else if (voluntary && cleanYears >= 1) {
            gradualityLabel = "REDUCCION_75_PORCIENTO (Art. 640 num. 2 E.T. — Sin antecedente en 1 año)";
            after640BeforeFloor = MoneyCOP.percentage(prelimSanction, new BigDecimal("75.0"));
        } else {
            gradualityLabel = "SIN_REDUCCION_100_PORCIENTO";
            after640BeforeFloor = prelimSanction;
        }

        boolean floorEnforced = after640BeforeFloor.compareTo(minSanctionRaw) < 0;
        BigDecimal finalExactSanction = after640BeforeFloor.max(minSanctionRaw);
        BigDecimal finalRoundedSanction = MoneyCOP.roundToThousandArt868(finalExactSanction).max(minSanctionRounded);
        BigDecimal savingsFrom640 = prelimSanction.max(minSanctionRaw).subtract(finalExactSanction).max(BigDecimal.ZERO);

        BigDecimal exactInterests = taxPayable
                .multiply(moratoryRate)
                .multiply(BigDecimal.valueOf(daysOverdue))
                .divide(HUNDRED.multiply(DAYS_IN_YEAR), 0, RoundingMode.HALF_UP);
        BigDecimal roundedInterests = MoneyCOP.roundToThousandArt868(exactInterests);

        BigDecimal totalForm490 = roundedCapital.add(finalRoundedSanction).add(roundedInterests);

        LocalDate firmnessDate = filing.plusYears(3);
        String dictum = String.format(
                "Declaración con %d días de mora (%d mes/fracción). Sanción preliminar %s: $%s COP -> Con gradualidad %s y control de piso Art. 639 (10 UVT): $%s COP redondeada + Intereses de mora ($%s COP) = Total Formulario 490: $%s COP. (Nota Art. 714 E.T.: Firmeza general 3 años; 5 años en pérdidas fiscales o precios de transferencia).",
                daysOverdue,
                monthsOrFraction,
                legalArticle,
                prelimSanction.toPlainString(),
                gradualityLabel,
                finalRoundedSanction.toPlainString(),
                roundedInterests.toPlainString(),
                totalForm490.toPlainString()
        );

        return new SanctionLiquidationResponse(
                year,
                uvt,
                minSanctionRaw,
                minSanctionRounded,
                declarationType,
                conduct,
                legalArticle,
                due,
                filing,
                daysOverdue,
                monthsOrFraction,
                baseUsed,
                baseType,
                prelimSanction,
                gradualityLabel,
                after640BeforeFloor,
                floorEnforced,
                finalExactSanction,
                finalRoundedSanction,
                savingsFrom640,
                moratoryRate,
                exactInterests,
                roundedInterests,
                roundedCapital,
                finalRoundedSanction,
                roundedInterests,
                totalForm490,
                firmnessDate,
                dictum
        );
    }
}
