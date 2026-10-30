package com.alcontable.payroll.domain.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

/**
 * Parámetros históricos oficiales de Salario Mínimo Legal Mensual Vigente (SMLMV),
 * Auxilio de Transporte y UVT en Colombia (2020-2026), así como tasas de ARL por nivel de riesgo.
 */
public final class LaborLegalConstants {

    public record AnnualLegalParameters(
            int year,
            BigDecimal smlmv,
            BigDecimal transportAllowance,
            BigDecimal uvt,
            int maxWeeklyHoursJuly,
            int monthlyHoursDivisorJuly
    ) {
        public BigDecimal smlmvCOP() {
            return smlmv;
        }

        public BigDecimal transportAllowanceCOP() {
            return transportAllowance;
        }

        public BigDecimal uvtCOP() {
            return uvt;
        }
    }

    private static final Map<Integer, AnnualLegalParameters> HISTORICAL_PARAMS = new TreeMap<>();

    static {
        HISTORICAL_PARAMS.put(2020, new AnnualLegalParameters(
                2020, new BigDecimal("877803"), new BigDecimal("102854"), new BigDecimal("35607"), 48, 240));
        HISTORICAL_PARAMS.put(2021, new AnnualLegalParameters(
                2021, new BigDecimal("908526"), new BigDecimal("106454"), new BigDecimal("36308"), 48, 240));
        HISTORICAL_PARAMS.put(2022, new AnnualLegalParameters(
                2022, new BigDecimal("1000000"), new BigDecimal("117172"), new BigDecimal("38004"), 48, 240));
        HISTORICAL_PARAMS.put(2023, new AnnualLegalParameters(
                2023, new BigDecimal("1160000"), new BigDecimal("140606"), new BigDecimal("42412"), 47, 235));
        HISTORICAL_PARAMS.put(2024, new AnnualLegalParameters(
                2024, new BigDecimal("1300000"), new BigDecimal("162000"), new BigDecimal("47065"), 46, 230));
        HISTORICAL_PARAMS.put(2025, new AnnualLegalParameters(
                2025, new BigDecimal("1423500"), new BigDecimal("200000"), new BigDecimal("49799"), 44, 220));
        HISTORICAL_PARAMS.put(2026, new AnnualLegalParameters(
                2026, new BigDecimal("1550000"), new BigDecimal("218000"), new BigDecimal("52300"), 42, 210));
    }

    private LaborLegalConstants() {}

    public static AnnualLegalParameters forYear(int year) {
        if (HISTORICAL_PARAMS.containsKey(year)) {
            return HISTORICAL_PARAMS.get(year);
        }
        return year < 2020 ? HISTORICAL_PARAMS.get(2020) : HISTORICAL_PARAMS.get(2026);
    }

    public static Map<Integer, AnnualLegalParameters> allYears() {
        return Map.copyOf(HISTORICAL_PARAMS);
    }

    /**
     * Tasas oficiales de cotización a ARL (Decreto 1772 de 1994 / Decreto 768 de 2022).
     */
    public static BigDecimal arlRateForRiskClass(String riskClass) {
        if (riskClass == null) return new BigDecimal("0.00522");
        return switch (riskClass.trim().toUpperCase()) {
            case "I", "1" -> new BigDecimal("0.00522");     // 0.522%
            case "II", "2" -> new BigDecimal("0.01044");    // 1.044%
            case "III", "3" -> new BigDecimal("0.02436");   // 2.436%
            case "IV", "4" -> new BigDecimal("0.04350");    // 4.350%
            case "V", "5" -> new BigDecimal("0.06960");     // 6.960%
            default -> new BigDecimal("0.00522");
        };
    }
}
