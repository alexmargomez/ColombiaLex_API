package com.alcontable.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaxObligationJpaRepository extends JpaRepository<TaxObligationEntity, Long> {
    @EntityGraph(attributePaths = "client")
    List<TaxObligationEntity> findAllByOrderByDueDateAsc();

    @EntityGraph(attributePaths = "client")
    List<TaxObligationEntity> findByClient_AccountantUserIdOrderByDueDateAsc(Long accountantUserId);

    @EntityGraph(attributePaths = "client")
    List<TaxObligationEntity> findByClient_IdOrderByDueDateAsc(Long clientId);
}
