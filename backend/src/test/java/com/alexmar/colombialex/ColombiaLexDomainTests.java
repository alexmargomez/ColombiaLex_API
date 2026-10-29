package com.alexmar.colombialex;

import com.alexmar.colombialex.calendar.application.usecases.CalculateBusinessDaysUseCase;
import com.alexmar.colombialex.calendar.domain.service.EmilianiHolidayEngine;
import com.alexmar.colombialex.labor.application.usecases.CalculateContractSettlementUseCase;
import com.alexmar.colombialex.labor.application.usecases.CalculateOvertimeUseCase;
import com.alexmar.colombialex.labor.domain.specification.LaborSpecifications;
import com.alexmar.colombialex.labor.domain.strategy.Law2101WorkingHoursStrategy;
import com.alexmar.colombialex.labor.domain.strategy.SeveranceIndemnificationStrategy;
import com.alexmar.colombialex.tax.application.usecases.TaxDianUseCase;
import com.alexmar.colombialex.tax.domain.vo.NitVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ColombiaLexDomainTests {

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
                LocalDate.of(2026, 8, 15), // Divisor 210 -> Hora ordinaria = $10,000 COP
                null,
                new BigDecimal("4"),   // 4 HED * $12,500 = $50,000
                new BigDecimal("2"),   // 2 HEN * $17,500 = $35,000
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("0"),
                new BigDecimal("2")    // 2 HENDF * $25,000 = $50,000
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
                new BigDecimal("1423500"), // 1 SMLMV 2025 (+ $200,000 aux transporte = $1,623,500 base prestaciones)
                BigDecimal.ZERO,
                720, // 2 años laborados -> Indemnización Indefinido <10 SMLMV = 30 días + 20 días = 50 días
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
        assertEquals(new BigDecimal("711750"), res.vacationsCompensatedCOP()); // Sin aux. transporte (1,423,500 / 2)
        assertEquals(new BigDecimal("50.00"), res.indemnification().compensatedDays());
    }

    @Test
    @DisplayName("DIAN Módulo 11: Calcula el Dígito de Verificación (DV) oficial de NITs reales colombianos")
    void testDianModulo11CheckDigit() {
        // NIT de la DIAN: 800197268-4
        var dianReport = NitVO.inspectAndCalculate("800.197.268-4");
        assertEquals(4, dianReport.calculatedCheckDigit());
        assertTrue(dianReport.validProvidedCheckDigit());
        assertEquals("800197268-4", dianReport.formattedNitWithDv());

        // NIT Bancolombia: 890903938-8
        var bancoReport = taxDianUseCase.verifyNit("890903938");
        assertEquals(8, bancoReport.calculatedCheckDigit());
        assertEquals("890903938-8", bancoReport.formattedNitWithDv());
    }

    @Test
    @DisplayName("Retención en la Fuente y UVT: Aplica retención solo cuando el subtotal supera la base mínima en UVT")
    void testWithholdingTaxThresholdUvt() {
        // Compras generales en 2025 (UVT = $49,799 -> Base mínima 27 UVT = $1,344,573)
        var belowBase = taxDianUseCase.calculateWithholding(new TaxDianUseCase.WithholdingCalculationRequest(
                2025, "COMPRAS_GENERALES", new BigDecimal("900000"), true, new BigDecimal("19")
        ));
        assertFalse(belowBase.appliesWithholding());
        assertEquals(BigDecimal.ZERO, belowBase.withholdingAmountCOP());

        var aboveBase = taxDianUseCase.calculateWithholding(new TaxDianUseCase.WithholdingCalculationRequest(
                2025, "COMPRAS_GENERALES", new BigDecimal("2000000"), true, new BigDecimal("19")
        ));
        assertTrue(aboveBase.appliesWithholding());
        assertEquals(new BigDecimal("50000"), aboveBase.withholdingAmountCOP()); // 2.5% de 2,000,000
    }

    @Test
    @DisplayName("Ley Emiliani (Ley 51 de 1983): Genera los 18 festivos oficiales y traslada los móviles al lunes")
    void testEmilianiHolidaysAndBusinessDays() {
        var holidays2026 = holidayEngine.getHolidaysForYear(2026);
        assertEquals(18, holidays2026.size());

        // Todos los festivos de categoría LEY_EMILIANI_LUNES y PASCUA_EMILIANI deben caer en Lunes
        holidays2026.stream()
                .filter(h -> h.category().contains("EMILIANI"))
                .forEach(h -> assertEquals(DayOfWeek.MONDAY, h.observedDate().getDayOfWeek()));

        // Sumar 5 días hábiles desde el miércoles 31 de diciembre de 2025 (salta 1 Ene festivo, 3-4 Ene fin de semana)
        var added = businessDaysUseCase.addBusinessDays(LocalDate.of(2025, 12, 31), 5, false);
        assertTrue(added.resultingDueDate().isAfter(LocalDate.of(2026, 1, 7)));
        assertFalse(added.holidaysEncountered().isEmpty());
    }

    @Test
    @DisplayName("Arquitectura Hexagonal DIVIPOLA: QueryDivipolaUseCase opera desacoplado de JPA mediante MunicipalityRepositoryPort")
    void testDivipolaHexagonalPortIsolation() {
        var sampleList = java.util.List.of(
                new com.alexmar.colombialex.geo.domain.model.MunicipalityEntity(
                        "68895", "68", "SANTANDER", "ZAPATOCA", "Sexta", "Andina", 6.8152, -73.2684, false
                ),
                new com.alexmar.colombialex.geo.domain.model.MunicipalityEntity(
                        "68001", "68", "SANTANDER", "BUCARAMANGA", "Especial", "Andina", 7.1193, -73.1227, true
                )
        );

        com.alexmar.colombialex.geo.domain.ports.MunicipalityRepositoryPort fakePort =
                new com.alexmar.colombialex.geo.domain.ports.MunicipalityRepositoryPort() {
                    @Override
                    public java.util.List<com.alexmar.colombialex.geo.domain.model.MunicipalityEntity> findAll() {
                        return sampleList;
                    }

                    @Override
                    public java.util.List<com.alexmar.colombialex.geo.domain.model.MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode) {
                        return sampleList.stream().filter(m -> m.getDepartmentCode().equals(departmentCode)).toList();
                    }

                    @Override
                    public java.util.Optional<com.alexmar.colombialex.geo.domain.model.MunicipalityEntity> findByDaneCode(String daneCode) {
                        return sampleList.stream().filter(m -> m.getDaneCode().equals(daneCode)).findFirst();
                    }
                };

        var useCase = new com.alexmar.colombialex.geo.application.usecases.QueryDivipolaUseCase(fakePort);
        assertEquals(1, useCase.listDepartments().size());
        assertEquals("ZAPATOCA", useCase.findByDaneCode("68895").getMunicipalityName());
    }
}
