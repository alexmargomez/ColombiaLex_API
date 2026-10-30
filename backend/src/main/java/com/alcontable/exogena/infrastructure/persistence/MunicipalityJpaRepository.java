package com.alcontable.exogena.infrastructure.persistence;

import com.alcontable.exogena.domain.model.MunicipalityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MunicipalityJpaRepository extends JpaRepository<MunicipalityEntity, Long> {
    Optional<MunicipalityEntity> findByDaneCode(String daneCode);
    List<MunicipalityEntity> findByDepartmentCodeOrderByMunicipalityNameAsc(String departmentCode);

    @Query("""
            SELECT m FROM MunicipalityEntity m
            WHERE (:dept IS NULL OR :dept = '' OR m.departmentCode = :dept)
              AND (
                :term IS NULL OR :term = ''
                OR LOWER(m.daneCode) LIKE CONCAT('%', LOWER(:term), '%')
                OR LOWER(m.municipalityName) LIKE CONCAT('%', LOWER(:term), '%')
                OR LOWER(m.departmentName) LIKE CONCAT('%', LOWER(:term), '%')
                OR LOWER(m.region) LIKE CONCAT('%', LOWER(:term), '%')
              )
            ORDER BY m.departmentName ASC, m.municipalityName ASC
            """)
    List<MunicipalityEntity> searchByDepartmentAndTerm(
            @Param("dept") String departmentCode,
            @Param("term") String term
    );
}
