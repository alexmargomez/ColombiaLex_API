package com.alcontable.portfolio.domain.ports;

import com.alcontable.portfolio.infrastructure.persistence.AccountingClientEntity;
import com.alcontable.portfolio.infrastructure.persistence.SavedWorkpaperEntity;
import com.alcontable.portfolio.infrastructure.persistence.TaxObligationEntity;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de Salida (Hexagonal Architecture) para la persistencia de Cartera de Clientes,
 * Radar de Obligaciones DIAN y Expediente Digital de Papeles de Trabajo.
 */
public interface AccountingPortfolioRepositoryPort {

    List<AccountingClientEntity> findAllClientsOrderByBusinessNameAsc();

    Optional<AccountingClientEntity> findClientById(Long id);

    Optional<AccountingClientEntity> findClientByCleanNit(String cleanNit);

    boolean existsClientById(Long id);

    AccountingClientEntity saveClient(AccountingClientEntity client);

    List<TaxObligationEntity> findAllObligationsOrderByDueDateAsc();

    Optional<TaxObligationEntity> findObligationById(Long id);

    TaxObligationEntity saveObligation(TaxObligationEntity obligation);

    List<TaxObligationEntity> saveAllObligations(List<TaxObligationEntity> obligations);

    List<SavedWorkpaperEntity> findAllWorkpapersOrderByCreatedAtDesc();

    List<SavedWorkpaperEntity> findWorkpapersByClientIdOrderByCreatedAtDesc(Long clientId);

    SavedWorkpaperEntity saveWorkpaper(SavedWorkpaperEntity workpaper);
}
