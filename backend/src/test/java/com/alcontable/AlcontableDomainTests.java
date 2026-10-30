package com.alcontable;

import com.alcontable.exogena.application.usecases.ExogenaBatchAuditUseCase;
import com.alcontable.payroll.application.usecases.PayrollBatchAuditAndPucUseCase;
import com.alcontable.portfolio.application.usecases.ClientPortfolioUseCase;
import com.alcontable.sanctions.application.usecases.DianSanctionAndInterestUseCase;
import com.alcontable.taxplanning.application.usecases.TaxPlanningAndLaw2277UseCase;
import com.alcontable.portfolio.domain.ports.AccountingPortfolioRepositoryPort;
import com.alcontable.portfolio.infrastructure.persistence.AccountingClientEntity;
import com.alcontable.portfolio.infrastructure.persistence.SavedWorkpaperEntity;
import com.alcontable.portfolio.infrastructure.persistence.TaxObligationEntity;
import com.alcontable.sanctions.application.usecases.CalculateBusinessDaysUseCase;
import com.alcontable.sanctions.domain.service.EmilianiHolidayEngine;
import com.alcontable.exogena.application.usecases.QueryDivipolaUseCase;
import com.alcontable.exogena.domain.model.MunicipalityEntity;
import com.alcontable.exogena.domain.ports.MunicipalityRepositoryPort;
import com.alcontable.payroll.application.usecases.CalculateContractSettlementUseCase;
import com.alcontable.payroll.application.usecases.CalculateOvertimeUseCase;
import com.alcontable.payroll.domain.specification.LaborSpecifications;
import com.alcontable.payroll.domain.strategy.Law2101WorkingHoursStrategy;
import com.alcontable.payroll.domain.strategy.SeveranceIndemnificationStrategy;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.taxplanning.application.usecases.TaxDianUseCase;
import com.alcontable.taxplanning.domain.vo.NitVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AlcontableDomainTests {

    private final Law2101WorkingHoursStrategy law2101Strategy = new Law2101WorkingHoursStrategy();
    private final CalculateOvertimeUseCase overtimeUseCase = new CalculateOvertimeUseCase(law2101Strategy);
    private final LaborSpecifications laborSpecs = new LaborSpecifications();
    private final SeveranceIndemnificationStrategy indemStrategy = new SeveranceIndemnificationStrategy();
    private final CalculateContractSettlementUseCase settlementUseCase =
            new CalculateContractSettlementUseCase(laborSpecs, indemStrategy);
    private final TaxDianUseCase taxDianUseCase = new TaxDianUseCase();
    private final EmilianiHolidayEngine holidayEngine = new EmilianiHolidayEngine();
    private final CalculateBusinessDaysUseCase businessDaysUseCase = new CalculateBusinessDaysUseCase(holidayEngine);

    @Test
    @DisplayName("Ley 2101 de 2021: Aplica correctamente la reducción gradual de horas semanales (48 -> 47 -> 46 -> 44 -> 42)")
    void testLaw2101GradualReductionStages() {
        assertEquals(240, law2101Strategy.resolveForDate(LocalDate.of(2023, 1, 10)).monthlyHoursDivisor().intValue());
        assertEquals(235, law2101Strategy.resolveForDate(LocalDate.of(2023, 8, 1)).monthlyHoursDivisor().intValue());
        assertEquals(230, law2101Strategy.resolveForDate(LocalDate.of(2024, 9, 1)).monthlyHoursDivisor().intValue());
        assertEquals(220, law2101Strategy.resolveForDate(LocalDate.of(2025, 10, 1)).monthlyHoursDivisor().intValue());
        assertEquals(210, law2101Strategy.resolveForDate(LocalDate.of(2026, 8, 1)).monthlyHoursDivisor().intValue());
    }

    @Test
    @DisplayName("Horas Extra y Recargos CST Art. 168/179: Liquida exactamente HED (1.25x), HEN (1.75x) y HENDF (2.50x)")
    void testOvertimeCalculationExactness() {
        var req = new CalculateOvertimeUseCase.OvertimeRequest(
                new BigDecimal("2100000"),
                LocalDate.of(2026, 8, 15),
                null,
                new BigDecimal("4"),
                new BigDecimal("2"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("2")
        );

        var res = overtimeUseCase.execute(req);
        assertEquals(new BigDecimal("10000.00"), res.ordinaryHourValueCOP());
        assertEquals(new BigDecimal("135000"), res.totalOvertimeAndSurchargesCOP());
        assertEquals(new BigDecimal("2235000"), res.totalSalaryPlusOvertimeCOP());
    }

    @Test
    @DisplayName("Liquidación CST: Calcula Cesantías, Intereses (12%), Prima, Vacaciones e Indemnización Art. 64 CST")
    void testFullContractSettlementAndArt64Indemnification() {
        var req = new CalculateContractSettlementUseCase.SettlementRequest(
                2025,
                new BigDecimal("1423500"),
                BigDecimal.ZERO,
                720,
                360,
                180,
                360,
                "INDEFINIDO",
                true,
                0,
                "I",
                true
        );

        var res = settlementUseCase.execute(req);
        assertTrue(res.eligibleForTransportAllowance());
        assertEquals(new BigDecimal("1623500"), res.baseBenefitsLiquidationSalaryCOP());
        assertEquals(new BigDecimal("1623500"), res.severanceCesantiasCOP());
        assertEquals(new BigDecimal("194820"), res.severanceInterests12PercentCOP());
        assertEquals(new BigDecimal("811750"), res.serviceBonusPrimaCOP());
        assertEquals(new BigDecimal("711750"), res.vacationsCompensatedCOP());
        assertEquals(new BigDecimal("50.00"), res.indemnification().compensatedDays());
    }

    @Test
    @DisplayName("DIAN Módulo 11: Calcula el Dígito de Verificación (DV) oficial de NITs reales colombianos")
    void testDianModulo11CheckDigit() {
        var dianReport = NitVO.inspectAndCalculate("800.197.268-4");
        assertEquals(4, dianReport.calculatedCheckDigit());
        assertTrue(dianReport.validProvidedCheckDigit());
        assertEquals("800197268-4", dianReport.formattedNitWithDv());

        var bancoReport = taxDianUseCase.verifyNit("890903938");
        assertEquals(8, bancoReport.calculatedCheckDigit());
        assertEquals("890903938-8", bancoReport.formattedNitWithDv());
    }

    @Test
    @DisplayName("Retención en la Fuente y UVT: Aplica retención solo cuando el subtotal supera la base mínima en UVT")
    void testWithholdingTaxThresholdUvt() {
        var belowBase = taxDianUseCase.calculateWithholding(new TaxDianUseCase.WithholdingCalculationRequest(
                2025, "COMPRAS_GENERALES", new BigDecimal("900000"), true, new BigDecimal("19")
        ));
        assertFalse(belowBase.appliesWithholding());
        assertEquals(BigDecimal.ZERO, belowBase.withholdingAmountCOP());

        var aboveBase = taxDianUseCase.calculateWithholding(new TaxDianUseCase.WithholdingCalculationRequest(
                2025, "COMPRAS_GENERALES", new BigDecimal("2000000"), true, new BigDecimal("19")
        ));
        assertTrue(aboveBase.appliesWithholding());
        assertEquals(new BigDecimal("50000"), aboveBase.withholdingAmountCOP());
    }

    @Test
    @DisplayName("Ley Emiliani (Ley 51 de 1983): Genera los 18 festivos oficiales y traslada los móviles al lunes")
    void testEmilianiHolidaysAndBusinessDays() {
        var holidays2026 = holidayEngine.getHolidaysForYear(2026);
        assertEquals(18, holidays2026.size());

        holidays2026.stream()
                .filter(h -> h.category().contains("EMILIANI"))
                .forEach(h -> assertEquals(DayOfWeek.MONDAY, h.observedDate().getDayOfWeek()));

        var added = businessDaysUseCase.addBusinessDays(LocalDate.of(2025, 12, 31), 5, false);
        assertTrue(added.resultingDueDate().isAfter(LocalDate.of(2026, 1, 7)));
        assertFalse(added.holidaysEncountered().isEmpty());
    }

    @Test
    @DisplayName("Arquitectura Hexagonal DIVIPOLA: QueryDivipolaUseCase opera desacoplado de JPA mediante MunicipalityRepositoryPort")
    void testDivipolaHexagonalPortIsolation() {
        var sampleList = List.of(
                new MunicipalityEntity("68895", "68", "SANTANDER", "ZAPATOCA", "Sexta", "Andina", 6.8152, -73.2684, false),
                new MunicipalityEntity("68001", "68", "SANTANDER", "BUCARAMANGA", "Especial", "Andina", 7.1193, -73.1227, true)
        );

        MunicipalityRepositoryPort fakePort = new MunicipalityRepositoryPort() {
            @Override
            public List<MunicipalityEntity> findAll() { return sampleList; }
            @Override
            public List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode) {
                return sampleList.stream().filter(m -> m.getDepartmentCode().equals(departmentCode)).toList();
            }
            @Override
            public Optional<MunicipalityEntity> findByDaneCode(String daneCode) {
                return sampleList.stream().filter(m -> m.getDaneCode().equals(daneCode)).findFirst();
            }
            @Override
            public List<MunicipalityEntity> searchByDepartmentAndTerm(String departmentCode, String term) {
                return sampleList.stream()
                        .filter(m -> (departmentCode == null || departmentCode.isBlank() || m.getDepartmentCode().equals(departmentCode))
                                && (term == null || term.isBlank() || m.getMunicipalityName().contains(term.toUpperCase())))
                        .toList();
            }
        };

        var useCase = new QueryDivipolaUseCase(fakePort);
        assertEquals(1, useCase.listDepartments().size());
        assertEquals("ZAPATOCA", useCase.findByDaneCode("68895").getMunicipalityName());
        assertEquals(1, useCase.searchMunicipalities("68", "ZAPA").size());
    }

    @Test
    @DisplayName("ALcontable Motor 2 (Nómina en Lote + UGPP Ley 1393 Tope 40% + Partida Doble PUC): Detecta exceso no salarial y cuadra Débitos = Créditos")
    void testPayrollBatchUgpp40PercentAndPucDoubleEntryBalance() {
        var payrollUseCase = new PayrollBatchAuditAndPucUseCase(laborSpecs);
        var req = new PayrollBatchAuditAndPucUseCase.PayrollBatchRequest(
                2026,
                true,
                List.of(
                        new PayrollBatchAuditAndPucUseCase.PayrollEmployeeInput(
                                "79456123", "Carlos Ruiz (Exceso UGPP)", "ADMINISTRACION",
                                new BigDecimal("2000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                                new BigDecimal("3000000"),
                                "I", false
                        ),
                        new PayrollBatchAuditAndPucUseCase.PayrollEmployeeInput(
                                "52987321", "Laura Gómez (Sin Exceso UGPP)", "VENTAS",
                                new BigDecimal("3500000"), new BigDecimal("250000"), new BigDecimal("400000"),
                                new BigDecimal("500000"),
                                "II", false
                        )
                )
        );

        var res = payrollUseCase.execute(req);
        assertEquals(2, res.employeesProcessed());
        assertEquals(1, res.employeesWithUgppRiskCount());
        assertEquals(new BigDecimal("1000000"), res.totalUgppExcessGravadoCOP());
        assertTrue(res.pucDoubleEntryBalanced(), "El comprobante contable PUC debe cuadrar Débitos == Créditos al peso");
        assertEquals(res.totalPucDebitCOP(), res.totalPucCreditCOP());
    }

    @Test
    @DisplayName("ALcontable Motor 2 (Casos Borde Chaos): Rechaza planillas vacías, devengos negativos, salario integral menor a 13 SMLMV y prorratea días laborados")
    void testPayrollBatchAdversarialValidationsAndWorkedDaysProration() {
        var payrollUseCase = new PayrollBatchAuditAndPucUseCase(laborSpecs);

        // 1. Lista vacía -> DomainValidationException
        assertThrows(DomainValidationException.class, () ->
                payrollUseCase.execute(new PayrollBatchAuditAndPucUseCase.PayrollBatchRequest(2026, true, List.of()))
        );

        // 2. Comisiones o devengos negativos -> DomainValidationException
        assertThrows(DomainValidationException.class, () ->
                payrollUseCase.execute(new PayrollBatchAuditAndPucUseCase.PayrollBatchRequest(
                        2026, true,
                        List.of(new PayrollBatchAuditAndPucUseCase.PayrollEmployeeInput(
                                "1001", "Empleado Negativo", "ADMINISTRACION",
                                new BigDecimal("2000000"), new BigDecimal("-50000"), BigDecimal.ZERO, BigDecimal.ZERO, "I", false
                        ))
                ))
        );

        // 3. Salario integral menor a 13 SMLMV -> DomainValidationException
        assertThrows(DomainValidationException.class, () ->
                payrollUseCase.execute(new PayrollBatchAuditAndPucUseCase.PayrollBatchRequest(
                        2026, true,
                        List.of(new PayrollBatchAuditAndPucUseCase.PayrollEmployeeInput(
                                "1002", "Integral Inválido", "ADMINISTRACION",
                                new BigDecimal("5000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "I", true
                        ))
                ))
        );

        // 4. Prorrateo por 15 días laborados manteniendo partida doble cuadrada
        var halfMonthRes = payrollUseCase.execute(new PayrollBatchAuditAndPucUseCase.PayrollBatchRequest(
                2026, true,
                List.of(new PayrollBatchAuditAndPucUseCase.PayrollEmployeeInput(
                        "1003", "Empleado Medio Mes", "ADMINISTRACION",
                        new BigDecimal("2000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "I", false, 15
                ))
        ));
        assertEquals(new BigDecimal("1000000"), halfMonthRes.employeeResults().get(0).baseSalaryCOP());
        assertTrue(halfMonthRes.pucDoubleEntryBalanced());
    }

    @Test
    @DisplayName("ALcontable Motor 3 (Pre-Auditor Exógena en Lote): Corrige DV Módulo 11 erróneo y detecta sub-retención Art. 370 E.T.")
    void testExogenaBatchAuditDetectsWrongDvAndWithholdingShortfall() {
        MunicipalityRepositoryPort fakePort = createSampleMunicipalityPort();
        var exogenaUseCase = new ExogenaBatchAuditUseCase(fakePort);
        String rawPaste = "800.197.268-9\tPROVEEDOR CON DV MALO SAS\tSERVICIOS\tSI\t5000000\t50000\tMedellin";
        var res = exogenaUseCase.execute(new ExogenaBatchAuditUseCase.ExogenaBatchRequest(2026, rawPaste, null));

        assertEquals(1, res.totalRowsProcessed());
        assertEquals(1, res.dvErrorsCorrectedCount());
        assertEquals(4, res.auditedRows().get(0).officialCalculatedDv());
        assertEquals("RETUVO_DE_MENOS_RIESGO_DIAN", res.auditedRows().get(0).withholdingAuditStatus());
        assertEquals(new BigDecimal("150000"), res.totalShortfallRiskArt370COP());
    }

    @Test
    @DisplayName("ALcontable Motor 3 (Casos Borde Chaos): Tolera NIT corrupto sin abortar el lote y parsea decimales de Excel sin multiplicar por 100")
    void testExogenaBatchToleratesCorruptNitAndParsesExcelDecimalsAccurately() {
        MunicipalityRepositoryPort fakePort = createSampleMunicipalityPort();
        var exogenaUseCase = new ExogenaBatchAuditUseCase(fakePort);
        String rawPaste =
                "99\tTERCERO NIT CORTO\tCOMPRAS\tSI\t1500000.00\t37500.00\tMedellin\n" +
                "890903938-8\tBANCOLOMBIA S.A.\tHONORARIOS\tSI\t2.000.000,00\t220.000,00\tMedellin";

        var res = exogenaUseCase.execute(new ExogenaBatchAuditUseCase.ExogenaBatchRequest(2026, rawPaste, null));
        assertEquals(2, res.totalRowsProcessed());
        assertEquals("DOCUMENTO_INVALIDO_O_CORRUPTO", res.auditedRows().get(0).dvValidationStatus());
        // Verifica que "1500000.00" se parseó como $1.500.000 y NO como $150.000.000
        assertEquals(new BigDecimal("1500000"), res.auditedRows().get(0).basePaidCOP());
        assertEquals(new BigDecimal("2000000"), res.auditedRows().get(1).basePaidCOP());
        assertEquals("DV_OK", res.auditedRows().get(1).dvValidationStatus());
    }

    @Test
    @DisplayName("ALcontable Motor 4 y 5 (Planeación Ley 2277/RST y Sanciones DIAN Art. 640 con Piso 10 UVT Art. 639)")
    void testTaxPlanningLaw2277AndDianSanctionMinimumFloor() {
        var planningUseCase = new TaxPlanningAndLaw2277UseCase(laborSpecs);
        var planRes = planningUseCase.execute(new TaxPlanningAndLaw2277UseCase.Law2277AndRstRequest(
                2026,
                new BigDecimal("18000000"),
                false,
                new BigDecimal("2500000"),
                new BigDecimal("900000"),
                2,
                new BigDecimal("2000000"),
                new BigDecimal("35000000"),
                new BigDecimal("1200000000"),
                new BigDecimal("420000000"),
                new BigDecimal("36000000"),
                new BigDecimal("950000000"),
                "GRUPO_2_COMERCIO_INDUSTRIA",
                new BigDecimal("9.66")
        ));
        assertTrue(planRes.law2277Depuration().effectiveBenefitsAllowedCOP().compareTo(planRes.law2277Depuration().globalCap1340UvtMonthlyCOP()) <= 0);
        assertEquals("SIMPLE_RST", planRes.regimeComparison().recommendedRegime());

        var sanctionUseCase = new DianSanctionAndInterestUseCase();
        var sanctionRes = sanctionUseCase.execute(new DianSanctionAndInterestUseCase.SanctionLiquidationRequest(
                2026,
                "RETEFUENTE_350",
                "EXTEMPORANEIDAD_641",
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 25),
                new BigDecimal("4000000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                2,
                new BigDecimal("28.5")
        ));
        assertTrue(sanctionRes.minimumSanctionFloorEnforced());
        assertEquals(sanctionRes.minimumSanction10UvtRoundedCOP(), sanctionRes.finalSanctionRoundedArt868COP());
    }

    @Test
    @DisplayName("ALcontable Motor 4 (Casos Borde Chaos): Ingresos en $0 devuelven $0 de impuesto e ingresos > 100.000 UVT inhabilitan el RST (Art. 905 E.T.)")
    void testTaxPlanningZeroIncomeAndRst100kUvtCapDisqualification() {
        var planningUseCase = new TaxPlanningAndLaw2277UseCase(laborSpecs);

        // Ingresos en $0 COP -> Base gravable y retención deben ser $0 COP (sin inventar montos)
        var zeroRes = planningUseCase.execute(new TaxPlanningAndLaw2277UseCase.Law2277AndRstRequest(
                2026, BigDecimal.ZERO, true, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "GRUPO_2_COMERCIO_INDUSTRIA", new BigDecimal("9.66")
        ));
        assertEquals(BigDecimal.ZERO, zeroRes.law2277Depuration().monthlyGrossIncomeCOP());
        assertEquals(BigDecimal.ZERO, zeroRes.law2277Depuration().withholdingArt383COP());
        assertEquals(BigDecimal.ZERO, zeroRes.regimeComparison().ordinaryTotalTaxBurdenCOP());

        // Ingresos brutos de $8.000 millones (> 100.000 UVT) -> Inhabilitado para SIMPLE_RST, recomienda ORDINARIO
        var overCapRes = planningUseCase.execute(new TaxPlanningAndLaw2277UseCase.Law2277AndRstRequest(
                2026, new BigDecimal("15000000"), true, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("8000000000"), new BigDecimal("1000000000"), BigDecimal.ZERO, BigDecimal.ZERO,
                "GRUPO_2_COMERCIO_INDUSTRIA", new BigDecimal("9.66")
        ));
        assertEquals(BigDecimal.ZERO, overCapRes.law2277Depuration().exempt25AcceptedCOP(), "Independiente con costos no tiene renta exenta del 25%");
        assertEquals("ORDINARIO", overCapRes.regimeComparison().recommendedRegime());
        assertTrue(overCapRes.regimeComparison().executiveVerdict().contains("INHABILITADO PARA RST"));
    }

    @Test
    @DisplayName("ALcontable Motor 5 (Casos Borde Chaos): Declaración oportuna (filing <= due) y corrección con delta $0 liquidan $0 de sanción")
    void testDianSanctionTimelyEarlyFilingAndZeroDeltaCorrection() {
        var sanctionUseCase = new DianSanctionAndInterestUseCase();

        // Presentación el mismo día del vencimiento (oportuna) -> $0 sanción y $0 intereses
        var timelyRes = sanctionUseCase.execute(new DianSanctionAndInterestUseCase.SanctionLiquidationRequest(
                2026, "IVA_300", "EXTEMPORANEIDAD_641",
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15),
                new BigDecimal("10000000"), BigDecimal.ZERO, BigDecimal.ZERO, true, 2, new BigDecimal("28.5")
        ));
        assertEquals(0L, timelyRes.calendarDaysOverdue());
        assertEquals(BigDecimal.ZERO, timelyRes.finalSanctionRoundedArt868COP());
        assertEquals(BigDecimal.ZERO, timelyRes.moratoryInterestsRoundedArt868COP());

        // Presentación anticipada -> $0 sanción sin lanzar excepción
        var earlyRes = sanctionUseCase.execute(new DianSanctionAndInterestUseCase.SanctionLiquidationRequest(
                2026, "IVA_300", "EXTEMPORANEIDAD_641",
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 10),
                new BigDecimal("10000000"), BigDecimal.ZERO, BigDecimal.ZERO, true, 2, new BigDecimal("28.5")
        ));
        assertEquals(BigDecimal.ZERO, earlyRes.finalSanctionRoundedArt868COP());

        // Corrección voluntaria con delta $0 COP -> $0 sanción
        var zeroDeltaCorr = sanctionUseCase.execute(new DianSanctionAndInterestUseCase.SanctionLiquidationRequest(
                2026, "RENTA_110", "CORRECCION_VOLUNTARIA_644",
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 8, 10),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, 2, new BigDecimal("28.5")
        ));
        assertEquals(BigDecimal.ZERO, zeroDeltaCorr.finalSanctionRoundedArt868COP());
    }

    @Test
    @DisplayName("ALcontable Motor 1 (Arquitectura Hexagonal Cartera & Radar): Crea cliente, valida DV Módulo 11 y genera vencimientos en días hábiles")
    void testClientPortfolioHexagonalUseCaseWithFakePort() {
        List<AccountingClientEntity> clients = new ArrayList<>();
        List<TaxObligationEntity> obligations = new ArrayList<>();
        List<SavedWorkpaperEntity> workpapers = new ArrayList<>();

        AccountingPortfolioRepositoryPort fakePortfolioPort = new AccountingPortfolioRepositoryPort() {
            @Override
            public List<AccountingClientEntity> findAllClientsOrderByBusinessNameAsc() { return clients; }
            @Override
            public Optional<AccountingClientEntity> findClientById(Long id) { return clients.stream().findFirst(); }
            @Override
            public Optional<AccountingClientEntity> findClientByCleanNit(String cleanNit) {
                return clients.stream().filter(c -> c.getNit().equals(cleanNit)).findFirst();
            }
            @Override
            public boolean existsClientById(Long id) { return !clients.isEmpty(); }
            @Override
            public AccountingClientEntity saveClient(AccountingClientEntity client) {
                clients.add(client);
                return client;
            }
            @Override
            public List<TaxObligationEntity> findAllObligationsOrderByDueDateAsc() { return obligations; }
            @Override
            public Optional<TaxObligationEntity> findObligationById(Long id) { return obligations.stream().findFirst(); }
            @Override
            public TaxObligationEntity saveObligation(TaxObligationEntity obligation) {
                obligations.add(obligation);
                return obligation;
            }
            @Override
            public List<TaxObligationEntity> saveAllObligations(List<TaxObligationEntity> obs) {
                obligations.addAll(obs);
                return obs;
            }
            @Override
            public List<SavedWorkpaperEntity> findAllWorkpapersOrderByCreatedAtDesc() { return workpapers; }
            @Override
            public List<SavedWorkpaperEntity> findWorkpapersByClientIdOrderByCreatedAtDesc(Long clientId) { return workpapers; }
            @Override
            public SavedWorkpaperEntity saveWorkpaper(SavedWorkpaperEntity wp) {
                workpapers.add(wp);
                return wp;
            }
        };

        var portfolioUseCase = new ClientPortfolioUseCase(fakePortfolioPort, holidayEngine);
        var created = portfolioUseCase.createClient(new ClientPortfolioUseCase.CreateClientRequest(
                "800.197.268", "Empresa de Prueba Hexagonal S.A.S.", "JURIDICA", "ORDINARIO",
                "BIMESTRAL", "05,07,14,48", "11001", "BOGOTÁ, D.C.",
                new BigDecimal("1200000000"), 10, true
        ));

        assertEquals("800197268-4", created.formattedNit());
        assertEquals(2, obligations.size(), "Debe generar automáticamente Retefuente 350 e IVA 300");
        // Ninguna obligación generada debe caer en sábado, domingo o festivo Emiliani
        for (TaxObligationEntity ob : obligations) {
            assertNotEquals(DayOfWeek.SATURDAY, ob.getDueDate().getDayOfWeek());
            assertNotEquals(DayOfWeek.SUNDAY, ob.getDueDate().getDayOfWeek());
            assertFalse(holidayEngine.isHoliday(ob.getDueDate()));
        }
    }

    private MunicipalityRepositoryPort createSampleMunicipalityPort() {
        return new MunicipalityRepositoryPort() {
            @Override
            public List<MunicipalityEntity> findAll() {
                return List.of(
                        new MunicipalityEntity("05001", "05", "ANTIOQUIA", "MEDELLÍN", "Especial", "Andina", 6.2442, -75.5812, true)
                );
            }
            @Override
            public List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String d) { return findAll(); }
            @Override
            public Optional<MunicipalityEntity> findByDaneCode(String c) { return findAll().stream().findFirst(); }
            @Override
            public List<MunicipalityEntity> searchByDepartmentAndTerm(String d, String t) { return findAll(); }
        };
    }
}
