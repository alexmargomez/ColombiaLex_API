package com.alexmar.colombialex.geo.domain.ports;

import com.alexmar.colombialex.geo.domain.model.MunicipalityEntity;

import java.util.Comparator;
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

    default List<MunicipalityEntity> searchByDepartmentAndTerm(String departmentCode, String term) {
        List<MunicipalityEntity> base = (departmentCode != null && !departmentCode.isBlank())
                ? findByDepartmentCodeOrderByMunicipalityNameAsc(departmentCode.trim())
                : findAll();
        if (term == null || term.isBlank()) {
            return base;
        }
        String lower = term.trim().toLowerCase();
        return base.stream()
                .filter(m -> m.getDaneCode().toLowerCase().contains(lower)
                        || m.getMunicipalityName().toLowerCase().contains(lower)
                        || m.getDepartmentName().toLowerCase().contains(lower)
                        || m.getRegion().toLowerCase().contains(lower))
                .sorted(Comparator.comparing(MunicipalityEntity::getMunicipalityName))
                .toList();
    }
}
