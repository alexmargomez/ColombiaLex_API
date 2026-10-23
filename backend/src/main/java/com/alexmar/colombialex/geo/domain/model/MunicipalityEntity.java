package com.alexmar.colombialex.geo.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "divipola_municipalities", indexes = {
        @Index(name = "idx_dane_code", columnList = "daneCode", unique = true),
        @Index(name = "idx_dept_code", columnList = "departmentCode")
})
public class MunicipalityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5, unique = true)
    private String daneCode;

    @Column(nullable = false, length = 2)
    private String departmentCode;

    @Column(nullable = false, length = 100)
    private String departmentName;

    @Column(nullable = false, length = 120)
    private String municipalityName;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 60)
    private String region;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Boolean isCapital;

    public MunicipalityEntity() {}

    public MunicipalityEntity(
            String daneCode,
            String departmentCode,
            String departmentName,
            String municipalityName,
            String category,
            String region,
            Double latitude,
            Double longitude,
            Boolean isCapital
    ) {
        this.daneCode = daneCode;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.municipalityName = municipalityName;
        this.category = category;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isCapital = isCapital;
    }

    public Long getId() { return id; }
    public String getDaneCode() { return daneCode; }
    public String getDepartmentCode() { return departmentCode; }
    public String getDepartmentName() { return departmentName; }
    public String getMunicipalityName() { return municipalityName; }
    public String getCategory() { return category; }
    public String getRegion() { return region; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Boolean getIsCapital() { return isCapital; }
}
