package com.alcontable.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountantUserJpaRepository extends JpaRepository<AccountantUserEntity, Long> {
    Optional<AccountantUserEntity> findByEmailIgnoreCase(String email);
    Optional<AccountantUserEntity> findBySessionToken(String sessionToken);
}
