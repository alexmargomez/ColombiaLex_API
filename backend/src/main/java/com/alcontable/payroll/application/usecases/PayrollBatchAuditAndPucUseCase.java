package com.alcontable.payroll.application.usecases;

import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.payroll.domain.specification.LaborSpecifications;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PayrollBatchAuditAndPucUseCase {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal FORTY_PERCENT = new BigDecimal("0.40");
    private static final BigDecimal MAX_IBC_SMLMV_MULTIPLIER = new BigDecimal("25");
    private static final BigDecimal THIRTY_DAYS = new BigDecimal("30");

    private final LaborSpecifications laborSpecifications;

    public PayrollBatchAuditAndPucUseCase(LaborSpecifications laborSpecifications) {
        this.laborSpecifications = laborSpecifications;
    }

    public record PayrollEmployeeInput(
            String document,
            String employeeName,
            String area, // ADMINISTRACION (5105) o VENTAS (5205)
            BigDecimal baseSalaryCOP,
            BigDecimal overtimeAndSurchargesCOP,
            BigDecimal commissionsCOP,
            BigDecimal nonSalaryPaymentsArt128COP,
            String arlRiskClass,
            Boolean integralSalary,
            Integer workedDays
    ) {
        // Constructor retrocompatible de 9 argumentos (asume 30 días laborados comerciales)
        public PayrollEmployeeInput(
                String document,
                String employeeName,
                String area,
                BigDecimal baseSalaryCOP,
                BigDecimal overtimeAndSurchargesCOP,
                BigDecimal commissionsCOP,
                BigDecimal nonSalaryPaymentsArt128COP,
                String arlRiskClass,
                Boolean integralSalary
        ) {
            this(document, employeeName, area, baseSalaryCOP, overtimeAndSurchargesCOP,
                    commissionsCOP, nonSalaryPaymentsArt128COP, arlRiskClass, integralSalary, 30);
        }
    }

    public record PayrollBatchRequest(
            Integer year,
            Boolean employerAppliesArt1141Exoneration,
            List<PayrollEmployeeInput> employees
    ) {}

    public record EmployeeAuditResult(
            String document,
            String employeeName,
            String area,
            BigDecimal baseSalaryCOP,
            BigDecimal overtimeAndSurchargesCOP,
            BigDecimal commissionsCOP,
            BigDecimal totalSalaryEarnedCOP,
            BigDecimal transportAllowanceCOP,
            BigDecimal nonSalaryPaymentsArt128COP,
            BigDecimal totalRemunerationForUgppCOP,
            BigDecimal nonSalarySharePercent,
            BigDecimal ugpp40PercentCapCOP,
            BigDecimal ugppExcessGravadoCOP,
            boolean ugppRiskDetected,
            BigDecimal ibcSeguridadSocialCOP,
            BigDecimal ibcIfCompanyIgnoredUgppCOP,
            BigDecimal omittedPilaContributionsMonthCOP,
            boolean exoneratedArt1141,
            BigDecimal employeeHealth4PercentCOP,
            BigDecimal employeePension4PercentCOP,
            BigDecimal employeeFspRatePercent,
            BigDecimal employeeFspAmountCOP,
            BigDecimal totalEmployeeDeductionsCOP,
            BigDecimal netPayToEmployeeCOP,
            BigDecimal employerHealthCOP,
            BigDecimal employerPension12PercentCOP,
            BigDecimal employerArlCOP,
            BigDecimal employerCaja4PercentCOP,
            BigDecimal employerIcbfCOP,
            BigDecimal employerSenaCOP,
            BigDecimal totalEmployerPilaAndParafiscalesCOP,
            BigDecimal provisionCesantiasCOP,
            BigDecimal provisionInteresesCesantiasCOP,
            BigDecimal provisionPrimaCOP,
            BigDecimal provisionVacacionesCOP,
            BigDecimal totalMonthlyProvisionsCOP,
            BigDecimal totalEmployerRealMonthlyCostCOP
    ) {}

    public record PucJournalLine(
            String pucCode,
            String accountName,
            String costCenterArea,
            String nature, // DEBITO, CREDITO
            BigDecimal debitCOP,
            BigDecimal creditCOP,
            String accountingNote
    ) {}

    public record PayrollBatchAuditResponse(
            int referenceYear,
            BigDecimal smlmvCOP,
            BigDecimal transportAllowanceLegalCOP,
            int employeesProcessed,
            int employeesWithUgppRiskCount,
            BigDecimal totalPayrollBaseSalaryCOP,
            BigDecimal totalNonSalaryPaymentsCOP,
            BigDecimal totalUgppExcessGravadoCOP,
            BigDecimal totalMonthlyOmittedPilaRiskCOP,
            BigDecimal totalAnnualOmittedPilaRiskCOP,
            BigDecimal totalNetPayDisbursementCOP,
            BigDecimal totalEmployerRealCostCOP,
            BigDecimal totalPucDebitCOP,
            BigDecimal totalPucCreditCOP,
            boolean pucDoubleEntryBalanced,
            List<EmployeeAuditResult> employeeResults,
            List<PucJournalLine> pucJournalEntries
    ) {}

    public PayrollBatchAuditResponse execute(PayrollBatchRequest request) {
        if (request == null || request.employees() == null || request.employees().isEmpty()) {
            throw new DomainValidationException("Debe incluir al menos un empleado en la planilla de nómina para auditar.");
        }

        int year = request.year() != null ? request.year() : 2026;
        boolean apply114 = request.employerAppliesArt1141Exoneration() == null || request.employerAppliesArt1141Exoneration();
        var params = LaborLegalConstants.forYear(year);
        BigDecimal smlmv = params.smlmvCOP();
        BigDecimal maxIbc = smlmv.multiply(MAX_IBC_SMLMV_MULTIPLIER);

        List<EmployeeAuditResult> results = new ArrayList<>();

        int ugppRiskCount = 0;
        BigDecimal sumBaseSalary = BigDecimal.ZERO;
        BigDecimal sumOvertime = BigDecimal.ZERO;
        BigDecimal sumCommissions = BigDecimal.ZERO;
        BigDecimal sumTransport = BigDecimal.ZERO;
        BigDecimal sumNonSalary = BigDecimal.ZERO;
        BigDecimal sumUgppExcess = BigDecimal.ZERO;
        BigDecimal sumOmittedMonth = BigDecimal.ZERO;
        BigDecimal sumNetPay = BigDecimal.ZERO;
        BigDecimal sumEmployerCost = BigDecimal.ZERO;

        BigDecimal sumEmpHealth = BigDecimal.ZERO;
        BigDecimal sumEmpPension = BigDecimal.ZERO;
        BigDecimal sumEmpFsp = BigDecimal.ZERO;
        BigDecimal sumPatronHealth = BigDecimal.ZERO;
        BigDecimal sumPatronPension = BigDecimal.ZERO;
        BigDecimal sumPatronArl = BigDecimal.ZERO;
        BigDecimal sumPatronCaja = BigDecimal.ZERO;
        BigDecimal sumPatronIcbf = BigDecimal.ZERO;
        BigDecimal sumPatronSena = BigDecimal.ZERO;

        BigDecimal sumProvCesantias = BigDecimal.ZERO;
        BigDecimal sumProvIntereses = BigDecimal.ZERO;
        BigDecimal sumProvPrima = BigDecimal.ZERO;
        BigDecimal sumProvVacaciones = BigDecimal.ZERO;

        for (PayrollEmployeeInput emp : request.employees()) {
            if (emp == null) {
                throw new DomainValidationException("La planilla de nómina contiene un registro de empleado nulo.");
            }
            int workedDays = emp.workedDays() != null ? emp.workedDays() : 30;
            if (workedDays <= 0 || workedDays > 30) {
                throw new DomainValidationException("Los días laborados del empleado deben estar entre 1 y 30 días comerciales.");
            }

            BigDecimal contractBaseSalary = MoneyCOP.requireNonNegative(emp.baseSalaryCOP(), "baseSalaryCOP");
            if (contractBaseSalary.compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainValidationException("El salario básico de cada empleado debe ser mayor a $0 COP.");
            }
            BigDecimal overtime = MoneyCOP.requireNonNegative(emp.overtimeAndSurchargesCOP(), "overtimeAndSurchargesCOP");
            BigDecimal commissions = MoneyCOP.requireNonNegative(emp.commissionsCOP(), "commissionsCOP");
            BigDecimal nonSalary = MoneyCOP.requireNonNegative(emp.nonSalaryPaymentsArt128COP(), "nonSalaryPaymentsArt128COP");

            boolean integral = Boolean.TRUE.equals(emp.integralSalary());
            BigDecimal minIntegralSalary = smlmv.multiply(new BigDecimal("13"));
            if (integral && contractBaseSalary.compareTo(minIntegralSalary) < 0) {
                throw new DomainValidationException(
                        "El salario integral del empleado '" + emp.employeeName() + "' debe ser de al menos 13 SMLMV ($"
                                + minIntegralSalary.toPlainString() + " COP)."
                );
            }

            BigDecimal baseSalary = workedDays == 30
                    ? contractBaseSalary
                    : contractBaseSalary.multiply(BigDecimal.valueOf(workedDays)).divide(THIRTY_DAYS, 0, RoundingMode.HALF_UP);

            String area = (emp.area() != null && emp.area().trim().equalsIgnoreCase("VENTAS")) ? "VENTAS" : "ADMINISTRACION";
            BigDecimal arlRate = LaborLegalConstants.arlRateForRiskClass(emp.arlRiskClass()).multiply(HUNDRED);

            BigDecimal totalSalaryEarned = baseSalary.add(overtime).add(commissions).setScale(0, RoundingMode.HALF_UP);
            boolean eligibleTransport = !integral && laborSpecifications.isEligibleForTransportAllowance(contractBaseSalary.add(overtime).add(commissions), year);
            BigDecimal fullTransport = eligibleTransport ? params.transportAllowanceCOP() : BigDecimal.ZERO;
            BigDecimal transport = workedDays == 30
                    ? fullTransport
                    : fullTransport.multiply(BigDecimal.valueOf(workedDays)).divide(THIRTY_DAYS, 0, RoundingMode.HALF_UP);

            // Regla UGPP Art. 30 Ley 1393 de 2010:
            // Total remuneración para el cálculo del 40% excluye el auxilio legal de transporte
            BigDecimal totalRemunerationUgpp = totalSalaryEarned.add(nonSalary);
            BigDecimal ugpp40Cap = totalRemunerationUgpp.multiply(FORTY_PERCENT).setScale(0, RoundingMode.HALF_UP);
            BigDecimal ugppExcess = nonSalary.subtract(ugpp40Cap).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
            boolean hasUgppRisk = ugppExcess.compareTo(BigDecimal.ZERO) > 0;
            if (hasUgppRisk) {
                ugppRiskCount++;
            }

            BigDecimal nonSalarySharePct = totalRemunerationUgpp.compareTo(BigDecimal.ZERO) > 0
                    ? nonSalary.multiply(HUNDRED).divide(totalRemunerationUgpp, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal baseSalaryForIbc = integral
                    ? totalSalaryEarned.multiply(new BigDecimal("0.70")).setScale(0, RoundingMode.HALF_UP)
                    : totalSalaryEarned;

            BigDecimal minIbcProportional = workedDays == 30
                    ? smlmv
                    : smlmv.multiply(BigDecimal.valueOf(workedDays)).divide(THIRTY_DAYS, 0, RoundingMode.HALF_UP);

            BigDecimal ibcReal = baseSalaryForIbc.add(ugppExcess).max(minIbcProportional).min(maxIbc).setScale(0, RoundingMode.HALF_UP);
            BigDecimal ibcWithoutUgpp = baseSalaryForIbc.max(minIbcProportional).min(maxIbc).setScale(0, RoundingMode.HALF_UP);

            boolean exonerated114 = laborSpecifications.isExoneratedFromParafiscalesArt114ET(totalSalaryEarned, year, apply114);

            // Deducciones Trabajador
            BigDecimal empHealth = MoneyCOP.percentage(ibcReal, new BigDecimal("4.0"));
            BigDecimal empPension = MoneyCOP.percentage(ibcReal, new BigDecimal("4.0"));
            BigDecimal fspRate = laborSpecifications.resolveSolidarityFundRatePercent(ibcReal, year);
            BigDecimal empFsp = MoneyCOP.percentage(ibcReal, fspRate);
            BigDecimal totalEmpDeductions = empHealth.add(empPension).add(empFsp);

            BigDecimal grossToReceive = totalSalaryEarned.add(transport).add(nonSalary);
            if (totalEmpDeductions.compareTo(grossToReceive) >= 0) {
                throw new DomainValidationException(
                        "El neto a pagar del empleado '" + emp.employeeName() + "' no puede ser negativo ni cero. "
                                + "Verifique que el salario básico cumpla con el piso legal proporcional al período."
                );
            }
            BigDecimal netPay = grossToReceive.subtract(totalEmpDeductions).setScale(0, RoundingMode.HALF_UP);

            // Aportes Empleador Seguridad Social (sobre ibcReal con exceso UGPP) y Parafiscales (sobre base salarial)
            BigDecimal patronHealthRate = exonerated114 ? BigDecimal.ZERO : new BigDecimal("8.5");
            BigDecimal patronHealth = MoneyCOP.percentage(ibcReal, patronHealthRate);
            BigDecimal patronPension = MoneyCOP.percentage(ibcReal, new BigDecimal("12.0"));
            BigDecimal patronArl = MoneyCOP.percentage(ibcReal, arlRate);

            BigDecimal ibcParafiscales = baseSalaryForIbc.max(minIbcProportional).min(maxIbc);
            BigDecimal patronCaja = MoneyCOP.percentage(ibcParafiscales, new BigDecimal("4.0"));
            BigDecimal patronIcbf = exonerated114 ? BigDecimal.ZERO : MoneyCOP.percentage(ibcParafiscales, new BigDecimal("3.0"));
            BigDecimal patronSena = exonerated114 ? BigDecimal.ZERO : MoneyCOP.percentage(ibcParafiscales, new BigDecimal("2.0"));
            BigDecimal totalPatronPila = patronHealth.add(patronPension).add(patronArl).add(patronCaja).add(patronIcbf).add(patronSena);

            // Aporte omitido ante UGPP (diferencia entre cotizar con exceso 40% vs cotizar sin exceso)
            BigDecimal combinedSsRateOnExcess = new BigDecimal("24.0")
                    .add(patronHealthRate)
                    .add(arlRate)
                    .add(fspRate);
            BigDecimal omittedMonth = MoneyCOP.percentage(ibcReal.subtract(ibcWithoutUgpp).max(BigDecimal.ZERO), combinedSsRateOnExcess);

            // Provisiones mensuales de prestaciones sociales
            BigDecimal basePrestaciones = integral ? BigDecimal.ZERO : totalSalaryEarned.add(transport);
            BigDecimal provCesantias = integral ? BigDecimal.ZERO : MoneyCOP.percentage(basePrestaciones, new BigDecimal("8.3333"));
            BigDecimal provIntereses = integral ? BigDecimal.ZERO : MoneyCOP.percentage(provCesantias, new BigDecimal("12.0"));
            BigDecimal provPrima = integral ? BigDecimal.ZERO : MoneyCOP.percentage(basePrestaciones, new BigDecimal("8.3333"));
            // Vacaciones sobre sueldo básico + comisiones sin horas extra ni auxilio de transporte (Art. 192 CST)
            BigDecimal baseVacaciones = baseSalary.add(commissions);
            BigDecimal provVacaciones = MoneyCOP.percentage(baseVacaciones, new BigDecimal("4.1667"));
            BigDecimal totalProvisions = provCesantias.add(provIntereses).add(provPrima).add(provVacaciones);

            BigDecimal totalRealEmployerCost = totalSalaryEarned
                    .add(transport)
                    .add(nonSalary)
                    .add(totalPatronPila)
                    .add(totalProvisions);

            results.add(new EmployeeAuditResult(
                    emp.document() != null && !emp.document().isBlank() ? emp.document().trim() : "N/A",
                    emp.employeeName() != null && !emp.employeeName().isBlank() ? emp.employeeName().trim() : "Empleado",
                    area,
                    baseSalary,
                    overtime,
                    commissions,
                    totalSalaryEarned,
                    transport,
                    nonSalary,
                    totalRemunerationUgpp,
                    nonSalarySharePct,
                    ugpp40Cap,
                    ugppExcess,
                    hasUgppRisk,
                    ibcReal,
                    ibcWithoutUgpp,
                    omittedMonth,
                    exonerated114,
                    empHealth,
                    empPension,
                    fspRate,
                    empFsp,
                    totalEmpDeductions,
                    netPay,
                    patronHealth,
                    patronPension,
                    patronArl,
                    patronCaja,
                    patronIcbf,
                    patronSena,
                    totalPatronPila,
                    provCesantias,
                    provIntereses,
                    provPrima,
                    provVacaciones,
                    totalProvisions,
                    totalRealEmployerCost
            ));

            sumBaseSalary = sumBaseSalary.add(baseSalary);
            sumOvertime = sumOvertime.add(overtime);
            sumCommissions = sumCommissions.add(commissions);
            sumTransport = sumTransport.add(transport);
            sumNonSalary = sumNonSalary.add(nonSalary);
            sumUgppExcess = sumUgppExcess.add(ugppExcess);
            sumOmittedMonth = sumOmittedMonth.add(omittedMonth);
            sumNetPay = sumNetPay.add(netPay);
            sumEmployerCost = sumEmployerCost.add(totalRealEmployerCost);

            sumEmpHealth = sumEmpHealth.add(empHealth);
            sumEmpPension = sumEmpPension.add(empPension);
            sumEmpFsp = sumEmpFsp.add(empFsp);
            sumPatronHealth = sumPatronHealth.add(patronHealth);
            sumPatronPension = sumPatronPension.add(patronPension);
            sumPatronArl = sumPatronArl.add(patronArl);
            sumPatronCaja = sumPatronCaja.add(patronCaja);
            sumPatronIcbf = sumPatronIcbf.add(patronIcbf);
            sumPatronSena = sumPatronSena.add(patronSena);

            sumProvCesantias = sumProvCesantias.add(provCesantias);
            sumProvIntereses = sumProvIntereses.add(provIntereses);
            sumProvPrima = sumProvPrima.add(provPrima);
            sumProvVacaciones = sumProvVacaciones.add(provVacaciones);
        }

        // Construcción del Comprobante Contable PUC de 6 dígitos (Partida Doble Exacta)
        List<PucJournalLine> journal = new ArrayList<>();
        addDebitIfPositive(journal, "510506", "Sueldos Básicos Ordinarios", "ADMIN/VENTAS", sumBaseSalary, "Causación sueldos básicos del período");
        addDebitIfPositive(journal, "510515", "Horas Extras y Recargos (Ley 2101)", "ADMIN/VENTAS", sumOvertime, "Causación horas extra y recargos");
        addDebitIfPositive(journal, "510518", "Comisiones y Variables Salariales", "VENTAS", sumCommissions, "Causación comisiones salariales");
        addDebitIfPositive(journal, "510527", "Auxilio Legal de Transporte / Conectividad", "ADMIN/VENTAS", sumTransport, "Auxilio legal <= 2 SMLMV");
        addDebitIfPositive(journal, "510548", "Bonificaciones y Auxilios No Salariales (Art. 128 CST)", "ADMIN/VENTAS", sumNonSalary, "Auditados bajo tope 40% Ley 1393 UGPP");
        addDebitIfPositive(journal, "510530", "Cesantías (Gasto Provisión Mensual 8.33%)", "ADMIN/VENTAS", sumProvCesantias, "Provisión mensual Art. 249 CST");
        addDebitIfPositive(journal, "510533", "Intereses sobre Cesantías (Gasto Provisión 1%)", "ADMIN/VENTAS", sumProvIntereses, "Provisión mensual 12% anual");
        addDebitIfPositive(journal, "510536", "Prima de Servicios (Gasto Provisión Mensual 8.33%)", "ADMIN/VENTAS", sumProvPrima, "Provisión mensual Art. 306 CST");
        addDebitIfPositive(journal, "510539", "Vacaciones (Gasto Provisión Mensual 4.17%)", "ADMIN/VENTAS", sumProvVacaciones, "Provisión mensual Art. 192 CST");
        addDebitIfPositive(journal, "510568", "Aportes a Administradoras de Riesgos Laborales (ARL)", "ADMIN/VENTAS", sumPatronArl, "Aporte patronal ARL sobre IBC UGPP");
        addDebitIfPositive(journal, "510569", "Aportes a Entidades Promotoras de Salud (EPS)", "ADMIN/VENTAS", sumPatronHealth, "Aporte patronal EPS (0% si aplica Art. 114-1 E.T.)");
        addDebitIfPositive(journal, "510570", "Aportes a Fondos de Pensiones (12% Patronal)", "ADMIN/VENTAS", sumPatronPension, "Aporte patronal obligatorio pensión");
        addDebitIfPositive(journal, "510572", "Aportes Cajas de Compensación Familiar (4%)", "ADMIN/VENTAS", sumPatronCaja, "Aporte parafiscal CCF 4%");
        addDebitIfPositive(journal, "510575", "Aportes ICBF (3%)", "ADMIN/VENTAS", sumPatronIcbf, "Exonerado si < 10 SMLMV Art. 114-1 E.T.");
        addDebitIfPositive(journal, "510578", "Aportes SENA (2%)", "ADMIN/VENTAS", sumPatronSena, "Exonerado si < 10 SMLMV Art. 114-1 E.T.");

        // Créditos (Pasivos Clases 23, 25 y 26)
        addCreditIfPositive(journal, "237005", "Aportes a Entidades Promotoras de Salud (EPS) por Pagar", "PILA", sumEmpHealth.add(sumPatronHealth), "4% trabajador + 8.5% empleador (si aplica)");
        addCreditIfPositive(journal, "237006", "Aportes a Administradoras de Riesgos Laborales (ARL) por Pagar", "PILA", sumPatronArl, "Total ARL planilla PILA");
        addCreditIfPositive(journal, "237010", "Aportes al ICBF, SENA y Cajas de Compensación por Pagar", "PILA", sumPatronCaja.add(sumPatronIcbf).add(sumPatronSena), "Total Parafiscales planilla PILA");
        addCreditIfPositive(journal, "238030", "Fondos de Cesantías y/o Pensiones por Pagar", "PILA", sumEmpPension.add(sumPatronPension).add(sumEmpFsp), "4% trabajador + 12% empleador + FSP");
        addCreditIfPositive(journal, "250505", "Salarios por Pagar (Neto Nómina a Dispersar en Banco)", "TESORERIA", sumNetPay, "Neto a girar a cuentas de nómina");
        addCreditIfPositive(journal, "261005", "Pasivo Estimado Provisión Cesantías Consolidadas", "PROVISIONES", sumProvCesantias, "Provisión acumulada cesantías");
        addCreditIfPositive(journal, "261010", "Pasivo Estimado Intereses sobre Cesantías", "PROVISIONES", sumProvIntereses, "Provisión acumulada intereses 12%");
        addCreditIfPositive(journal, "261015", "Pasivo Estimado Vacaciones Consolidadas", "PROVISIONES", sumProvVacaciones, "Provisión acumulada vacaciones");
        addCreditIfPositive(journal, "261020", "Pasivo Estimado Prima de Servicios", "PROVISIONES", sumProvPrima, "Provisión acumulada prima semestral");

        BigDecimal totalDebit = journal.stream().map(PucJournalLine::debitCOP).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = journal.stream().map(PucJournalLine::creditCOP).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PayrollBatchAuditResponse(
                year,
                smlmv,
                params.transportAllowanceCOP(),
                results.size(),
                ugppRiskCount,
                sumBaseSalary,
                sumNonSalary,
                sumUgppExcess,
                sumOmittedMonth,
                sumOmittedMonth.multiply(new BigDecimal("12")),
                sumNetPay,
                sumEmployerCost,
                totalDebit,
                totalCredit,
                totalDebit.compareTo(totalCredit) == 0,
                results,
                journal
        );
    }

    private void addDebitIfPositive(List<PucJournalLine> lines, String code, String name, String area, BigDecimal amount, String note) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new PucJournalLine(code, name, area, "DEBITO", amount, BigDecimal.ZERO, note));
        }
    }

    private void addCreditIfPositive(List<PucJournalLine> lines, String code, String name, String area, BigDecimal amount, String note) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new PucJournalLine(code, name, area, "CREDITO", BigDecimal.ZERO, amount, note));
        }
    }
}
