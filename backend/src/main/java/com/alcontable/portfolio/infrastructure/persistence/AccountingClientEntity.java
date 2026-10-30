package com.alcontable.portfolio.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "accounting_clients",
        indexes = {
                @Index(name = "idx_acc_client_nit", columnList = "nit", unique = true),
                @Index(name = "idx_acc_client_regime", columnList = "tax_regime"),
                @Index(name = "idx_acc_client_last_digit", columnList = "last_nit_digit")
        }
)
public class AccountingClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nit", nullable = false, length = 20, unique = true)
    private String nit;

    @Column(name = "dv", nullable = false, length = 2)
    private String dv;

    @Column(name = "last_nit_digit", nullable = false)
    private Integer lastNitDigit;

    @Column(name = "business_name", nullable = false, length = 180)
    private String businessName;

    @Column(name = "person_type", nullable = false, length = 25)
    private String personType; // JURIDICA, NATURAL

    @Column(name = "tax_regime", nullable = false, length = 30)
    private String taxRegime; // ORDINARIO, SIMPLE_RST, PERSONA_NATURAL

    @Column(name = "iva_periodicity", nullable = false, length = 25)
    private String ivaPeriodicity; // BIMESTRAL, CUATRIMESTRAL, NO_RESPONSABLE

    @Column(name = "dian_responsibilities", nullable = false, length = 250)
    private String dianResponsibilities; // Ej: "05,07,14,48,52"

    @Column(name = "municipality_dane_code", nullable = false, length = 10)
    private String municipalityDaneCode;

    @Column(name = "municipality_name", nullable = false, length = 100)
    private String municipalityName;

    @Column(name = "estimated_annual_income_cop", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedAnnualIncomeCop;

    @Column(name = "employees_count", nullable = false)
    private Integer employeesCount;

    @Column(name = "applies_art_114_1_exemption", nullable = false)
    private Boolean appliesArt1141Exemption;

    @Column(name = "status", nullable = false, length = 25)
    private String status; // ACTIVO, EN_AUDITORIA, INACTIVO

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AccountingClientEntity() {
    }

    public AccountingClientEntity(
            String nit,
            String dv,
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
    ) {
        this.nit = nit;
        this.dv = dv;
        this.lastNitDigit = lastNitDigit;
        this.businessName = businessName;
        this.personType = personType;
        this.taxRegime = taxRegime;
        this.ivaPeriodicity = ivaPeriodicity;
        this.dianResponsibilities = dianResponsibilities;
        this.municipalityDaneCode = municipalityDaneCode;
        this.municipalityName = municipalityName;
        this.estimatedAnnualIncomeCop = estimatedAnnualIncomeCop;
        this.employeesCount = employeesCount;
        this.appliesArt1141Exemption = appliesArt1141Exemption;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNit() {
        return nit;
    }

    public void setNit(String nit) {
        this.nit = nit;
    }

    public String getDv() {
        return dv;
    }

    public void setDv(String dv) {
        this.dv = dv;
    }

    public Integer getLastNitDigit() {
        return lastNitDigit;
    }

    public void setLastNitDigit(Integer lastNitDigit) {
        this.lastNitDigit = lastNitDigit;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getPersonType() {
        return personType;
    }

    public void setPersonType(String personType) {
        this.personType = personType;
    }

    public String getTaxRegime() {
        return taxRegime;
    }

    public void setTaxRegime(String taxRegime) {
        this.taxRegime = taxRegime;
    }

    public String getIvaPeriodicity() {
        return ivaPeriodicity;
    }

    public void setIvaPeriodicity(String ivaPeriodicity) {
        this.ivaPeriodicity = ivaPeriodicity;
    }

    public String getDianResponsibilities() {
        return dianResponsibilities;
    }

    public void setDianResponsibilities(String dianResponsibilities) {
        this.dianResponsibilities = dianResponsibilities;
    }

    public String getMunicipalityDaneCode() {
        return municipalityDaneCode;
    }

    public void setMunicipalityDaneCode(String municipalityDaneCode) {
        this.municipalityDaneCode = municipalityDaneCode;
    }

    public String getMunicipalityName() {
        return municipalityName;
    }

    public void setMunicipalityName(String municipalityName) {
        this.municipalityName = municipalityName;
    }

    public BigDecimal getEstimatedAnnualIncomeCop() {
        return estimatedAnnualIncomeCop;
    }

    public void setEstimatedAnnualIncomeCop(BigDecimal estimatedAnnualIncomeCop) {
        this.estimatedAnnualIncomeCop = estimatedAnnualIncomeCop;
    }

    public Integer getEmployeesCount() {
        return employeesCount;
    }

    public void setEmployeesCount(Integer employeesCount) {
        this.employeesCount = employeesCount;
    }

    public Boolean getAppliesArt1141Exemption() {
        return appliesArt1141Exemption;
    }

    public void setAppliesArt1141Exemption(Boolean appliesArt1141Exemption) {
        this.appliesArt1141Exemption = appliesArt1141Exemption;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
