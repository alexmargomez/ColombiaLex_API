package com.alcontable.portfolio.domain.ports;

import com.alcontable.portfolio.infrastructure.persistence.AccountantUserEntity;
import com.alcontable.portfolio.infrastructure.persistence.AccountingClientEntity;
import com.alcontable.portfolio.infrastructure.persistence.SavedWorkpaperEntity;
import com.alcontable.portfolio.infrastructure.persistence.TaxObligationEntity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Puerto de Salida (Hexagonal Architecture) para la persistencia de Usuarios Contadores,
 * Cartera Privada de Clientes, Radar de Obligaciones DIAN y Expediente Digital de Papeles de Trabajo.
 */
public interface AccountingPortfolioRepositoryPort {

    List<AccountingClientEntity> findAllClientsOrderByBusinessNameAsc();

    default List<AccountingClientEntity> findClientsByAccountantUserIdOrderByBusinessNameAsc(Long accountantUserId) {
        return findAllClientsOrderByBusinessNameAsc().stream()
                .filter(c -> Objects.equals(c.getAccountantUserId(), accountantUserId))
                .toList();
    }

    Optional<AccountingClientEntity> findClientById(Long id);

    Optional<AccountingClientEntity> findClientByCleanNit(String cleanNit);

    default Optional<AccountingClientEntity> findClientByAccountantUserIdAndCleanNit(Long accountantUserId, String cleanNit) {
        return findAllClientsOrderByBusinessNameAsc().stream()
                .filter(c -> Objects.equals(c.getAccountantUserId(), accountantUserId) && cleanNit.equals(c.getNit()))
                .findFirst();
    }

    default Optional<AccountingClientEntity> findClientByCleanNitAndAccountantUserId(String cleanNit, Long accountantUserId) {
        return findClientByAccountantUserIdAndCleanNit(accountantUserId, cleanNit);
    }

    boolean existsClientById(Long id);

    AccountingClientEntity saveClient(AccountingClientEntity client);

    List<TaxObligationEntity> findAllObligationsOrderByDueDateAsc();

    default List<TaxObligationEntity> findObligationsByAccountantUserIdOrderByDueDateAsc(Long accountantUserId) {
        return findAllObligationsOrderByDueDateAsc().stream()
                .filter(o -> o.getClient() != null && Objects.equals(o.getClient().getAccountantUserId(), accountantUserId))
                .toList();
    }

    Optional<TaxObligationEntity> findObligationById(Long id);

    TaxObligationEntity saveObligation(TaxObligationEntity obligation);

    List<TaxObligationEntity> saveAllObligations(List<TaxObligationEntity> obligations);

    List<SavedWorkpaperEntity> findAllWorkpapersOrderByCreatedAtDesc();

    default List<SavedWorkpaperEntity> findWorkpapersByAccountantUserIdOrderByCreatedAtDesc(Long accountantUserId) {
        return findAllWorkpapersOrderByCreatedAtDesc().stream()
                .filter(w -> Objects.equals(w.getAccountantUserId(), accountantUserId)
                        || (w.getClient() != null && Objects.equals(w.getClient().getAccountantUserId(), accountantUserId)))
                .toList();
    }

    List<SavedWorkpaperEntity> findWorkpapersByClientIdOrderByCreatedAtDesc(Long clientId);

    SavedWorkpaperEntity saveWorkpaper(SavedWorkpaperEntity workpaper);

    default Optional<AccountantUserEntity> findUserByEmail(String email) {
        return Optional.empty();
    }

    default Optional<AccountantUserEntity> findUserByEmailIgnoreCase(String email) {
        return findUserByEmail(email);
    }

    default Optional<AccountantUserEntity> findUserBySessionToken(String sessionToken) {
        return Optional.empty();
    }

    default Optional<AccountantUserEntity> findUserByAuthToken(String authToken) {
        return findUserBySessionToken(authToken);
    }

    default Optional<AccountantUserEntity> findUserById(Long id) {
        return Optional.empty();
    }

    default AccountantUserEntity saveUser(AccountantUserEntity user) {
        return user;
    }
}
