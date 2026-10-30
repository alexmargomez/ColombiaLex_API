package com.alcontable.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountingClientJpaRepository extends JpaRepository<AccountingClientEntity, Long> {
    Optional<AccountingClientEntity> findByNit(String nit);
    List<AccountingClientEntity> findAllByOrderByBusinessNameAsc();
}
