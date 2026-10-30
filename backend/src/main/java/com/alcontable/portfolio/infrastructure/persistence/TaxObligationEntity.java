package com.alcontable.portfolio.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "client_tax_obligations",
        indexes = {
                @Index(name = "idx_tax_ob_client", columnList = "client_id"),
                @Index(name = "idx_tax_ob_due_date", columnList = "due_date"),
                @Index(name = "idx_tax_ob_status", columnList = "status")
        }
)
public class TaxObligationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private AccountingClientEntity client;

    @Column(name = "declaration_type", nullable = false, length = 40)
    private String declarationType; // RENTA_PJ_110, RENTA_PN_210, IVA_300, RETEFUENTE_350, EXOGENA_1001, ANTICIPO_RST_2593

    @Column(name = "form_code", nullable = false, length = 15)
    private String formCode; // 110, 210, 300, 350, 1001, 2593

    @Column(name = "tax_period", nullable = false, length = 35)
    private String taxPeriod;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "status", nullable = false, length = 25)
    private String status; // PENDIENTE, EN_PREPARACION, PRESENTADA, VENCIDA

    @Column(name = "estimated_tax_cop", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedTaxCop;

    @Column(name = "calculated_sanction_cop", nullable = false, precision = 19, scale = 2)
    private BigDecimal calculatedSanctionCop;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TaxObligationEntity() {
    }

    public TaxObligationEntity(
            AccountingClientEntity client,
            String declarationType,
            String formCode,
            String taxPeriod,
            LocalDate dueDate,
            String status,
            BigDecimal estimatedTaxCop,
            BigDecimal calculatedSanctionCop
    ) {
        this.client = client;
        this.declarationType = declarationType;
        this.formCode = formCode;
        this.taxPeriod = taxPeriod;
        this.dueDate = dueDate;
        this.status = status;
        this.estimatedTaxCop = estimatedTaxCop;
        this.calculatedSanctionCop = calculatedSanctionCop;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AccountingClientEntity getClient() {
        return client;
    }

    public void setClient(AccountingClientEntity client) {
        this.client = client;
    }

    public String getDeclarationType() {
        return declarationType;
    }

    public String getFormCode() {
        return formCode;
    }

    public String getTaxPeriod() {
        return taxPeriod;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getEstimatedTaxCop() {
        return estimatedTaxCop;
    }

    public void setEstimatedTaxCop(BigDecimal estimatedTaxCop) {
        this.estimatedTaxCop = estimatedTaxCop;
    }

    public BigDecimal getCalculatedSanctionCop() {
        return calculatedSanctionCop;
    }

    public void setCalculatedSanctionCop(BigDecimal calculatedSanctionCop) {
        this.calculatedSanctionCop = calculatedSanctionCop;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
