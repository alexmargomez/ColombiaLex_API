package com.alcontable.taxplanning.application.usecases;

import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.shared.domain.vo.MoneyCOP;
import com.alcontable.taxplanning.domain.vo.NitVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TaxDianUseCase {

    public record UvtConversionResponse(
            int year,
            BigDecimal uvtValueCOP,
            BigDecimal smlmvValueCOP,
            BigDecimal inputAmount,
            String inputUnit,
            BigDecimal equivalentCOP,
            BigDecimal equivalentUVT,
            BigDecimal equivalentSMLMV
    ) {}

    public record WithholdingRuleConcept(
            String code,
            String name,
            BigDecimal minimumUvtThreshold,
            BigDecimal declarantRatePercent,
            BigDecimal nonDeclarantRatePercent,
            String legalReference
    ) {}

    public record WithholdingCalculationRequest(
            Integer year,
            String conceptCode,
            BigDecimal subtotalBeforeVatCOP,
            Boolean declarantTaxpayer,
            BigDecimal vatRatePercent
    ) {}

    public record WithholdingCalculationResponse(
            int year,
            String conceptCode,
            String conceptName,
            BigDecimal uvtYearCOP,
            BigDecimal minimumBaseUVT,
            BigDecimal minimumBaseCOP,
            BigDecimal subtotalBeforeVatCOP,
            BigDecimal vatRatePercent,
            BigDecimal vatAmountCOP,
            boolean appliesWithholding,
            BigDecimal withholdingRatePercent,
            BigDecimal withholdingAmountCOP,
            BigDecimal netPayableToProviderCOP,
            String explanation
    ) {}

    public record CiiuActivityItem(
            String code,
            String section,
            String description,
            String typicalWithholdingConcept,
            BigDecimal referenceIcaPerThousand
    ) {}

    private static final List<WithholdingRuleConcept> WITHHOLDING_CONCEPTS = List.of(
            new WithholdingRuleConcept("COMPRAS_GENERALES", "Compras generales de bienes muebles", new BigDecimal("27"), new BigDecimal("2.5"), new BigDecimal("3.5"), "Art. 401 E.T. / Decreto 2418"),
            new WithholdingRuleConcept("SERVICIOS_GENERALES", "Servicios en general", new BigDecimal("4"), new BigDecimal("4.0"), new BigDecimal("6.0"), "Art. 392 E.T."),
            new WithholdingRuleConcept("HONORARIOS_COMISIONES", "Honorarios y comisiones profesionales", new BigDecimal("0"), new BigDecimal("11.0"), new BigDecimal("10.0"), "Art. 392 E.T."),
            new WithholdingRuleConcept("ARRENDAMIENTO_INMUEBLES", "Arrendamiento de bienes raíces (inmuebles)", new BigDecimal("27"), new BigDecimal("3.5"), new BigDecimal("3.5"), "Art. 401 E.T."),
            new WithholdingRuleConcept("ARRENDAMIENTO_MUEBLES", "Arrendamiento de bienes muebles (maquinaria/equipos)", new BigDecimal("0"), new BigDecimal("4.0"), new BigDecimal("4.0"), "Art. 401 E.T."),
            new WithholdingRuleConcept("TRANSPORTE_CARGA", "Servicios nacionales de transporte de carga", new BigDecimal("4"), new BigDecimal("1.0"), new BigDecimal("1.0"), "Art. 392 E.T."),
            new WithholdingRuleConcept("RESTAURANTES_HOTELES", "Servicios de hoteles, restaurantes y hospedaje", new BigDecimal("4"), new BigDecimal("3.5"), new BigDecimal("3.5"), "Art. 401 E.T."),
            new WithholdingRuleConcept("LICENCIAMIENTO_SOFTWARE", "Licenciamiento y desarrollo de software / TI", new BigDecimal("0"), new BigDecimal("3.5"), new BigDecimal("3.5"), "Art. 401 / 392 E.T.")
    );

    private static final List<CiiuActivityItem> CIIU_CATALOG = List.of(
            new CiiuActivityItem("6201", "J - Información y Comunicaciones", "Actividades de desarrollo de sistemas informáticos (planificación, análisis, diseño, programación, pruebas)", "HONORARIOS_COMISIONES", new BigDecimal("9.66")),
            new CiiuActivityItem("6202", "J - Información y Comunicaciones", "Actividades de consultoría informática y actividades de administración de instalaciones informáticas", "HONORARIOS_COMISIONES", new BigDecimal("9.66")),
            new CiiuActivityItem("6311", "J - Información y Comunicaciones", "Procesamiento de datos, alojamiento (hosting) y actividades relacionadas", "SERVICIOS_GENERALES", new BigDecimal("9.66")),
            new CiiuActivityItem("8610", "Q - Salud Humana", "Actividades de hospitales y clínicas, con internación", "SERVICIOS_GENERALES", new BigDecimal("6.90")),
            new CiiuActivityItem("8621", "Q - Salud Humana", "Actividades de la práctica médica, sin internación (Consulta Externa / IPS)", "SERVICIOS_GENERALES", new BigDecimal("6.90")),
            new CiiuActivityItem("4520", "G - Comercio y Talleres", "Mantenimiento y reparación de vehículos automotores", "SERVICIOS_GENERALES", new BigDecimal("9.66")),
            new CiiuActivityItem("4541", "G - Comercio y Talleres", "Comercio de motocicletas y de sus partes, piezas y accesorios", "COMPRAS_GENERALES", new BigDecimal("11.04")),
            new CiiuActivityItem("4711", "G - Comercio y Talleres", "Comercio al por menor en establecimientos no especializados (Supermercados / POS)", "COMPRAS_GENERALES", new BigDecimal("4.14")),
            new CiiuActivityItem("5511", "I - Alojamiento y Restaurantes", "Alojamiento en hoteles y centros turísticos", "RESTAURANTES_HOTELES", new BigDecimal("13.80")),
            new CiiuActivityItem("5611", "I - Alojamiento y Restaurantes", "Expendio a la mesa de comidas preparadas (Restaurantes)", "RESTAURANTES_HOTELES", new BigDecimal("13.80")),
            new CiiuActivityItem("6910", "M - Actividades Profesionales", "Actividades jurídicas y asesoría legal", "HONORARIOS_COMISIONES", new BigDecimal("9.66")),
            new CiiuActivityItem("6920", "M - Actividades Profesionales", "Actividades de contabilidad, teneduría de libros, auditoría financiera y asesoría tributaria", "HONORARIOS_COMISIONES", new BigDecimal("9.66")),
            new CiiuActivityItem("4923", "H - Transporte", "Transporte de carga por carretera", "TRANSPORTE_CARGA", new BigDecimal("4.14"))
    );

    public NitVO.NitVerificationReport verifyNit(String rawNit) {
        return NitVO.inspectAndCalculate(rawNit);
    }

    public UvtConversionResponse convertUnits(Integer year, BigDecimal amount, String unit) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainValidationException("El monto a convertir debe ser mayor o igual a 0.");
        }
        int effectiveYear = year != null ? year : 2026;
        var params = LaborLegalConstants.forYear(effectiveYear);
        BigDecimal uvt = params.uvt();
        BigDecimal smlmv = params.smlmv();
        String normUnit = unit != null ? unit.trim().toUpperCase() : "UVT";

        BigDecimal cop;
        switch (normUnit) {
            case "COP", "PESOS" -> cop = amount.setScale(0, RoundingMode.HALF_UP);
            case "SMLMV", "SMMLV" -> cop = amount.multiply(smlmv).setScale(0, RoundingMode.HALF_UP);
            default -> {
                normUnit = "UVT";
                cop = amount.multiply(uvt).setScale(0, RoundingMode.HALF_UP);
            }
        }

        BigDecimal eqUvt = cop.divide(uvt, 4, RoundingMode.HALF_UP);
        BigDecimal eqSmlmv = cop.divide(smlmv, 4, RoundingMode.HALF_UP);

        return new UvtConversionResponse(
                params.year(),
                uvt,
                smlmv,
                amount,
                normUnit,
                cop,
                eqUvt,
                eqSmlmv
        );
    }

    public WithholdingCalculationResponse calculateWithholding(WithholdingCalculationRequest req) {
        if (req == null || req.subtotalBeforeVatCOP() == null || req.subtotalBeforeVatCOP().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainValidationException("El subtotal antes de IVA debe ser mayor a 0 COP.");
        }

        int year = req.year() != null ? req.year() : 2026;
        var params = LaborLegalConstants.forYear(year);
        BigDecimal uvt = params.uvt();

        String code = req.conceptCode() != null ? req.conceptCode().trim().toUpperCase() : "COMPRAS_GENERALES";
        WithholdingRuleConcept concept = WITHHOLDING_CONCEPTS.stream()
                .filter(c -> c.code().equalsIgnoreCase(code))
                .findFirst()
                .orElse(WITHHOLDING_CONCEPTS.get(0));

        MoneyCOP subtotal = MoneyCOP.of(req.subtotalBeforeVatCOP());
        BigDecimal vatPercent = req.vatRatePercent() != null && req.vatRatePercent().compareTo(BigDecimal.ZERO) >= 0
                ? req.vatRatePercent()
                : new BigDecimal("19");
        MoneyCOP vatAmount = subtotal.multiply(vatPercent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)).roundToPesos();

        MoneyCOP minBaseCop = MoneyCOP.of(concept.minimumUvtThreshold().multiply(uvt)).roundToPesos();
        boolean applies = subtotal.isGreaterThanOrEqualTo(minBaseCop);

        boolean isDeclarant = req.declarantTaxpayer() == null || req.declarantTaxpayer();
        BigDecimal ratePercent = isDeclarant ? concept.declarantRatePercent() : concept.nonDeclarantRatePercent();

        MoneyCOP withholdingCop = applies
                ? subtotal.multiply(ratePercent.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP)).roundToPesos()
                : MoneyCOP.ZERO;

        MoneyCOP netPayable = subtotal.add(vatAmount).subtract(withholdingCop).roundToPesos();

        String explanation = applies
                ? String.format("El subtotal (%s) supera el tope mínimo de %s UVT (%s). Se aplica tarifa del %s%% (%s).",
                subtotal, concept.minimumUvtThreshold(), minBaseCop, ratePercent, isDeclarant ? "Declarante" : "No Declarante")
                : String.format("No aplica Retención en la Fuente porque el subtotal (%s) es inferior a la base mínima de %s UVT (%s).",
                subtotal, concept.minimumUvtThreshold(), minBaseCop);

        return new WithholdingCalculationResponse(
                params.year(),
                concept.code(),
                concept.name(),
                uvt,
                concept.minimumUvtThreshold(),
                minBaseCop.roundedPesosValue(),
                subtotal.roundedPesosValue(),
                vatPercent,
                vatAmount.roundedPesosValue(),
                applies,
                applies ? ratePercent : BigDecimal.ZERO,
                withholdingCop.roundedPesosValue(),
                netPayable.roundedPesosValue(),
                explanation
        );
    }

    public List<WithholdingRuleConcept> listWithholdingConcepts() {
        return WITHHOLDING_CONCEPTS;
    }

    public List<CiiuActivityItem> searchCiiu(String query) {
        if (query == null || query.isBlank()) {
            return CIIU_CATALOG;
        }
        String q = query.trim().toLowerCase();
        return CIIU_CATALOG.stream()
                .filter(c -> c.code().toLowerCase().contains(q)
                        || c.description().toLowerCase().contains(q)
                        || c.section().toLowerCase().contains(q))
                .toList();
    }
}
