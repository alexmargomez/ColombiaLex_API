package com.alexmar.colombialex.labor.application.usecases;

import com.alexmar.colombialex.labor.domain.model.LaborLegalConstants;
import com.alexmar.colombialex.labor.domain.specification.LaborSpecifications;
import com.alexmar.colombialex.labor.domain.strategy.SeveranceIndemnificationStrategy;
import com.alexmar.colombialex.shared.domain.exceptions.DomainValidationException;
import com.alexmar.colombialex.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Use Case + Chain of Responsibility + Builder Pattern —
 * Liquidación integral de Prestaciones Sociales (Cesantías, Intereses 12%, Prima, Vacaciones),
 * Seguridad Social (Empleado y Empleador), Parafiscales e Indemnización por Despido (Art. 64 CST).
 */
@Service
public class CalculateContractSettlementUseCase {

    private final LaborSpecifications laborSpecs;
    private final SeveranceIndemnificationStrategy indemnificationStrategy;

    public CalculateContractSettlementUseCase(
            LaborSpecifications laborSpecs,
            SeveranceIndemnificationStrategy indemnificationStrategy
    ) {
        this.laborSpecs = laborSpecs;
        this.indemnificationStrategy = indemnificationStrategy;
    }

    public record SettlementRequest(
            Integer year,
            BigDecimal monthlyBaseSalary,
            BigDecimal monthlyVariableCommissions,
            Integer workedDaysTotal,
            Integer workedDaysCurrentYear,
            Integer workedDaysCurrentSemester,
            Integer pendingVacationDaysAccounted,
            String contractType,
            Boolean dismissalWithoutJustCause,
            Integer remainingDaysFixedTerm,
            String arlRiskClass,
            Boolean applyArt114Exoneration
    ) {}

    public record FormulaBreakdownStep(
            String code,
            String title,
            String legalReference,
            String formulaApplied,
            BigDecimal amountCOP
    ) {}

    public record SocialSecurityMonthlySummary(
            BigDecimal ibcCOP,
            BigDecimal employeeHealth4PercentCOP,
            BigDecimal employeePension4PercentCOP,
            BigDecimal solidarityFundRate,
            BigDecimal employeeSolidarityFundCOP,
            BigDecimal totalEmployeeDeductionsCOP,
            boolean exoneratedArt114ET,
            BigDecimal employerHealthCOP,
            BigDecimal employerPension12PercentCOP,
            String arlRiskClass,
            BigDecimal arlRate,
            BigDecimal employerArlCOP,
            BigDecimal employerCompensationFund4PercentCOP,
            BigDecimal employerIcbf3PercentCOP,
            BigDecimal employerSena2PercentCOP,
            BigDecimal totalEmployerMonthlyContributionsCOP
    ) {}

    public record SettlementResponse(
            int referenceYear,
            BigDecimal smlmvYearCOP,
            BigDecimal transportAllowanceYearCOP,
            boolean eligibleForTransportAllowance,
            BigDecimal monthlyBaseSalaryCOP,
            BigDecimal monthlyVariableAverageCOP,
            BigDecimal transportAllowanceAppliedCOP,
            BigDecimal baseBenefitsLiquidationSalaryCOP,
            BigDecimal baseVacationsSalaryCOP,
            int workedDaysTotal,
            int workedDaysCurrentYear,
            int workedDaysCurrentSemester,
            BigDecimal severanceCesantiasCOP,
            BigDecimal severanceInterests12PercentCOP,
            BigDecimal serviceBonusPrimaCOP,
            BigDecimal vacationsCompensatedCOP,
            BigDecimal totalSocialBenefitsCOP,
            SeveranceIndemnificationStrategy.IndemnificationResult indemnification,
            SocialSecurityMonthlySummary monthlySocialSecurity,
            BigDecimal grandTotalSettlementToEmployeeCOP,
            List<FormulaBreakdownStep> formulaBreakdown
    ) {}

