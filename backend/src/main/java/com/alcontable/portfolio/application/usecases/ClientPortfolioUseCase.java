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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ClientPortfolioUseCase {

    public static final String DEMO_ACCOUNTANT_EMAIL = "contador@alcontable.co";

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

    public record RegisterAccountantRequest(
            String email,
            String fullName,
            String professionalCardNumber,
            String firmName,
            String password
    ) {}

    public record LoginAccountantRequest(
            String email,
            String password
    ) {}

    public record AccountantAuthResponse(
            Long id,
            String email,
            String fullName,
            String professionalCardNumber,
            String firmName,
            String role,
            String authToken
    ) {}

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

    @Transactional
    public AccountantAuthResponse registerAccountant(RegisterAccountantRequest req) {
        if (req == null || req.email() == null || req.email().isBlank() || !req.email().contains("@")) {
            throw new DomainValidationException("Debe ingresar un correo electrónico válido para registrar su cuenta de Contador.");
        }
        if (req.fullName() == null || req.fullName().isBlank()) {
            throw new DomainValidationException("El nombre completo del Contador o Revisor Fiscal es obligatorio.");
        }
        if (req.password() == null || req.password().trim().length() < 6) {
            throw new DomainValidationException("La contraseña debe tener al menos 6 caracteres.");
        }
        String normalizedEmail = req.email().trim().toLowerCase();
        if (portfolioPort.findUserByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new DomainValidationException("Ya existe un contador registrado con el correo " + normalizedEmail);
        }

        String card = req.professionalCardNumber() != null && !req.professionalCardNumber().isBlank()
                ? req.professionalCardNumber().trim().toUpperCase()
                : "TP-PENDIENTE";
        String firm = req.firmName() != null && !req.firmName().isBlank()
                ? req.firmName().trim()
                : "Consultorio Contable Independiente";

        String token = generateSessionToken();
        AccountantUserEntity saved = portfolioPort.saveUser(new AccountantUserEntity(
                normalizedEmail,
                req.fullName().trim(),
                card,
                firm,
                hashPassword(req.password().trim()),
                "CONTADOR_TITULAR",
                token
        ));
        return toAuthResponse(saved);
    }

    @Transactional
    public AccountantAuthResponse loginAccountant(LoginAccountantRequest req) {
        if (req == null || req.email() == null || req.email().isBlank() || req.password() == null || req.password().isBlank()) {
            throw new DomainValidationException("Debe ingresar su correo electrónico y contraseña.");
        }
        String normalizedEmail = req.email().trim().toLowerCase();
        AccountantUserEntity user = portfolioPort.findUserByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new DomainValidationException("Credenciales inválidas. Verifique su correo y contraseña."));

        String expectedHash = hashPassword(req.password().trim());
        if (!expectedHash.equals(user.getPasswordHash())) {
            throw new DomainValidationException("Credenciales inválidas. Verifique su correo y contraseña.");
        }
        if (user.getAuthToken() == null || user.getAuthToken().isBlank()) {
            user.setAuthToken(generateSessionToken());
            user = portfolioPort.saveUser(user);
        }
        return toAuthResponse(user);
    }

    @Transactional
    public AccountantAuthResponse loginDemoAccountant() {
        AccountantUserEntity demoUser = portfolioPort.findUserByEmailIgnoreCase(DEMO_ACCOUNTANT_EMAIL)
                .orElseGet(() -> portfolioPort.saveUser(new AccountantUserEntity(
                        DEMO_ACCOUNTANT_EMAIL,
                        "Dra. Carolina Méndez, CPA",
                        "TP-194820-T JCC",
                        "Méndez & Asociados Consultores Tributarios S.A.S.",
                        hashPassword("Demo2026*"),
                        "SOCIA_DIRECTORA",
                        generateSessionToken()
                )));
        if (demoUser.getAuthToken() == null || demoUser.getAuthToken().isBlank()) {
            demoUser.setAuthToken(generateSessionToken());
            demoUser = portfolioPort.saveUser(demoUser);
        }
        return toAuthResponse(demoUser);
    }

    @Transactional(readOnly = true)
    public AccountantAuthResponse getAccountantProfileByToken(String rawToken) {
        String cleanToken = extractBearerToken(rawToken);
        if (cleanToken == null) {
            throw new DomainValidationException("No se proporcionó token de sesión activa.");
        }
        AccountantUserEntity user = portfolioPort.findUserByAuthToken(cleanToken)
                .orElseThrow(() -> new DomainValidationException("Sesión de contador expirada o inválida."));
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public Long resolveAccountantUserIdFromToken(String rawToken) {
        String cleanToken = extractBearerToken(rawToken);
        if (cleanToken != null) {
            return portfolioPort.findUserByAuthToken(cleanToken)
                    .map(AccountantUserEntity::getId)
                    .orElseThrow(() -> new DomainValidationException("Sesión de contador inválida o expirada. Inicie sesión nuevamente."));
        }
        return portfolioPort.findUserByEmailIgnoreCase(DEMO_ACCOUNTANT_EMAIL)
                .map(AccountantUserEntity::getId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PortfolioDashboardResponse getDashboard() {
        return getDashboard(null);
    }

    @Transactional(readOnly = true)
    public PortfolioDashboardResponse getDashboard(Long accountantUserId) {
        LocalDate today = LocalDate.now();
        List<AccountingClientEntity> clients = accountantUserId != null
                ? portfolioPort.findClientsByAccountantUserIdOrderByBusinessNameAsc(accountantUserId)
                : portfolioPort.findAllClientsOrderByBusinessNameAsc();
        List<TaxObligationEntity> obligations = accountantUserId != null
                ? portfolioPort.findObligationsByAccountantUserIdOrderByDueDateAsc(accountantUserId)
                : portfolioPort.findAllObligationsOrderByDueDateAsc();
        List<SavedWorkpaperEntity> workpapers = accountantUserId != null
                ? portfolioPort.findWorkpapersByAccountantUserIdOrderByCreatedAtDesc(accountantUserId)
                : portfolioPort.findAllWorkpapersOrderByCreatedAtDesc();

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
        return createClient(req, null);
    }

    @Transactional
    public ClientSummaryDto createClient(CreateClientRequest req, Long accountantUserId) {
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
        boolean existsInPortfolio = accountantUserId != null
                ? portfolioPort.findClientByCleanNitAndAccountantUserId(cleanNit, accountantUserId).isPresent()
                : portfolioPort.findClientByCleanNit(cleanNit).isPresent();
        if (existsInPortfolio) {
            throw new DomainValidationException("Ya existe un cliente registrado en su cartera privada con el NIT " + cleanNit);
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
                accountantUserId,
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
        return updateObligationStatus(obligationId, newStatus, null);
    }

    @Transactional
    public ObligationRadarDto updateObligationStatus(Long obligationId, String newStatus, Long accountantUserId) {
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
        if (accountantUserId != null && ob.getClient() != null && ob.getClient().getAccountantUserId() != null
                && !ob.getClient().getAccountantUserId().equals(accountantUserId)) {
            throw new DomainValidationException("Acceso denegado: La obligación tributaria pertenece a la cartera privada de otro contador.");
        }
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
        return saveWorkpaper(req, null);
    }

    @Transactional
    public WorkpaperDto saveWorkpaper(SaveWorkpaperRequest req, Long accountantUserId) {
        if (req == null || req.title() == null || req.title().isBlank()) {
            throw new DomainValidationException("El título del papel de trabajo es obligatorio.");
        }
        AccountingClientEntity client = null;
        if (req.clientId() != null) {
            client = portfolioPort.findClientById(req.clientId())
                    .orElseThrow(() -> new DomainValidationException(
                            "No existe el cliente contable con ID " + req.clientId() + " para asociar el papel de trabajo."
                    ));
            if (accountantUserId != null && client.getAccountantUserId() != null
                    && !client.getAccountantUserId().equals(accountantUserId)) {
                throw new DomainValidationException(
                        "Acceso denegado: No puede guardar papeles de trabajo en un cliente perteneciente a otro contador."
                );
            }
        } else {
            List<AccountingClientEntity> availableClients = accountantUserId != null
                    ? portfolioPort.findClientsByAccountantUserIdOrderByBusinessNameAsc(accountantUserId)
                    : portfolioPort.findAllClientsOrderByBusinessNameAsc();
            client = availableClients.stream().findFirst().orElse(null);
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

        Long effectiveOwnerId = accountantUserId != null
                ? accountantUserId
                : (client != null ? client.getAccountantUserId() : null);

        SavedWorkpaperEntity entity = new SavedWorkpaperEntity(
                effectiveOwnerId,
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
        return listWorkpapers(clientId, null);
    }

    @Transactional(readOnly = true)
    public List<WorkpaperDto> listWorkpapers(Long clientId, Long accountantUserId) {
        if (clientId != null) {
            AccountingClientEntity client = portfolioPort.findClientById(clientId)
                    .orElseThrow(() -> new DomainValidationException("No existe el cliente contable con ID " + clientId + "."));
            if (accountantUserId != null && client.getAccountantUserId() != null
                    && !client.getAccountantUserId().equals(accountantUserId)) {
                throw new DomainValidationException("Acceso denegado: El cliente consultado pertenece a la cartera privada de otro contador.");
            }
        }
        List<SavedWorkpaperEntity> list;
        if (clientId != null) {
            list = portfolioPort.findWorkpapersByClientIdOrderByCreatedAtDesc(clientId);
        } else if (accountantUserId != null) {
            list = portfolioPort.findWorkpapersByAccountantUserIdOrderByCreatedAtDesc(accountantUserId);
        } else {
            list = portfolioPort.findAllWorkpapersOrderByCreatedAtDesc();
        }
        return list.stream().map(this::toWorkpaperDto).toList();
    }

    public static String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("alcontable-salt-2026:" + rawPassword).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private String generateSessionToken() {
        return "alc_" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String extractBearerToken(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return null;
        }
        String trimmed = rawHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private AccountantAuthResponse toAuthResponse(AccountantUserEntity user) {
        return new AccountantAuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfessionalCardNumber(),
                user.getFirmName(),
                user.getRole(),
                user.getAuthToken()
        );
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
