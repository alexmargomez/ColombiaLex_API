package com.alexmar.colombialex.geo.domain.ports;

import com.alexmar.colombialex.geo.domain.model.MunicipalityEntity;

import java.util.List;
import java.util.Optional;

/**
 * Outbound Port (Hexagonal Architecture) —
 * Desacopla los casos de uso geográficos (DIVIPOLA) de la infraestructura Spring Data JPA.
 */
public interface MunicipalityRepositoryPort {
    List<MunicipalityEntity> findAll();
    List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode);
    Optional<MunicipalityEntity> findByDaneCode(String daneCode);
}
