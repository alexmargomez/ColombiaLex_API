package com.alexmar.colombialex.labor.domain.specification;

import com.alexmar.colombialex.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Specification Pattern — Reglas de elegibilidad normativa laboral y de seguridad social en Colombia.
 */
@Component
public class LaborSpecifications {

    /**
     * Ley 15 de 1959: Tienen derecho al Auxilio de Transporte los trabajadores que devenguen hasta dos (2) SMLMV.
     */
    public boolean isEligibleForTransportAllowance(MoneyCOP monthlySalary, MoneyCOP smlmv) {
        MoneyCOP twoSmlmv = smlmv.multiply(2);
        return monthlySalary.isLessThanOrEqualTo(twoSmlmv);
    }

    /**
     * Estatuto Tributario Art. 114-1: Exoneración de aportes patronales a Salud (8.5%), SENA (2%) e ICBF (3%)
     * para trabajadores que devenguen menos de diez (10) SMLMV.
     */
    public boolean isExoneratedUnderArt114ET(MoneyCOP monthlyIbc, MoneyCOP smlmv, boolean applyArt114Exoneration) {
        if (!applyArt114Exoneration) {
            return false;
        }
        MoneyCOP tenSmlmv = smlmv.multiply(10);
        return !monthlyIbc.isGreaterThanOrEqualTo(tenSmlmv);
    }

    /**
     * Ley 100 de 1993 Art. 27 / Ley 797 de 2003: Tarifa progresiva del Fondo de Solidaridad Pensional (FSP)
     * según el Ingreso Base de Cotización (IBC) expresado en múltiplos del SMLMV.
     */
    public BigDecimal resolveSolidarityFundRate(MoneyCOP monthlyIbc, MoneyCOP smlmv) {
        BigDecimal ratio = monthlyIbc.value().divide(smlmv.value(), 4, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("4")) < 0) {
            return BigDecimal.ZERO;
        }
        if (ratio.compareTo(new BigDecimal("16")) < 0) {
            return new BigDecimal("0.010"); // 1.0%
        }
        if (ratio.compareTo(new BigDecimal("17")) < 0) {
            return new BigDecimal("0.012"); // 1.2%
        }
        if (ratio.compareTo(new BigDecimal("18")) < 0) {
            return new BigDecimal("0.014"); // 1.4%
        }
        if (ratio.compareTo(new BigDecimal("19")) < 0) {
            return new BigDecimal("0.016"); // 1.6%
        }
        if (ratio.compareTo(new BigDecimal("20")) < 0) {
            return new BigDecimal("0.018"); // 1.8%
        }
        return new BigDecimal("0.020"); // 2.0%
    }
}
