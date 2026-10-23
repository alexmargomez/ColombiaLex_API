package com.alexmar.colombialex.geo.infrastructure.persistence;

import com.alexmar.colombialex.geo.domain.model.MunicipalityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MunicipalityJpaRepository extends JpaRepository<MunicipalityEntity, Long> {
    Optional<MunicipalityEntity> findByDaneCode(String daneCode);
    List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode);
}
