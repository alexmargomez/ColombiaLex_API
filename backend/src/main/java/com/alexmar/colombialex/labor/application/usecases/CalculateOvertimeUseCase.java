package com.alexmar.colombialex.labor.application.usecases;

import com.alexmar.colombialex.labor.domain.strategy.Law2101WorkingHoursStrategy;
import com.alexmar.colombialex.shared.domain.exceptions.DomainValidationException;
import com.alexmar.colombialex.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CalculateOvertimeUseCase {

    private final Law2101WorkingHoursStrategy workingHoursStrategy;

    public CalculateOvertimeUseCase(Law2101WorkingHoursStrategy workingHoursStrategy) {
        this.workingHoursStrategy = workingHoursStrategy;
    }

    public record OvertimeRequest(
            BigDecimal monthlySalary,
            LocalDate calculationDate,
            Integer customMonthlyHoursDivisor,
            BigDecimal extraDaytimeHours,
            BigDecimal extraNightHours,
            BigDecimal nightSurchargeHours,
            BigDecimal sundayHolidayDaytimeHours,
            BigDecimal sundayHolidayNightHours,
            BigDecimal extraSundayHolidayDaytimeHours,
            BigDecimal extraSundayHolidayNightHours
    ) {}

    public record OvertimeLineItem(
            String code,
            String concept,
            String legalBasis,
            BigDecimal multiplierFactor,
            String percentageSurcharge,
            BigDecimal unitHourValueCOP,
            BigDecimal quantityHours,
            BigDecimal totalCOP,
            String formula
    ) {}

    public record OvertimeCalculationResponse(
            BigDecimal monthlySalaryCOP,
            LocalDate referenceDate,
            int maxWeeklyHours,
            BigDecimal monthlyHoursDivisor,
            String law2101Stage,
            BigDecimal ordinaryHourValueCOP,
            BigDecimal totalHoursReported,
            BigDecimal totalOvertimeAndSurchargesCOP,
            BigDecimal totalSalaryPlusOvertimeCOP,
            List<OvertimeLineItem> breakdown
    ) {}

    public OvertimeCalculationResponse execute(OvertimeRequest req) {
        if (req == null || req.monthlySalary() == null || req.monthlySalary().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainValidationException("El salario mensual debe ser mayor a 0 COP.");
        }

        MoneyCOP salary = MoneyCOP.of(req.monthlySalary());
        LocalDate refDate = req.calculationDate() != null ? req.calculationDate() : LocalDate.now();
        var resolution = workingHoursStrategy.resolveForDate(refDate);

        BigDecimal divisor = (req.customMonthlyHoursDivisor() != null && req.customMonthlyHoursDivisor() >= 120)
                ? BigDecimal.valueOf(req.customMonthlyHoursDivisor())
                : resolution.monthlyHoursDivisor();

        MoneyCOP ordinaryHour = salary.divide(divisor);

        List<OvertimeLineItem> items = new ArrayList<>();
        items.add(buildLine(
                "HED",
                "Hora Extra Diurna (6:00 a.m. – 9:00 p.m.)",
                "Art. 168 num. 2 CST",
                new BigDecimal("1.25"),
                "+25%",
                ordinaryHour,
                safeHours(req.extraDaytimeHours())
        ));
        items.add(buildLine(
                "HEN",
                "Hora Extra Nocturna (9:00 p.m. – 6:00 a.m.)",
                "Art. 168 num. 3 CST",
                new BigDecimal("1.75"),
                "+75%",
                ordinaryHour,
                safeHours(req.extraNightHours())
        ));
        items.add(buildLine(
                "RN",
                "Recargo Nocturno Ordinario (9:00 p.m. – 6:00 a.m.)",
                "Art. 168 num. 1 CST",
                new BigDecimal("0.35"),
                "+35% (solo recargo)",
                ordinaryHour,
                safeHours(req.nightSurchargeHours())
        ));
        items.add(buildLine(
                "HRDDF",
                "Hora Dominical o Festiva Diurna",
                "Art. 179 CST",
                new BigDecimal("1.75"),
                "+75%",
                ordinaryHour,
                safeHours(req.sundayHolidayDaytimeHours())
        ));
        items.add(buildLine(
                "HRNDF",
                "Hora Dominical o Festiva Nocturna (75% + 35%)",
                "Art. 168 y 179 CST",
                new BigDecimal("2.10"),
                "+110%",
                ordinaryHour,
                safeHours(req.sundayHolidayNightHours())
        ));
        items.add(buildLine(
                "HEDDF",
                "Hora Extra Diurna Dominical o Festiva (75% + 25%)",
                "Art. 168 y 179 CST",
                new BigDecimal("2.00"),
                "+100%",
                ordinaryHour,
                safeHours(req.extraSundayHolidayDaytimeHours())
        ));
        items.add(buildLine(
                "HENDF",
                "Hora Extra Nocturna Dominical o Festiva (75% + 75%)",
                "Art. 168 y 179 CST",
                new BigDecimal("2.50"),
                "+150%",
                ordinaryHour,
                safeHours(req.extraSundayHolidayNightHours())
        ));

        BigDecimal totalHours = items.stream()
                .map(OvertimeLineItem::quantityHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOvertime = items.stream()
                .map(OvertimeLineItem::totalCOP)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal grandTotal = salary.roundedPesosValue().add(totalOvertime);

        return new OvertimeCalculationResponse(
                salary.roundedPesosValue(),
                refDate,
                resolution.maxWeeklyHours(),
                divisor,
                resolution.legalStageDescription(),
                ordinaryHour.value().setScale(2, RoundingMode.HALF_UP),
                totalHours,
                totalOvertime,
                grandTotal,
                items
        );
    }

    private BigDecimal safeHours(BigDecimal h) {
        if (h == null || h.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return h.setScale(2, RoundingMode.HALF_UP);
    }

    private OvertimeLineItem buildLine(
            String code,
            String concept,
            String legalBasis,
            BigDecimal factor,
            String percentageSurcharge,
            MoneyCOP ordinaryHour,
            BigDecimal qtyHours
    ) {
        MoneyCOP unitRate = ordinaryHour.multiply(factor);
        MoneyCOP total = unitRate.multiply(qtyHours).roundToPesos();
        String formula = String.format("Hora Ordinaria ($%s) × %s × %s h = $%s COP",
                ordinaryHour.value().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                factor.toPlainString(),
                qtyHours.toPlainString(),
                total.roundedPesosValue().toPlainString());

        return new OvertimeLineItem(
                code,
                concept,
                legalBasis,
                factor,
                percentageSurcharge,
                unitRate.value().setScale(2, RoundingMode.HALF_UP),
                qtyHours,
                total.roundedPesosValue(),
                formula
        );
    }
}
