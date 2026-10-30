package com.alcontable.portfolio.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "accounting_workpapers",
        indexes = {
                @Index(name = "idx_wp_client", columnList = "client_id"),
                @Index(name = "idx_wp_type", columnList = "workpaper_type"),
                @Index(name = "idx_wp_created_at", columnList = "created_at")
        }
)
public class SavedWorkpaperEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    private AccountingClientEntity client;

    @Column(name = "workpaper_type", nullable = false, length = 45)
    private String workpaperType; // NOMINA_UGPP_PUC, EXOGENA_PREAUDITORIA, PLANEACION_LEY2277_RST, LIQUIDACION_SANCIONES_DIAN

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "tax_period", nullable = false, length = 35)
    private String taxPeriod;

    @Column(name = "executive_summary", nullable = false, length = 700)
    private String executiveSummary;

    @Column(name = "detected_savings_or_risk_cop", nullable = false, precision = 19, scale = 2)
    private BigDecimal detectedSavingsOrRiskCop;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SavedWorkpaperEntity() {
    }

    public SavedWorkpaperEntity(
            AccountingClientEntity client,
            String workpaperType,
            String title,
            String taxPeriod,
            String executiveSummary,
            BigDecimal detectedSavingsOrRiskCop,
            String payloadJson
    ) {
        this.client = client;
        this.workpaperType = workpaperType;
        this.title = title;
        this.taxPeriod = taxPeriod;
        this.executiveSummary = executiveSummary;
        this.detectedSavingsOrRiskCop = detectedSavingsOrRiskCop;
        this.payloadJson = payloadJson != null && !payloadJson.isBlank() ? payloadJson : "{}";
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.payloadJson == null || this.payloadJson.isBlank()) {
            this.payloadJson = "{}";
        }
    }

    public Long getId() {
        return id;
    }

    public AccountingClientEntity getClient() {
        return client;
    }

    public String getWorkpaperType() {
        return workpaperType;
    }

    public String getTitle() {
        return title;
    }

    public String getTaxPeriod() {
        return taxPeriod;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public BigDecimal getDetectedSavingsOrRiskCop() {
        return detectedSavingsOrRiskCop;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
