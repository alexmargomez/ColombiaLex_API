package com.alexmar.colombialex.geo.infrastructure.persistence;

import com.alexmar.colombialex.geo.domain.model.MunicipalityEntity;
import com.alexmar.colombialex.geo.domain.ports.MunicipalityRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Driven Adapter (Hexagonal Architecture) —
 * Implementa el puerto de dominio MunicipalityRepositoryPort delegando a Spring Data JPA.
 */
@Component
public class JpaMunicipalityRepositoryAdapter implements MunicipalityRepositoryPort {

    private final MunicipalityJpaRepository jpaRepository;

    public JpaMunicipalityRepositoryAdapter(MunicipalityJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<MunicipalityEntity> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode) {
        return jpaRepository.findByDepartmentCodeOrderByMunicipalityNameAsc(departmentCode);
    }

    @Override
    public Optional<MunicipalityEntity> findByDaneCode(String daneCode) {
        return jpaRepository.findByDaneCode(daneCode);
    }

    @Override
    public List<MunicipalityEntity> searchByDepartmentAndTerm(String departmentCode, String term) {
        return jpaRepository.searchByDepartmentAndTerm(
                departmentCode != null ? departmentCode.trim() : "",
                term != null ? term.trim() : ""
        );
    }
}
