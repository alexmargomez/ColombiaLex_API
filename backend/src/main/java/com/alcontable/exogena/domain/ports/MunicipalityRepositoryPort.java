package com.alcontable.exogena.domain.ports;

import com.alcontable.exogena.domain.model.MunicipalityEntity;

import java.util.List;
import java.util.Optional;

/**
 * Outbound Port (Hexagonal Architecture) —
 * Desacopla los casos de uso geográficos (DIVIPOLA) de la infraestructura Spring Data JPA.
 * Sin implementaciones por defecto en memoria que contaminen el puerto de dominio.
 */
public interface MunicipalityRepositoryPort {
    List<MunicipalityEntity> findAll();
    List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode);
    Optional<MunicipalityEntity> findByDaneCode(String daneCode);
    List<MunicipalityEntity> searchByDepartmentAndTerm(String departmentCode, String term);
}
