package com.alcontable.portfolio.application.usecases;

import com.alcontable.portfolio.domain.ports.AccountingPortfolioRepositoryPort;
import com.alcontable.portfolio.infrastructure.persistence.*;
import com.alcontable.sanctions.domain.service.EmilianiHolidayEngine;
import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.shared.domain.exceptions.DomainValidationException;
import com.alcontable.shared.domain.vo.MoneyCOP;
import com.alcontable.taxplanning.domain.vo.NitVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ClientPortfolioUseCase {

    private static final Set<String> VALID_OBLIGATION_STATUSES = Set.of(
            "PENDIENTE", "EN_PREPARACION", "PRESENTADA", "VENCIDA"
    );

    private final AccountingPortfolioRepositoryPort portfolioPort;
    private final EmilianiHolidayEngine holidayEngine;

    @Autowired
    public ClientPortfolioUseCase(
            AccountingPortfolioRepositoryPort portfolioPort,
            EmilianiHolidayEngine holidayEngine
    ) {
        this.portfolioPort = portfolioPort;
        this.holidayEngine = holidayEngine;
    }

    public ClientPortfolioUseCase(AccountingPortfolioRepositoryPort portfolioPort) {
        this(portfolioPort, new EmilianiHolidayEngine());
    }

    public record CreateClientRequest(
            String nit,
            String businessName,
            String personType,
            String taxRegime,
            String ivaPeriodicity,
            String dianResponsibilities,
            String municipalityDaneCode,
            String municipalityName,
            BigDecimal estimatedAnnualIncomeCop,
            Integer employeesCount,
            Boolean appliesArt1141Exemption
    ) {}

    public record ClientSummaryDto(
            Long id,
            String nit,
            String dv,
            String formattedNit,
            Integer lastNitDigit,
            String businessName,
            String personType,
            String taxRegime,
            String ivaPeriodicity,
            String dianResponsibilities,
            String municipalityDaneCode,
            String municipalityName,
            BigDecimal estimatedAnnualIncomeCop,
            Integer employeesCount,
            Boolean appliesArt1141Exemption,
            String status
    ) {}

    public record ObligationRadarDto(
            Long id,
            Long clientId,
            String clientBusinessName,
            String clientFormattedNit,
            Integer lastNitDigit,
            String clientTaxRegime,
            String declarationType,
            String formCode,
            String taxPeriod,
            LocalDate dueDate,
            String status,
            long daysUntilOrOverdue,
            String urgencyLevel,
            BigDecimal estimatedTaxCop,
            BigDecimal calculatedSanctionCop
    ) {}

    public record WorkpaperDto(
            Long id,
            Long clientId,
            String clientBusinessName,
            String clientFormattedNit,
            String workpaperType,
            String title,
            String taxPeriod,
            String executiveSummary,
            BigDecimal detectedSavingsOrRiskCop,
            String payloadJson,
            String createdAt
    ) {}

    public record SaveWorkpaperRequest(
            Long clientId,
            String workpaperType,
            String title,
            String taxPeriod,
            String executiveSummary,
            BigDecimal detectedSavingsOrRiskCop,
            String payloadJson
    ) {}

    public record PortfolioDashboardResponse(
            int totalActiveClients,
            int totalObligationsCount,
            int overdueObligationsCount,
            int upcoming7DaysCount,
            int presentedObligationsCount,
            BigDecimal complianceRatePercent,
            BigDecimal totalEstimatedSanctionRiskCop,
            List<ClientSummaryDto> clients,
            List<ObligationRadarDto> obligations,
            List<WorkpaperDto> recentWorkpapers
    ) {}

    @Transactional(readOnly = true)
    public PortfolioDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();
        List<AccountingClientEntity> clients = portfolioPort.findAllClientsOrderByBusinessNameAsc();
        List<TaxObligationEntity> obligations = portfolioPort.findAllObligationsOrderByDueDateAsc();
        List<SavedWorkpaperEntity> workpapers = portfolioPort.findAllWorkpapersOrderByCreatedAtDesc();

        List<ClientSummaryDto> clientDtos = clients.stream().map(this::toClientDto).toList();

        int overdueCount = 0;
        int upcomingCount = 0;
        int presentedCount = 0;
        BigDecimal totalSanctionRisk = BigDecimal.ZERO;
        List<ObligationRadarDto> obligationDtos = new ArrayList<>();
        BigDecimal minSanction10Uvt = minimumSanction10UvtRounded(today.getYear());

        for (TaxObligationEntity ob : obligations) {
            long daysDiff = ChronoUnit.DAYS.between(today, ob.getDueDate());
            String effectiveStatus = ob.getStatus();
            if (!"PRESENTADA".equalsIgnoreCase(effectiveStatus) && daysDiff < 0) {
                effectiveStatus = "VENCIDA";
            }

            BigDecimal effectiveSanction = ob.getCalculatedSanctionCop() != null
                    ? ob.getCalculatedSanctionCop()
                    : BigDecimal.ZERO;

            String urgency;
            if ("PRESENTADA".equalsIgnoreCase(effectiveStatus)) {
                urgency = "AL_DIA";
                presentedCount++;
            } else if ("VENCIDA".equalsIgnoreCase(effectiveStatus)) {
                urgency = "CRITICA_MORA";
                overdueCount++;
                if (effectiveSanction.compareTo(BigDecimal.ZERO) == 0) {
                    effectiveSanction = minSanction10Uvt;
                }
                totalSanctionRisk = totalSanctionRisk.add(effectiveSanction);
            } else if (daysDiff <= 7) {
                urgency = "PROXIMA_7_DIAS";
                upcomingCount++;
            } else {
                urgency = "PROGRAMADA";
            }

            AccountingClientEntity c = ob.getClient();
            obligationDtos.add(new ObligationRadarDto(
                    ob.getId(),
                    c.getId(),
                    c.getBusinessName(),
                    c.getNit() + "-" + c.getDv(),
                    c.getLastNitDigit(),
                    c.getTaxRegime(),
                    ob.getDeclarationType(),
                    ob.getFormCode(),
                    ob.getTaxPeriod(),
                    ob.getDueDate(),
                    effectiveStatus,
                    daysDiff,
                    urgency,
                    ob.getEstimatedTaxCop(),
                    effectiveSanction
            ));
        }

        BigDecimal complianceRate = obligations.isEmpty()
                ? new BigDecimal("100.0")
                : BigDecimal.valueOf(presentedCount)
                        .multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(obligations.size()), 1, RoundingMode.HALF_UP);

        List<WorkpaperDto> workpaperDtos = workpapers.stream().map(this::toWorkpaperDto).toList();

        return new PortfolioDashboardResponse(
                clients.size(),
                obligations.size(),
                overdueCount,
                upcomingCount,
                presentedCount,
                complianceRate,
                totalSanctionRisk.setScale(0, RoundingMode.HALF_UP),
                clientDtos,
                obligationDtos,
                workpaperDtos
        );
    }

    @Transactional
    public ClientSummaryDto createClient(CreateClientRequest req) {
        if (req == null || req.nit() == null || req.nit().isBlank()) {
            throw new DomainValidationException("El NIT o documento del cliente es obligatorio.");
        }
        if (req.businessName() == null || req.businessName().isBlank()) {
            throw new DomainValidationException("La Razón Social o nombre del cliente es obligatorio.");
        }
        if (req.estimatedAnnualIncomeCop() != null && req.estimatedAnnualIncomeCop().compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainValidationException("El ingreso anual estimado del cliente no puede ser negativo.");
        }

        NitVO.NitVerificationReport nitReport = NitVO.inspectAndCalculate(req.nit());
        String cleanNit = nitReport.cleanBaseDigits();
        if (portfolioPort.findClientByCleanNit(cleanNit).isPresent()) {
            throw new DomainValidationException("Ya existe un cliente registrado en la cartera con el NIT " + cleanNit);
        }

        String dv = String.valueOf(nitReport.calculatedCheckDigit());
        int lastDigit = Character.getNumericValue(cleanNit.charAt(cleanNit.length() - 1));

        String businessName = req.businessName().trim();
        if (businessName.length() > 180) {
            businessName = businessName.substring(0, 180);
        }

        String personType = req.personType() != null && !req.personType().isBlank() ? req.personType().trim().toUpperCase() : "JURIDICA";
        String regime = req.taxRegime() != null && !req.taxRegime().isBlank() ? req.taxRegime().trim().toUpperCase() : "ORDINARIO";
        String ivaPeriod = req.ivaPeriodicity() != null && !req.ivaPeriodicity().isBlank() ? req.ivaPeriodicity().trim().toUpperCase() : "BIMESTRAL";
        String responsibilities = req.dianResponsibilities() != null && !req.dianResponsibilities().isBlank()
                ? req.dianResponsibilities().trim()
                : ("SIMPLE_RST".equals(regime) ? "47,48,52" : "05,07,14,48,52");
        String daneCode = req.municipalityDaneCode() != null && !req.municipalityDaneCode().isBlank() ? req.municipalityDaneCode().trim() : "11001";
        String muniName = req.municipalityName() != null && !req.municipalityName().isBlank() ? req.municipalityName().trim() : "BOGOTÁ, D.C.";
        BigDecimal annualIncome = req.estimatedAnnualIncomeCop() != null ? req.estimatedAnnualIncomeCop() : new BigDecimal("850000000");
        int employees = req.employeesCount() != null ? Math.max(0, req.employeesCount()) : 5;
        boolean art114 = req.appliesArt1141Exemption() != null ? req.appliesArt1141Exemption() : true;

        AccountingClientEntity saved = portfolioPort.saveClient(new AccountingClientEntity(
                cleanNit,
                dv,
                lastDigit,
                businessName,
                personType,
                regime,
                ivaPeriod,
                responsibilities,
                daneCode,
                muniName,
                annualIncome,
                employees,
                art114,
                "ACTIVO"
        ));

        generateAutomaticObligationsForClient(saved);
        return toClientDto(saved);
    }

    @Transactional
    public void generateAutomaticObligationsForClient(AccountingClientEntity client) {
        LocalDate today = LocalDate.now();
        int dayOffset = Math.min(22, 10 + client.getLastNitDigit());
        LocalDate rawNextDue = today.withDayOfMonth(Math.min(dayOffset, today.lengthOfMonth()));
        if (!rawNextDue.isAfter(today)) {
            rawNextDue = rawNextDue.plusMonths(1);
        }
        LocalDate nextDue = adjustToNextBusinessDay(rawNextDue);

        BigDecimal monthlyReteEstimate = MoneyCOP.roundToThousandArt868(
                client.getEstimatedAnnualIncomeCop().divide(new BigDecimal("120"), 0, RoundingMode.HALF_UP)
        );

        portfolioPort.saveObligation(new TaxObligationEntity(
                client,
                "RETEFUENTE_350",
                "350",
                nextDue.getYear() + "-M" + String.format("%02d", nextDue.getMonthValue()),
                nextDue,
                "PENDIENTE",
                monthlyReteEstimate,
                BigDecimal.ZERO
        ));

        if ("SIMPLE_RST".equals(client.getTaxRegime())) {
            portfolioPort.saveObligation(new TaxObligationEntity(
                    client,
                    "ANTICIPO_RST_2593",
                    "2593",
                    nextDue.getYear() + "-B03",
                    adjustToNextBusinessDay(nextDue.plusDays(4)),
                    "PENDIENTE",
                    monthlyReteEstimate.multiply(new BigDecimal("2")),
                    BigDecimal.ZERO
            ));
        } else if ("PERSONA_NATURAL".equals(client.getTaxRegime())) {
            portfolioPort.saveObligation(new TaxObligationEntity(
                    client,
                    "RENTA_PN_210",
                    "210",
                    (today.getYear() - 1) + "-ANUAL",
                    adjustToNextBusinessDay(nextDue.plusDays(12)),
                    "PENDIENTE",
                    monthlyReteEstimate.multiply(new BigDecimal("3")),
                    BigDecimal.ZERO
            ));
        } else {
            portfolioPort.saveObligation(new TaxObligationEntity(
                    client,
                    "IVA_300",
                    "300",
                    nextDue.getYear() + "-" + client.getIvaPeriodicity(),
                    adjustToNextBusinessDay(nextDue.plusDays(3)),
                    "PENDIENTE",
                    monthlyReteEstimate.multiply(new BigDecimal("4")),
                    BigDecimal.ZERO
            ));
        }
    }

    @Transactional
    public ObligationRadarDto updateObligationStatus(Long obligationId, String newStatus) {
        if (obligationId == null) {
            throw new DomainValidationException("El ID de la obligación tributaria es obligatorio.");
        }
        if (newStatus == null || newStatus.isBlank()) {
            throw new DomainValidationException("El nuevo estado de la obligación es requerido.");
        }
        String normalized = newStatus.trim().toUpperCase();
        if (!VALID_OBLIGATION_STATUSES.contains(normalized)) {
            throw new DomainValidationException(
                    "Estado de obligación inválido: '" + normalized + "'. Valores permitidos: " + VALID_OBLIGATION_STATUSES
            );
        }

        TaxObligationEntity ob = portfolioPort.findObligationById(obligationId)
                .orElseThrow(() -> new DomainValidationException("No existe la obligación tributaria con ID " + obligationId));
        ob.setStatus(normalized);
        if ("PRESENTADA".equals(normalized)) {
            ob.setCalculatedSanctionCop(BigDecimal.ZERO);
        } else if ("VENCIDA".equals(normalized) && ob.getCalculatedSanctionCop().compareTo(BigDecimal.ZERO) == 0) {
            ob.setCalculatedSanctionCop(minimumSanction10UvtRounded(LocalDate.now().getYear()));
        }
        TaxObligationEntity saved = portfolioPort.saveObligation(ob);
        long daysDiff = ChronoUnit.DAYS.between(LocalDate.now(), saved.getDueDate());
        String urgency = "PRESENTADA".equals(saved.getStatus())
                ? "AL_DIA"
                : (daysDiff < 0 ? "CRITICA_MORA" : (daysDiff <= 7 ? "PROXIMA_7_DIAS" : "PROGRAMADA"));
        AccountingClientEntity c = saved.getClient();
        return new ObligationRadarDto(
                saved.getId(),
                c.getId(),
                c.getBusinessName(),
                c.getNit() + "-" + c.getDv(),
                c.getLastNitDigit(),
                c.getTaxRegime(),
                saved.getDeclarationType(),
                saved.getFormCode(),
                saved.getTaxPeriod(),
                saved.getDueDate(),
                saved.getStatus(),
                daysDiff,
                urgency,
                saved.getEstimatedTaxCop(),
                saved.getCalculatedSanctionCop()
        );
    }

    @Transactional
    public WorkpaperDto saveWorkpaper(SaveWorkpaperRequest req) {
        if (req == null || req.title() == null || req.title().isBlank()) {
            throw new DomainValidationException("El título del papel de trabajo es obligatorio.");
        }
        AccountingClientEntity client = null;
        if (req.clientId() != null) {
            client = portfolioPort.findClientById(req.clientId())
                    .orElseThrow(() -> new DomainValidationException(
                            "No existe el cliente contable con ID " + req.clientId() + " para asociar el papel de trabajo."
                    ));
        } else {
            client = portfolioPort.findAllClientsOrderByBusinessNameAsc().stream().findFirst().orElse(null);
        }

        String title = req.title().trim();
        if (title.length() > 180) {
            title = title.substring(0, 180);
        }
        String summary = (req.executiveSummary() != null && !req.executiveSummary().isBlank())
                ? req.executiveSummary().trim()
                : "Papel de trabajo generado en ALcontable";
        if (summary.length() > 700) {
            summary = summary.substring(0, 700);
        }

        SavedWorkpaperEntity entity = new SavedWorkpaperEntity(
                client,
                req.workpaperType() != null && !req.workpaperType().isBlank() ? req.workpaperType().trim() : "GENERAL",
                title,
                req.taxPeriod() != null && !req.taxPeriod().isBlank() ? req.taxPeriod().trim() : "2026",
                summary,
                req.detectedSavingsOrRiskCop() != null ? req.detectedSavingsOrRiskCop() : BigDecimal.ZERO,
                req.payloadJson() != null && !req.payloadJson().isBlank() ? req.payloadJson().trim() : "{}"
        );
        return toWorkpaperDto(portfolioPort.saveWorkpaper(entity));
    }

    @Transactional(readOnly = true)
    public List<WorkpaperDto> listWorkpapers(Long clientId) {
        if (clientId != null && !portfolioPort.existsClientById(clientId)) {
            throw new DomainValidationException("No existe el cliente contable con ID " + clientId + ".");
        }
        List<SavedWorkpaperEntity> list = clientId != null
                ? portfolioPort.findWorkpapersByClientIdOrderByCreatedAtDesc(clientId)
                : portfolioPort.findAllWorkpapersOrderByCreatedAtDesc();
        return list.stream().map(this::toWorkpaperDto).toList();
    }

    private LocalDate adjustToNextBusinessDay(LocalDate date) {
        LocalDate cursor = date;
        while (cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                || cursor.getDayOfWeek() == DayOfWeek.SUNDAY
                || holidayEngine.isHoliday(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    private BigDecimal minimumSanction10UvtRounded(int year) {
        BigDecimal uvt = LaborLegalConstants.forYear(year).uvtCOP();
        return MoneyCOP.roundToThousandArt868(uvt.multiply(BigDecimal.TEN));
    }

    private ClientSummaryDto toClientDto(AccountingClientEntity c) {
        return new ClientSummaryDto(
                c.getId(),
                c.getNit(),
                c.getDv(),
                c.getNit() + "-" + c.getDv(),
                c.getLastNitDigit(),
                c.getBusinessName(),
                c.getPersonType(),
                c.getTaxRegime(),
                c.getIvaPeriodicity(),
                c.getDianResponsibilities(),
                c.getMunicipalityDaneCode(),
                c.getMunicipalityName(),
                c.getEstimatedAnnualIncomeCop(),
                c.getEmployeesCount(),
                c.getAppliesArt1141Exemption(),
                c.getStatus()
        );
    }

    private WorkpaperDto toWorkpaperDto(SavedWorkpaperEntity w) {
        AccountingClientEntity c = w.getClient();
        return new WorkpaperDto(
                w.getId(),
                c != null ? c.getId() : null,
                c != null ? c.getBusinessName() : "Estudio Contable General",
                c != null ? (c.getNit() + "-" + c.getDv()) : "N/A",
                w.getWorkpaperType(),
                w.getTitle(),
                w.getTaxPeriod(),
                w.getExecutiveSummary(),
                w.getDetectedSavingsOrRiskCop(),
                w.getPayloadJson(),
                w.getCreatedAt() != null ? w.getCreatedAt().toString() : LocalDate.now().toString()
        );
    }
}
