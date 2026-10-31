package com.alcontable.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountingClientJpaRepository extends JpaRepository<AccountingClientEntity, Long> {
    Optional<AccountingClientEntity> findFirstByNit(String nit);
    Optional<AccountingClientEntity> findFirstByAccountantUserIdAndNit(Long accountantUserId, String nit);
    List<AccountingClientEntity> findAllByOrderByBusinessNameAsc();
    List<AccountingClientEntity> findByAccountantUserIdOrderByBusinessNameAsc(Long accountantUserId);
}
