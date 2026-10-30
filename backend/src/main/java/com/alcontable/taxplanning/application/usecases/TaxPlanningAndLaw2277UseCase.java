package com.alcontable.taxplanning.application.usecases;

import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.payroll.domain.specification.LaborSpecifications;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.shared.domain.vo.MoneyCOP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TaxPlanningAndLaw2277UseCase {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    private final LaborSpecifications laborSpecifications;

    @Autowired
    public TaxPlanningAndLaw2277UseCase(LaborSpecifications laborSpecifications) {
        this.laborSpecifications = laborSpecifications;
    }

    public TaxPlanningAndLaw2277UseCase() {
        this(new LaborSpecifications());
    }

    public record Law2277AndRstRequest(
            Integer year,
            // Parte A: Depuración Laboral / Honorarios Art. 383 & 388 E.T. (Ley 2277)
            BigDecimal monthlyGrossIncomeCOP,
            Boolean independentContractorWithCosts,
            BigDecimal monthlyHousingInterestCOP,
            BigDecimal monthlyPrepaidMedicineCOP,
            Integer dependentsCount,
            BigDecimal monthlyVoluntaryPensionAfcCOP,
            BigDecimal annualElectronicInvoicePurchasesCOP,
            // Parte B: Comparador Estratégico Régimen Ordinario vs Régimen Simple (RST)
            BigDecimal annualGrossRevenueCOP,
            BigDecimal annualDeductibleCostsAndExpensesCOP,
            BigDecimal annualEmployerPensionContributionsCOP,
            BigDecimal annualElectronicPaymentsReceivedCOP,
            String rstActivityGroup, // GRUPO_1_TIENDAS, GRUPO_2_COMERCIO_INDUSTRIA, GRUPO_3_RESTAURANTES_TRANSPORTE, GRUPO_4_PROFESIONALES_CONSULTORIA
            BigDecimal municipalIcaRatePerThousand
    ) {}

    public record DepurationStepLine(
            String stepCode,
            String description,
            String legalReference,
            BigDecimal amountCOP,
            BigDecimal amountUVT
    ) {}

    public record Law2277DepurationResult(
            BigDecimal monthlyGrossIncomeCOP,
            BigDecimal mandatoryHealthAndPensionIncrngoCOP,
            BigDecimal monthlyNetIncomeCOP,
            BigDecimal housingInterestAcceptedCOP,
            BigDecimal prepaidMedicineAcceptedCOP,
            BigDecimal dependentsMonthlyAcceptedCOP,
            BigDecimal voluntaryAfcFvpAcceptedCOP,
            BigDecimal exempt25PreliminaryCOP,
            BigDecimal exempt25MonthlyCap790UvtCOP,
            BigDecimal exempt25AcceptedCOP,
            BigDecimal totalBenefitsRequestedCOP,
            BigDecimal globalCap40PercentCOP,
            BigDecimal globalCap1340UvtMonthlyCOP,
            BigDecimal effectiveBenefitsAllowedCOP,
            BigDecimal benefitsTruncatedByLaw2277COP,
            BigDecimal annualExtraDependents72UvtBenefitCOP,
            BigDecimal annualElectronicInvoice1PercentBenefitCOP,
            BigDecimal taxableBaseCOP,
            BigDecimal taxableBaseUVT,
            String art383BracketDescription,
            BigDecimal withholdingArt383COP,
            BigDecimal flatHonorarios11PercentWithholdingCOP,
            BigDecimal monthlySavingsArt383VsHonorariosCOP,
            List<DepurationStepLine> workpaperSteps
    ) {}

    public record RegimeComparisonResult(
            BigDecimal annualGrossRevenueCOP,
            BigDecimal annualGrossRevenueUVT,
            BigDecimal annualCostsAndExpensesCOP,
            BigDecimal costExpenseRatioPercent,
            // Ordinario
            BigDecimal ordinaryNetTaxableIncomeCOP,
            BigDecimal ordinaryIncomeTax35PercentCOP,
            BigDecimal ordinaryMunicipalIcaCOP,
            BigDecimal ordinaryTotalTaxBurdenCOP,
            BigDecimal ordinaryEffectiveTaxRatePercent,
            // SIMPLE (RST)
            String rstActivityGroup,
            BigDecimal rstConsolidatedRatePercent,
            BigDecimal rstGrossSimpleTaxCOP,
            BigDecimal rstEmployerPensionCreditCOP,
            BigDecimal rstElectronicPayments05PercentCreditCOP,
            BigDecimal rstNetTaxPayableCOP,
            BigDecimal rstEffectiveTaxRatePercent,
            // Veredicto
            String recommendedRegime,
            BigDecimal annualTaxSavingsCOP,
            BigDecimal breakEvenCostRatioPercent,
            String executiveVerdict
    ) {}

    public record TaxPlanningResponse(
            int year,
            BigDecimal uvtValueCOP,
            BigDecimal smlmvValueCOP,
            Law2277DepurationResult law2277Depuration,
            RegimeComparisonResult regimeComparison
    ) {}

    public TaxPlanningResponse execute(Law2277AndRstRequest req) {
        if (req == null) {
            throw new DomainValidationException("La solicitud de planeación tributaria no puede ser nula.");
        }
        int year = req.year() != null ? req.year() : 2026;
        var params = LaborLegalConstants.forYear(year);
        BigDecimal uvt = params.uvtCOP();
        BigDecimal smlmv = params.smlmvCOP();

        Law2277DepurationResult depuration = computeLaw2277Depuration(req, year, uvt, smlmv);
        RegimeComparisonResult comparison = computeRegimeComparison(req, uvt);

        return new TaxPlanningResponse(year, uvt, smlmv, depuration, comparison);
    }

    private Law2277DepurationResult computeLaw2277Depuration(Law2277AndRstRequest req, int year, BigDecimal uvt, BigDecimal smlmv) {
        BigDecimal grossIncome = MoneyCOP.requireNonNegative(req.monthlyGrossIncomeCOP(), "monthlyGrossIncomeCOP");

        // 1. INCRNGO (Salud 4% + Pensión 4% + FSP 1%-2%)
        BigDecimal incrngo = BigDecimal.ZERO;
        if (grossIncome.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxIbc = smlmv.multiply(new BigDecimal("25"));
            BigDecimal ibc = grossIncome.min(maxIbc).max(smlmv);
            BigDecimal health4 = MoneyCOP.percentage(ibc, new BigDecimal("4.0"));
            BigDecimal pension4 = MoneyCOP.percentage(ibc, new BigDecimal("4.0"));
            BigDecimal fspRate = laborSpecifications.resolveSolidarityFundRatePercent(ibc, year);
            BigDecimal fsp = MoneyCOP.percentage(ibc, fspRate);
            incrngo = health4.add(pension4).add(fsp);
        }

        BigDecimal netIncome = grossIncome.subtract(incrngo).max(BigDecimal.ZERO);

        // 2. Deducciones Art. 387 E.T.
        BigDecimal capHousing100Uvt = uvt.multiply(new BigDecimal("100"));
        BigDecimal housingAccepted = MoneyCOP.ofSafeNonNegative(req.monthlyHousingInterestCOP()).min(capHousing100Uvt);

        BigDecimal capPrepaid16Uvt = uvt.multiply(new BigDecimal("16"));
        BigDecimal prepaidAccepted = MoneyCOP.ofSafeNonNegative(req.monthlyPrepaidMedicineCOP()).min(capPrepaid16Uvt);

        int depCount = req.dependentsCount() != null ? Math.max(0, Math.min(4, req.dependentsCount())) : 0;
        BigDecimal capDep32Uvt = uvt.multiply(new BigDecimal("32"));
        BigDecimal depMonthlyAccepted = depCount > 0
                ? MoneyCOP.percentage(grossIncome, new BigDecimal("10.0")).min(capDep32Uvt)
                : BigDecimal.ZERO;

        BigDecimal totalDeductions = housingAccepted.add(prepaidAccepted).add(depMonthlyAccepted);

        // 3. Rentas Exentas AFC / FPV (tope 30% ingreso hasta 316.67 UVT/mes = 3800 UVT/año)
        BigDecimal capAfc30Pct = MoneyCOP.percentage(grossIncome, new BigDecimal("30.0"));
        BigDecimal capAfc316Uvt = uvt.multiply(new BigDecimal("3800")).divide(TWELVE, 0, RoundingMode.HALF_UP);
        BigDecimal afcAccepted = MoneyCOP.ofSafeNonNegative(req.monthlyVoluntaryPensionAfcCOP()).min(capAfc30Pct).min(capAfc316Uvt);

        // 4. Renta Exenta 25% (Art. 206 num. 10 E.T. reformado por Ley 2277: tope 790 UVT anuales = 65.8333 UVT/mes)
        // Si el independiente imputa costos y gastos procedentes, NO aplica la renta exenta del 25%
        boolean contractorWithCosts = Boolean.TRUE.equals(req.independentContractorWithCosts());
        BigDecimal subtotalFor25 = netIncome.subtract(totalDeductions).subtract(afcAccepted).max(BigDecimal.ZERO);
        BigDecimal exempt25Prelim = contractorWithCosts
                ? BigDecimal.ZERO
                : MoneyCOP.percentage(subtotalFor25, new BigDecimal("25.0"));
        BigDecimal cap790UvtMonthly = uvt.multiply(new BigDecimal("790")).divide(TWELVE, 0, RoundingMode.HALF_UP);
        BigDecimal exempt25Accepted = exempt25Prelim.min(cap790UvtMonthly);

        // 5. Límite Global Ley 2277 (40% del Ingreso Neto sin superar 1.340 UVT anuales = 111.6667 UVT/mes)
        BigDecimal totalBenefitsRequested = totalDeductions.add(afcAccepted).add(exempt25Accepted);
        BigDecimal cap40Percent = MoneyCOP.percentage(netIncome, new BigDecimal("40.0"));
        BigDecimal cap1340UvtMonthly = uvt.multiply(new BigDecimal("1340")).divide(TWELVE, 0, RoundingMode.HALF_UP);
        BigDecimal effectiveBenefits = totalBenefitsRequested.min(cap40Percent).min(cap1340UvtMonthly);
        BigDecimal truncatedByLaw2277 = totalBenefitsRequested.subtract(effectiveBenefits).max(BigDecimal.ZERO);

        // Beneficios adicionales anuales fuera del límite del 40% (Ley 2277 Art. 336)
        BigDecimal extraDep72UvtAnnual = uvt.multiply(BigDecimal.valueOf(72L * depCount));
        BigDecimal capElectronic240Uvt = uvt.multiply(new BigDecimal("240"));
        BigDecimal electronic1PctAnnual = MoneyCOP.percentage(MoneyCOP.ofSafeNonNegative(req.annualElectronicInvoicePurchasesCOP()), BigDecimal.ONE)
                .min(capElectronic240Uvt);

        // 6. Base Gravable y Tabla Art. 383 E.T.
        BigDecimal taxableBaseCOP = netIncome.subtract(effectiveBenefits).max(BigDecimal.ZERO);
        BigDecimal taxableBaseUVT = taxableBaseCOP.divide(uvt, 2, RoundingMode.HALF_UP);

        Art383Calculation bracketCalc = computeArt383Table(taxableBaseUVT, uvt);
        BigDecimal flat11 = MoneyCOP.percentage(grossIncome, new BigDecimal("11.0"));
        BigDecimal savingsVsFlat = flat11.subtract(bracketCalc.withholdingCOP());

        List<DepurationStepLine> steps = List.of(
                new DepurationStepLine("1", "Ingresos Laborales / Honorarios Brutos del Mes", "Art. 103 y 383 E.T.", grossIncome, toUvt(grossIncome, uvt)),
                new DepurationStepLine("2", "(-) Ingresos No Constitutivos de Renta (Salud 4% + Pensión 4% + FSP)", "Art. 55 y 56 E.T.", incrngo, toUvt(incrngo, uvt)),
                new DepurationStepLine("3", "(=) Ingreso Neto Mensual Base del Límite del 40%", "Art. 388 E.T.", netIncome, toUvt(netIncome, uvt)),
                new DepurationStepLine("4", "(-) Deducciones Art. 387 (Vivienda + Prepagada + Dependientes)", "Art. 387 E.T.", totalDeductions, toUvt(totalDeductions, uvt)),
                new DepurationStepLine("5", "(-) Aportes Voluntarios AFC / FPV y Renta Exenta 25% (Tope 790 UVT/año)", "Art. 126-1 y 206 num. 10 (Ley 2277)", afcAccepted.add(exempt25Accepted), toUvt(afcAccepted.add(exempt25Accepted), uvt)),
                new DepurationStepLine("6", "(=) Beneficios Efectivos Imputables (Limitados al 40% y 1.340 UVT/año)", "Art. 336 E.T. (Ley 2277)", effectiveBenefits, toUvt(effectiveBenefits, uvt)),
                new DepurationStepLine("7", "(=) Base Gravable Depurada para Tabla Art. 383 E.T.", "Art. 383 E.T.", taxableBaseCOP, taxableBaseUVT)
        );

        return new Law2277DepurationResult(
                grossIncome,
                incrngo,
                netIncome,
                housingAccepted,
                prepaidAccepted,
                depMonthlyAccepted,
                afcAccepted,
                exempt25Prelim,
                cap790UvtMonthly,
                exempt25Accepted,
                totalBenefitsRequested,
                cap40Percent,
                cap1340UvtMonthly,
                effectiveBenefits,
                truncatedByLaw2277,
                extraDep72UvtAnnual,
                electronic1PctAnnual,
                taxableBaseCOP,
                taxableBaseUVT,
                bracketCalc.bracketLabel(),
                bracketCalc.withholdingCOP(),
                flat11,
                savingsVsFlat,
                steps
        );
    }

    private RegimeComparisonResult computeRegimeComparison(Law2277AndRstRequest req, BigDecimal uvt) {
        BigDecimal grossRev = MoneyCOP.requireNonNegative(req.annualGrossRevenueCOP(), "annualGrossRevenueCOP");
        String group = req.rstActivityGroup() != null && !req.rstActivityGroup().isBlank()
                ? req.rstActivityGroup().trim().toUpperCase()
                : "GRUPO_2_COMERCIO_INDUSTRIA";

        if (grossRev.compareTo(BigDecimal.ZERO) == 0) {
            return new RegimeComparisonResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    group, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO,
                    "ORDINARIO", BigDecimal.ZERO, BigDecimal.ZERO,
                    "Sin ingresos brutos en el período ($0 COP). Impuesto estimado en ambos regímenes: $0 COP."
            );
        }

        BigDecimal costs = MoneyCOP.ofSafeNonNegative(req.annualDeductibleCostsAndExpensesCOP()).min(grossRev);
        BigDecimal pensionEmployer = MoneyCOP.ofSafeNonNegative(req.annualEmployerPensionContributionsCOP());
        BigDecimal electronicSales = MoneyCOP.ofSafeNonNegative(req.annualElectronicPaymentsReceivedCOP()).min(grossRev);
        BigDecimal icaPerThousand = req.municipalIcaRatePerThousand() != null && req.municipalIcaRatePerThousand().compareTo(BigDecimal.ZERO) > 0
                ? req.municipalIcaRatePerThousand()
                : new BigDecimal("9.66");

        BigDecimal grossRevUvt = grossRev.divide(uvt, 2, RoundingMode.HALF_UP);
        BigDecimal costRatioPct = costs.multiply(HUNDRED).divide(grossRev, 2, RoundingMode.HALF_UP);

        // 1. Régimen Ordinario (35% sobre Renta Líquida + ICA municipal deducible)
        BigDecimal municipalIca = grossRev.multiply(icaPerThousand).divide(THOUSAND, 0, RoundingMode.HALF_UP);
        BigDecimal ordinaryTaxableIncome = grossRev.subtract(costs).subtract(municipalIca).max(BigDecimal.ZERO);
        BigDecimal ordinaryTax35 = MoneyCOP.percentage(ordinaryTaxableIncome, new BigDecimal("35.0"));
        BigDecimal ordinaryTotalBurden = ordinaryTax35.add(municipalIca);
        BigDecimal ordinaryTet = ordinaryTotalBurden.multiply(HUNDRED).divide(grossRev, 2, RoundingMode.HALF_UP);

        // 2. Régimen Simple de Tributación (RST - Art. 905, 906 y 908 E.T.)
        BigDecimal maxRstUvt = "GRUPO_4_PROFESIONALES_CONSULTORIA".equals(group)
                ? new BigDecimal("12000")
                : new BigDecimal("100000");
        boolean eligibleForRst = grossRevUvt.compareTo(maxRstUvt) <= 0;

        BigDecimal rstRatePct = resolveRstRatePercent(group, grossRevUvt);
        BigDecimal rstGrossTax = grossRev.multiply(rstRatePct).divide(HUNDRED, 0, RoundingMode.HALF_UP);

        // Componente ICA territorial mínimo integrado en el SIMPLE (~0.7% del ingreso bruto que no puede ser absorbido por descuentos)
        BigDecimal minIcaComponentInRst = MoneyCOP.percentage(grossRev, new BigDecimal("0.7"));
        BigDecimal maxDiscountableRstPortion = rstGrossTax.subtract(minIcaComponentInRst).max(BigDecimal.ZERO);

        BigDecimal creditElectronic05 = MoneyCOP.percentage(electronicSales, new BigDecimal("0.5"));
        BigDecimal rawTotalCredits = pensionEmployer.add(creditElectronic05);
        BigDecimal effectiveCredits = rawTotalCredits.min(maxDiscountableRstPortion);

        BigDecimal rstNetPayable = rstGrossTax.subtract(effectiveCredits).max(minIcaComponentInRst);
        BigDecimal rstTet = rstNetPayable.multiply(HUNDRED).divide(grossRev, 2, RoundingMode.HALF_UP);

        boolean rstWins = eligibleForRst && rstNetPayable.compareTo(ordinaryTotalBurden) < 0;
        String recommended = rstWins ? "SIMPLE_RST" : "ORDINARIO";
        BigDecimal savings = eligibleForRst ? ordinaryTotalBurden.subtract(rstNetPayable).abs() : BigDecimal.ZERO;

        BigDecimal targetOrdinaryTax = rstNetPayable.subtract(municipalIca).max(BigDecimal.ZERO);
        BigDecimal breakEvenNetIncome = targetOrdinaryTax.multiply(HUNDRED).divide(new BigDecimal("35.0"), 0, RoundingMode.HALF_UP);
        BigDecimal breakEvenCosts = grossRev.subtract(municipalIca).subtract(breakEvenNetIncome).max(BigDecimal.ZERO);
        BigDecimal breakEvenRatioPct = breakEvenCosts.multiply(HUNDRED).divide(grossRev, 2, RoundingMode.HALF_UP);

        String verdict;
        if (!eligibleForRst) {
            verdict = "INHABILITADO PARA RST: Los ingresos brutos anuales (" + grossRevUvt + " UVT) superan el tope legal máximo de "
                    + maxRstUvt + " UVT del Art. 905/906 E.T. Debe tributar obligatoriamente en el Régimen Ordinario.";
        } else if (rstWins) {
            verdict = "RECOMENDACIÓN: Acogerse al Régimen Simple (RST) genera un ahorro tributario neto de $"
                    + savings.toPlainString() + " COP al año (Tasa Efectiva " + rstTet + "% vs " + ordinaryTet + "% en Ordinario).";
        } else {
            verdict = "RECOMENDACIÓN: Permanecer en el Régimen Ordinario ahorra $"
                    + savings.toPlainString() + " COP al año gracias a su estructura de costos/gastos del " + costRatioPct + "%.";
        }

        return new RegimeComparisonResult(
                grossRev,
                grossRevUvt,
                costs,
                costRatioPct,
                ordinaryTaxableIncome,
                ordinaryTax35,
                municipalIca,
                ordinaryTotalBurden,
                ordinaryTet,
                group,
                rstRatePct,
                rstGrossTax,
                pensionEmployer,
                creditElectronic05,
                rstNetPayable,
                rstTet,
                recommended,
                savings,
                breakEvenRatioPct,
                verdict
        );
    }

    private record Art383Calculation(String bracketLabel, BigDecimal withholdingCOP) {}

    private Art383Calculation computeArt383Table(BigDecimal baseUvt, BigDecimal uvt) {
        if (baseUvt.compareTo(new BigDecimal("95")) <= 0) {
            return new Art383Calculation("Rango 1 (0 a 95 UVT) — Tarifa 0% Exento", BigDecimal.ZERO);
        }
        BigDecimal taxUvt;
        String label;
        if (baseUvt.compareTo(new BigDecimal("150")) <= 0) {
            taxUvt = baseUvt.subtract(new BigDecimal("95")).multiply(new BigDecimal("0.19"));
            label = "Rango 2 (>95 a 150 UVT) — Marginal 19%";
        } else if (baseUvt.compareTo(new BigDecimal("360")) <= 0) {
            taxUvt = baseUvt.subtract(new BigDecimal("150")).multiply(new BigDecimal("0.28")).add(new BigDecimal("10"));
            label = "Rango 3 (>150 a 360 UVT) — Marginal 28% + 10 UVT";
        } else if (baseUvt.compareTo(new BigDecimal("640")) <= 0) {
            taxUvt = baseUvt.subtract(new BigDecimal("360")).multiply(new BigDecimal("0.33")).add(new BigDecimal("69"));
            label = "Rango 4 (>360 a 640 UVT) — Marginal 33% + 69 UVT";
        } else if (baseUvt.compareTo(new BigDecimal("945")) <= 0) {
            taxUvt = baseUvt.subtract(new BigDecimal("640")).multiply(new BigDecimal("0.35")).add(new BigDecimal("162"));
            label = "Rango 5 (>640 a 945 UVT) — Marginal 35% + 162 UVT";
        } else if (baseUvt.compareTo(new BigDecimal("2300")) <= 0) {
            taxUvt = baseUvt.subtract(new BigDecimal("945")).multiply(new BigDecimal("0.37")).add(new BigDecimal("268"));
            label = "Rango 6 (>945 a 2.300 UVT) — Marginal 37% + 268 UVT";
        } else {
            taxUvt = baseUvt.subtract(new BigDecimal("2300")).multiply(new BigDecimal("0.39")).add(new BigDecimal("770"));
            label = "Rango 7 (>2.300 UVT) — Marginal 39% + 770 UVT";
        }
        BigDecimal rawCop = taxUvt.multiply(uvt).setScale(0, RoundingMode.HALF_UP);
        return new Art383Calculation(label, MoneyCOP.roundToThousandArt868(rawCop));
    }

    private BigDecimal resolveRstRatePercent(String group, BigDecimal uvtIncome) {
        boolean tier1 = uvtIncome.compareTo(new BigDecimal("6000")) <= 0;
        boolean tier2 = uvtIncome.compareTo(new BigDecimal("15000")) <= 0;
        boolean tier3 = uvtIncome.compareTo(new BigDecimal("30000")) <= 0;

        return switch (group) {
            case "GRUPO_1_TIENDAS" -> tier1 ? new BigDecimal("1.2") : (tier2 ? new BigDecimal("2.2") : (tier3 ? new BigDecimal("4.4") : new BigDecimal("5.6")));
            case "GRUPO_3_RESTAURANTES_TRANSPORTE" -> tier1 ? new BigDecimal("3.1") : (tier2 ? new BigDecimal("3.4") : (tier3 ? new BigDecimal("4.0") : new BigDecimal("4.5")));
            case "GRUPO_4_PROFESIONALES_CONSULTORIA" -> tier1 ? new BigDecimal("7.3") : new BigDecimal("8.3");
            default -> tier1 ? new BigDecimal("1.6") : (tier2 ? new BigDecimal("2.0") : (tier3 ? new BigDecimal("3.5") : new BigDecimal("4.5")));
        };
    }

    private BigDecimal toUvt(BigDecimal cop, BigDecimal uvt) {
        return cop.divide(uvt, 2, RoundingMode.HALF_UP);
    }
}
