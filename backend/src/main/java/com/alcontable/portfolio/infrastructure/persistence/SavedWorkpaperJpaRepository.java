package com.alcontable.portfolio.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SavedWorkpaperJpaRepository extends JpaRepository<SavedWorkpaperEntity, Long> {
    @EntityGraph(attributePaths = "client")
    List<SavedWorkpaperEntity> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "client")
    List<SavedWorkpaperEntity> findByClient_IdOrderByCreatedAtDesc(Long clientId);
}
