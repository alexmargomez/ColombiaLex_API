package com.alcontable.exogena.application.usecases;

import com.alcontable.exogena.domain.model.MunicipalityEntity;
import com.alcontable.exogena.domain.ports.MunicipalityRepositoryPort;
import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.shared.domain.vo.MoneyCOP;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExogenaBatchAuditUseCase {

    private static final int[] DIAN_PRIMES = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record ConceptRule(
            String conceptCode,
            String conceptLabel,
            String exogenaFormat,
            String exogenaConceptCode,
            BigDecimal minUvtThreshold,
            BigDecimal declarantRatePercent,
            BigDecimal nonDeclarantRatePercent
    ) {}

    private static final Map<String, ConceptRule> CONCEPT_RULES;

    static {
        Map<String, ConceptRule> map = new LinkedHashMap<>();
        ConceptRule compras = new ConceptRule("COMPRAS", "Compras Generales", "1001", "5007", new BigDecimal("27"), new BigDecimal("2.5"), new BigDecimal("3.5"));
        ConceptRule servicios = new ConceptRule("SERVICIOS", "Servicios en General", "1001", "5004", new BigDecimal("4"), new BigDecimal("4.0"), new BigDecimal("6.0"));
        ConceptRule honorarios = new ConceptRule("HONORARIOS", "Honorarios y Consultoría", "1001", "5002", BigDecimal.ZERO, new BigDecimal("11.0"), new BigDecimal("10.0"));
        ConceptRule comisiones = new ConceptRule("COMISIONES", "Comisiones", "1001", "5003", BigDecimal.ZERO, new BigDecimal("11.0"), new BigDecimal("10.0"));
        ConceptRule arrInmueble = new ConceptRule("ARRENDAMIENTO_INMUEBLE", "Arrendamiento Bienes Inmuebles", "1001", "5005", new BigDecimal("27"), new BigDecimal("3.5"), new BigDecimal("3.5"));
        ConceptRule arrMueble = new ConceptRule("ARRENDAMIENTO_MUEBLE", "Arrendamiento Bienes Muebles", "1001", "5006", BigDecimal.ZERO, new BigDecimal("4.0"), new BigDecimal("4.0"));
        ConceptRule transporte = new ConceptRule("TRANSPORTE_CARGA", "Transporte Nacional de Carga", "1001", "5004", new BigDecimal("4"), new BigDecimal("1.0"), new BigDecimal("1.0"));
        ConceptRule ingresos = new ConceptRule("INGRESOS_OPERACIONALES", "Ingresos Brutos Operacionales", "1007", "4001", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        map.put("COMPRAS", compras);
        map.put("COMPRAS_GENERALES", compras);
        map.put("SERVICIOS", servicios);
        map.put("SERVICIOS_GENERALES", servicios);
        map.put("HONORARIOS", honorarios);
        map.put("HONORARIOS_COMISIONES", honorarios);
        map.put("COMISIONES", comisiones);
        map.put("ARRENDAMIENTO_INMUEBLE", arrInmueble);
        map.put("ARRENDAMIENTO_INMUEBLES", arrInmueble);
        map.put("ARRENDAMIENTOS_INMUEBLES", arrInmueble);
        map.put("ARRENDAMIENTO_MUEBLE", arrMueble);
        map.put("ARRENDAMIENTO_MUEBLES", arrMueble);
        map.put("ARRENDAMIENTOS_MUEBLES", arrMueble);
        map.put("TRANSPORTE_CARGA", transporte);
        map.put("INGRESOS_OPERACIONALES", ingresos);
        CONCEPT_RULES = Map.copyOf(map);
    }

    private final MunicipalityRepositoryPort municipalityRepositoryPort;

    public ExogenaBatchAuditUseCase(MunicipalityRepositoryPort municipalityRepositoryPort) {
        this.municipalityRepositoryPort = municipalityRepositoryPort;
    }

    public record ExogenaRowInput(
            String rawNitOrDocument,
            String thirdPartyName,
            String conceptCode,
            Boolean declarant,
            BigDecimal basePaidCOP,
            BigDecimal withholdingPracticedCOP,
            String rawMunicipality
    ) {}

    public record ExogenaBatchRequest(
            Integer year,
            String rawTsvText,
            List<ExogenaRowInput> items
    ) {}

    public record ExogenaAuditedRow(
            int rowNumber,
            String rawNitInput,
            String cleanIdentificationNumber,
            Integer providedDv,
            int officialCalculatedDv,
            String formattedNitWithDv,
            String dianDocumentTypeCode, // 31 NIT, 13 CC, 43 Cuantías Menores
            String dvValidationStatus, // DV_OK, DV_CORREGIDO_ERROR_CONTABLE, DV_CALCULADO_NUEVO, DOCUMENTO_INVALIDO_O_CORRUPTO
            String thirdPartyName,
            String daneDepartmentCode,
            String daneMunicipalityCode,
            String standardizedMunicipalityName,
            String conceptCode,
            String exogenaFormat,
            String exogenaConceptCode,
            boolean declarant,
            BigDecimal basePaidCOP,
            BigDecimal minimumBaseCOP,
            boolean exceedsMinimumUvtBase,
            BigDecimal legalRatePercent,
            BigDecimal expectedLegalWithholdingCOP,
            BigDecimal withholdingPracticedCOP,
            BigDecimal differenceShortfallCOP,
            String withholdingAuditStatus, // EXACTA, RETUVO_DE_MENOS_RIESGO_DIAN, RETUVO_DE_MAS
            String auditObservation
    ) {}

    public record ExogenaBatchAuditResponse(
            int year,
            BigDecimal uvtValueCOP,
            int totalRowsProcessed,
            int dvErrorsCorrectedCount,
            int underWithholdingAlertsCount,
            int overWithholdingAlertsCount,
            BigDecimal totalBaseAuditedCOP,
            BigDecimal totalWithholdingPracticedCOP,
            BigDecimal totalExpectedLegalWithholdingCOP,
            BigDecimal totalShortfallRiskArt370COP,
            BigDecimal form350RoundedTotalWithholdingCOP,
            List<ExogenaAuditedRow> auditedRows
    ) {}

    private record NormalizedMunicipality(
            String departmentCode,
            String daneCode,
            String officialName,
            String normalizedName
    ) {}

    public ExogenaBatchAuditResponse execute(ExogenaBatchRequest request) {
        int year = (request != null && request.year() != null) ? request.year() : 2026;
        BigDecimal uvt = LaborLegalConstants.forYear(year).uvtCOP();

        List<ExogenaRowInput> rowsToProcess = new ArrayList<>();
        if (request != null && request.rawTsvText() != null && !request.rawTsvText().isBlank()) {
            rowsToProcess.addAll(parseRawTextLines(request.rawTsvText()));
        }
        if (request != null && request.items() != null && !request.items().isEmpty()) {
            rowsToProcess.addAll(request.items());
        }
        if (rowsToProcess.isEmpty()) {
            throw new DomainValidationException("Debe enviar filas o pegar texto desde Excel para ejecutar la pre-auditoría de Exógena y Retenciones.");
        }

        List<NormalizedMunicipality> normalizedCatalog = municipalityRepositoryPort.findAll().stream()
                .map(m -> new NormalizedMunicipality(
                        m.getDepartmentCode(),
                        m.getDaneCode(),
                        m.getMunicipalityName(),
                        stripAccents(m.getMunicipalityName()).toUpperCase(Locale.ROOT).trim()
                ))
                .toList();

        int dvErrorCount = 0;
        int underWithheldCount = 0;
        int overWithheldCount = 0;
        BigDecimal sumBase = BigDecimal.ZERO;
        BigDecimal sumPracticed = BigDecimal.ZERO;
        BigDecimal sumExpected = BigDecimal.ZERO;
        BigDecimal sumShortfall = BigDecimal.ZERO;

        List<ExogenaAuditedRow> auditedRows = new ArrayList<>();
        int idx = 1;

        for (ExogenaRowInput row : rowsToProcess) {
            if (row == null) {
                throw new DomainValidationException("El lote de Exógena contiene una fila nula.");
            }
            ParsedNit parsedNit = cleanAndComputeModulo11Tolerant(row.rawNitOrDocument());
            if ("DV_CORREGIDO_ERROR_CONTABLE".equals(parsedNit.status())
                    || "DOCUMENTO_INVALIDO_O_CORRUPTO".equals(parsedNit.status())) {
                dvErrorCount++;
            }

            MunicipalityMatch muni = matchMunicipality(row.rawMunicipality(), normalizedCatalog);

            String rawConcept = row.conceptCode() != null && !row.conceptCode().isBlank()
                    ? row.conceptCode().trim().toUpperCase()
                    : "COMPRAS";
            boolean unknownConcept = !CONCEPT_RULES.containsKey(rawConcept);
            ConceptRule rule = CONCEPT_RULES.getOrDefault(rawConcept, CONCEPT_RULES.get("COMPRAS"));
            boolean isDeclarant = row.declarant() == null || row.declarant();

            BigDecimal basePaid = MoneyCOP.ofSafeNonNegative(row.basePaidCOP());
            BigDecimal practiced = MoneyCOP.ofSafeNonNegative(row.withholdingPracticedCOP());
            BigDecimal minBaseCOP = rule.minUvtThreshold().multiply(uvt).setScale(0, RoundingMode.HALF_UP);
            boolean exceedsMin = basePaid.compareTo(minBaseCOP) >= 0;

            BigDecimal legalRate = isDeclarant ? rule.declarantRatePercent() : rule.nonDeclarantRatePercent();
            BigDecimal expectedWithholding = (exceedsMin && legalRate.compareTo(BigDecimal.ZERO) > 0)
                    ? basePaid.multiply(legalRate).divide(HUNDRED, 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal diffShortfall = expectedWithholding.subtract(practiced);
            String auditStatus;
            String observation;

            if (diffShortfall.compareTo(new BigDecimal("1000")) > 0) {
                auditStatus = "RETUVO_DE_MENOS_RIESGO_DIAN";
                underWithheldCount++;
                sumShortfall = sumShortfall.add(diffShortfall);
                observation = "Faltante de $" + diffShortfall.toPlainString()
                        + " COP (Riesgo responsabilidad solidaria Art. 370 E.T. y rechazo costo Art. 177 E.T.)";
            } else if (diffShortfall.compareTo(new BigDecimal("-1000")) < 0) {
                auditStatus = "RETUVO_DE_MAS";
                overWithheldCount++;
                observation = "Retuvo $" + diffShortfall.abs().toPlainString()
                        + " COP en exceso frente a tarifa/tope legal (" + legalRate + "%)";
            } else {
                auditStatus = "EXACTA";
                observation = "Retención y base consistentes con norma DIAN (" + rule.exogenaFormat() + "/" + rule.exogenaConceptCode() + ")";
            }

            if ("DOCUMENTO_INVALIDO_O_CORRUPTO".equals(parsedNit.status())) {
                observation = "ALERTA RUT: Documento '" + (row.rawNitOrDocument() != null ? row.rawNitOrDocument() : "")
                        + "' con longitud anómala. Verificar en RUT DIAN | " + observation;
            }
            if (unknownConcept) {
                observation = "ADVERTENCIA: Concepto '" + rawConcept + "' no tipificado, homologado preventivamente a COMPRAS | " + observation;
            }

            BigDecimal rowShortfall = "RETUVO_DE_MENOS_RIESGO_DIAN".equals(auditStatus)
                    ? diffShortfall.max(BigDecimal.ZERO)
                    : BigDecimal.ZERO;

            String docTypeCode = parsedNit.cleanDigits().startsWith("8") || parsedNit.cleanDigits().startsWith("9") ? "31" : "13";
            if (basePaid.compareTo(uvt.multiply(new BigDecimal("3"))) < 0 && practiced.compareTo(BigDecimal.ZERO) == 0) {
                observation += " | Sugerencia: Agrupable en Cuantías Menores (NIT 222222222)";
            }

            auditedRows.add(new ExogenaAuditedRow(
                    idx++,
                    row.rawNitOrDocument() != null ? row.rawNitOrDocument().trim() : "",
                    parsedNit.cleanDigits(),
                    parsedNit.providedDv(),
                    parsedNit.calculatedDv(),
                    parsedNit.cleanDigits() + "-" + parsedNit.calculatedDv(),
                    docTypeCode,
                    parsedNit.status(),
                    row.thirdPartyName() != null && !row.thirdPartyName().isBlank() ? row.thirdPartyName().trim().toUpperCase() : "TERCERO SIN NOMBRE",
                    muni.departmentCode(),
                    muni.daneCode(),
                    muni.municipalityName(),
                    rule.conceptCode(),
                    rule.exogenaFormat(),
                    rule.exogenaConceptCode(),
                    isDeclarant,
                    basePaid,
                    minBaseCOP,
                    exceedsMin,
                    legalRate,
                    expectedWithholding,
                    practiced,
                    rowShortfall,
                    auditStatus,
                    observation
            ));

            sumBase = sumBase.add(basePaid);
            sumPracticed = sumPracticed.add(practiced);
            sumExpected = sumExpected.add(expectedWithholding);
        }

        BigDecimal roundedForm350 = MoneyCOP.roundToThousandArt868(sumExpected);

        return new ExogenaBatchAuditResponse(
                year,
                uvt,
                auditedRows.size(),
                dvErrorCount,
                underWithheldCount,
                overWithheldCount,
                sumBase,
                sumPracticed,
                sumExpected,
                sumShortfall,
                roundedForm350,
                auditedRows
        );
    }

    private List<ExogenaRowInput> parseRawTextLines(String rawText) {
        List<ExogenaRowInput> parsed = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cols;
            if (line.contains("\t")) {
                cols = line.split("\t");
            } else if (line.contains("|")) {
                cols = line.split("\\|");
            } else if (line.contains(";")) {
                cols = line.split(";");
            } else {
                cols = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            }
            if (cols.length < 2) {
                continue;
            }
            String rawNit = cols[0].replace("\"", "").trim();
            if (rawNit.equalsIgnoreCase("NIT") || rawNit.toUpperCase().startsWith("DOCUMENTO")) {
                continue;
            }
            String name = cols.length > 1 ? cols[1].replace("\"", "").trim() : "TERCERO";
            String concept = cols.length > 2 ? cols[2].replace("\"", "").trim().toUpperCase() : "COMPRAS";
            boolean declarant = cols.length <= 3 || !cols[3].replace("\"", "").trim().toUpperCase().contains("NO");
            BigDecimal base = cols.length > 4 ? parseMoney(cols[4]) : BigDecimal.ZERO;
            BigDecimal practiced = cols.length > 5 ? parseMoney(cols[5]) : BigDecimal.ZERO;
            String city = cols.length > 6 ? cols[6].replace("\"", "").trim() : "BOGOTA";

            parsed.add(new ExogenaRowInput(rawNit, name, concept, declarant, base, practiced, city));
        }
        return parsed;
    }

    /**
     * Parsea montos monetarios copiados desde Excel o CSV en formato colombiano ("1.500.000,50")
     * o estadounidense ("1,500,000.50" o "1500000.50") sin multiplicar los centavos por 100.
     */
    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        String cleaned = raw.replace("\"", "").trim().replaceAll("[^0-9.,-]", "");
        if (cleaned.isBlank() || "-".equals(cleaned)) {
            return BigDecimal.ZERO;
        }
        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        if (lastComma > lastDot) {
            if (cleaned.indexOf(',') == lastComma && cleaned.length() - lastComma - 1 <= 2) {
                cleaned = cleaned.replace(".", "").replace(',', '.');
            } else {
                cleaned = cleaned.replace(".", "").replace(",", "");
            }
        } else if (lastDot > lastComma) {
            if (cleaned.indexOf('.') == lastDot && cleaned.length() - lastDot - 1 <= 2) {
                cleaned = cleaned.replace(",", "");
            } else {
                cleaned = cleaned.replace(",", "").replace(".", "");
            }
        }
        try {
            return new BigDecimal(cleaned).setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private record ParsedNit(String cleanDigits, Integer providedDv, int calculatedDv, String status) {}

    /**
     * Limpia el NIT y calcula el Dígito de Verificación Módulo 11 de forma tolerante a fallos por fila,
     * para que un registro con NIT corrupto sea marcado en el reporte sin abortar el lote completo.
     */
    private ParsedNit cleanAndComputeModulo11Tolerant(String rawNit) {
        if (rawNit == null || rawNit.isBlank()) {
            return new ParsedNit("000000000", null, 0, "DOCUMENTO_INVALIDO_O_CORRUPTO");
        }
        String normalized = rawNit.trim().toUpperCase()
                .replace("NIT", "")
                .replace("CC", "")
                .replace(".", "")
                .replace(" ", "");

        Integer providedDv = null;
        String basePart;
        if (normalized.contains("-")) {
            String[] split = normalized.split("-", 2);
            basePart = split[0].replaceAll("[^0-9]", "");
            String dvPart = split[1].replaceAll("[^0-9]", "");
            if (!dvPart.isEmpty()) {
                providedDv = Character.getNumericValue(dvPart.charAt(0));
            }
        } else {
            basePart = normalized.replaceAll("[^0-9]", "");
            if (basePart.length() == 10 && (basePart.startsWith("8") || basePart.startsWith("9"))) {
                providedDv = Character.getNumericValue(basePart.charAt(9));
                basePart = basePart.substring(0, 9);
            }
        }

        if (basePart.length() < 5 || basePart.length() > 15) {
            String safeDigits = basePart.isEmpty() ? "00000" : basePart;
            return new ParsedNit(safeDigits, providedDv, 0, "DOCUMENTO_INVALIDO_O_CORRUPTO");
        }

        int sum = 0;
        int len = basePart.length();
        for (int i = 0; i < len; i++) {
            int digit = Character.getNumericValue(basePart.charAt(len - 1 - i));
            sum += digit * DIAN_PRIMES[i];
        }
        int mod = sum % 11;
        int calcDv = (mod == 0 || mod == 1) ? mod : (11 - mod);

        String status;
        if (providedDv == null) {
            status = "DV_CALCULADO_NUEVO";
        } else if (providedDv == calcDv) {
            status = "DV_OK";
        } else {
            status = "DV_CORREGIDO_ERROR_CONTABLE";
        }

        return new ParsedNit(basePart, providedDv, calcDv, status);
    }

    private record MunicipalityMatch(String departmentCode, String daneCode, String municipalityName) {}

    private MunicipalityMatch matchMunicipality(String rawCity, List<NormalizedMunicipality> catalog) {
        if (rawCity == null || rawCity.isBlank() || catalog.isEmpty()) {
            return new MunicipalityMatch("11", "11001", "BOGOTÁ, D.C.");
        }
        String normQuery = stripAccents(rawCity).toUpperCase(Locale.ROOT).trim();
        // 1ra pasada: Coincidencia exacta por código DANE o nombre exacto
        for (NormalizedMunicipality m : catalog) {
            if (m.daneCode().equals(normQuery) || m.normalizedName().equals(normQuery)) {
                return new MunicipalityMatch(m.departmentCode(), m.daneCode(), m.officialName());
            }
        }
        // 2da pasada: Coincidencia por prefijo o inclusión
        for (NormalizedMunicipality m : catalog) {
            if (m.normalizedName().startsWith(normQuery)
                    || m.normalizedName().contains(normQuery)
                    || normQuery.contains(m.normalizedName())) {
                return new MunicipalityMatch(m.departmentCode(), m.daneCode(), m.officialName());
            }
        }
        return new MunicipalityMatch("11", "11001", "BOGOTÁ, D.C.");
    }

    private String stripAccents(String input) {
        if (input == null) {
            return "";
        }
        return Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