    public SettlementResponse execute(SettlementRequest req) {
        if (req == null || req.monthlyBaseSalary() == null || req.monthlyBaseSalary().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainValidationException("El salario básico mensual debe ser mayor a 0 COP.");
        }

        int year = req.year() != null ? req.year() : 2026;
        var legalParams = LaborLegalConstants.forYear(year);
        MoneyCOP smlmv = MoneyCOP.of(legalParams.smlmv());
        MoneyCOP legalTransport = MoneyCOP.of(legalParams.transportAllowance());

        MoneyCOP baseSalary = MoneyCOP.of(req.monthlyBaseSalary());
        MoneyCOP variableAvg = req.monthlyVariableCommissions() != null && req.monthlyVariableCommissions().compareTo(BigDecimal.ZERO) > 0
                ? MoneyCOP.of(req.monthlyVariableCommissions())
                : MoneyCOP.ZERO;

        MoneyCOP totalSalaryEarned = baseSalary.add(variableAvg);

        boolean eligibleTransport = laborSpecs.isEligibleForTransportAllowance(totalSalaryEarned, smlmv);
        MoneyCOP transportApplied = eligibleTransport ? legalTransport : MoneyCOP.ZERO;

        // Salario base para Cesantías y Prima incluye auxilio de transporte si devenga <= 2 SMLMV
        MoneyCOP benefitsBaseSalary = totalSalaryEarned.add(transportApplied);
        // Salario base para Vacaciones NO incluye auxilio de transporte (Art. 192 CST)
        MoneyCOP vacationsBaseSalary = totalSalaryEarned;

        int totalDays = req.workedDaysTotal() != null && req.workedDaysTotal() > 0 ? req.workedDaysTotal() : 360;
        int daysYear = req.workedDaysCurrentYear() != null && req.workedDaysCurrentYear() > 0
                ? Math.min(360, req.workedDaysCurrentYear())
                : Math.min(360, totalDays);
        int daysSemester = req.workedDaysCurrentSemester() != null && req.workedDaysCurrentSemester() > 0
                ? Math.min(180, req.workedDaysCurrentSemester())
                : Math.min(180, daysYear);
        int daysForVacations = req.pendingVacationDaysAccounted() != null && req.pendingVacationDaysAccounted() > 0
                ? req.pendingVacationDaysAccounted()
                : totalDays;

        List<FormulaBreakdownStep> steps = new ArrayList<>();

        // 1. Cesantías (Art. 249 CST): (Salario Base * Días Año) / 360
        MoneyCOP cesantias = benefitsBaseSalary
                .multiply(daysYear)
                .divide(new BigDecimal("360"))
                .roundToPesos();
        steps.add(new FormulaBreakdownStep(
                "CESANTIAS",
                "Auxilio de Cesantías",
                "Art. 249 Código Sustantivo del Trabajo",
                String.format("(%s × %d días) / 360", benefitsBaseSalary, daysYear),
                cesantias.roundedPesosValue()
        ));

        // 2. Intereses sobre Cesantías (Ley 52 de 1975): (Cesantías * Días Año * 0.12) / 360
        MoneyCOP interesesCesantias = cesantias
                .multiply(daysYear)
                .multiply(new BigDecimal("0.12"))
                .divide(new BigDecimal("360"))
                .roundToPesos();
        steps.add(new FormulaBreakdownStep(
                "INTERESES_CESANTIAS",
                "Intereses Legales sobre Cesantías (12% anual)",
                "Ley 52 de 1975 Art. 1",
                String.format("(%s × %d días × 0.12) / 360", cesantias, daysYear),
                interesesCesantias.roundedPesosValue()
        ));

        // 3. Prima de Servicios (Art. 306 CST): (Salario Base * Días Semestre) / 360
        MoneyCOP prima = benefitsBaseSalary
                .multiply(daysSemester)
                .divide(new BigDecimal("360"))
                .roundToPesos();
        steps.add(new FormulaBreakdownStep(
                "PRIMA_SERVICIOS",
                "Prima Legal de Servicios",
                "Art. 306 Código Sustantivo del Trabajo",
                String.format("(%s × %d días del semestre) / 360", benefitsBaseSalary, daysSemester),
                prima.roundedPesosValue()
        ));

        // 4. Vacaciones Compensadas (Art. 186 / Ley 995 de 2005): (Salario sin aux. transporte * Días) / 720
        MoneyCOP vacaciones = vacationsBaseSalary
                .multiply(daysForVacations)
                .divide(new BigDecimal("720"))
                .roundToPesos();
        steps.add(new FormulaBreakdownStep(
                "VACACIONES",
                "Vacaciones Compensadas en Dinero",
                "Art. 186 y 192 CST / Ley 995 de 2005",
                String.format("(%s sin aux. transporte × %d días) / 720", vacationsBaseSalary, daysForVacations),
                vacaciones.roundedPesosValue()
        ));

        MoneyCOP totalBenefits = cesantias.add(interesesCesantias).add(prima).add(vacaciones);

        // 5. Indemnización por Despido sin Justa Causa (Strategy Pattern - Art. 64 CST)
        boolean withoutJustCause = Boolean.TRUE.equals(req.dismissalWithoutJustCause());
        int remainingFixedDays = req.remainingDaysFixedTerm() != null ? req.remainingDaysFixedTerm() : 60;
        var indemResult = indemnificationStrategy.calculate(
                req.contractType(),
                withoutJustCause,
                totalSalaryEarned,
                smlmv,
                totalDays,
                remainingFixedDays
        );
        if (withoutJustCause) {
            steps.add(new FormulaBreakdownStep(
                    "INDEMNIZACION_ART64",
                    "Indemnización por Despido sin Justa Causa",
                    indemResult.legalRuleApplied(),
                    indemResult.formulaExplanation(),
                    indemResult.amountCOP()
            ));
        }

        // 6. Seguridad Social y Parafiscales Mensuales (Ley 100 de 1993 + Art. 114-1 E.T.)
        MoneyCOP ibc = totalSalaryEarned.isLessThanOrEqualTo(smlmv) ? smlmv : totalSalaryEarned;
        MoneyCOP empHealth = ibc.multiply(new BigDecimal("0.04")).roundToPesos();
        MoneyCOP empPension = ibc.multiply(new BigDecimal("0.04")).roundToPesos();
        BigDecimal fspRate = laborSpecs.resolveSolidarityFundRate(ibc, smlmv);
        MoneyCOP empFsp = ibc.multiply(fspRate).roundToPesos();
        MoneyCOP totalEmpDeductions = empHealth.add(empPension).add(empFsp);

        boolean applyExoneration = req.applyArt114Exoneration() == null || req.applyArt114Exoneration();
        boolean exonerated = laborSpecs.isExoneratedUnderArt114ET(ibc, smlmv, applyExoneration);

        MoneyCOP employerHealth = exonerated ? MoneyCOP.ZERO : ibc.multiply(new BigDecimal("0.085")).roundToPesos();
        MoneyCOP employerPension = ibc.multiply(new BigDecimal("0.12")).roundToPesos();
        String arlClass = req.arlRiskClass() != null ? req.arlRiskClass().trim().toUpperCase() : "I";
        BigDecimal arlRate = LaborLegalConstants.arlRateForRiskClass(arlClass);
        MoneyCOP employerArl = ibc.multiply(arlRate).roundToPesos();
        MoneyCOP employerCaja = ibc.multiply(new BigDecimal("0.04")).roundToPesos();
        MoneyCOP employerIcbf = exonerated ? MoneyCOP.ZERO : ibc.multiply(new BigDecimal("0.03")).roundToPesos();
        MoneyCOP employerSena = exonerated ? MoneyCOP.ZERO : ibc.multiply(new BigDecimal("0.02")).roundToPesos();

        MoneyCOP totalEmployer = employerHealth
                .add(employerPension)
                .add(employerArl)
                .add(employerCaja)
                .add(employerIcbf)
                .add(employerSena);

        var socialSecuritySummary = new SocialSecurityMonthlySummary(
                ibc.roundedPesosValue(),
                empHealth.roundedPesosValue(),
                empPension.roundedPesosValue(),
                fspRate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP),
                empFsp.roundedPesosValue(),
                totalEmpDeductions.roundedPesosValue(),
                exonerated,
                employerHealth.roundedPesosValue(),
                employerPension.roundedPesosValue(),
                arlClass,
                arlRate.multiply(new BigDecimal("100")).setScale(3, RoundingMode.HALF_UP),
                employerArl.roundedPesosValue(),
                employerCaja.roundedPesosValue(),
                employerIcbf.roundedPesosValue(),
                employerSena.roundedPesosValue(),
                totalEmployer.roundedPesosValue()
        );

        BigDecimal grandTotalSettlement = totalBenefits.roundedPesosValue().add(indemResult.amountCOP());

        return new SettlementResponse(
                legalParams.year(),
                smlmv.roundedPesosValue(),
                legalTransport.roundedPesosValue(),
                eligibleTransport,
                baseSalary.roundedPesosValue(),
                variableAvg.roundedPesosValue(),
                transportApplied.roundedPesosValue(),
                benefitsBaseSalary.roundedPesosValue(),
                vacationsBaseSalary.roundedPesosValue(),
                totalDays,
                daysYear,
                daysSemester,
                cesantias.roundedPesosValue(),
                interesesCesantias.roundedPesosValue(),
                prima.roundedPesosValue(),
                vacaciones.roundedPesosValue(),
                totalBenefits.roundedPesosValue(),
                indemResult,
                socialSecuritySummary,
                grandTotalSettlement,
                steps
        );
    }
}
