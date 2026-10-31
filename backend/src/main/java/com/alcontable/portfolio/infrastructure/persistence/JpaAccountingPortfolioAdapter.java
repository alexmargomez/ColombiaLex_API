package com.alcontable.portfolio.infrastructure.persistence;

import com.alcontable.portfolio.domain.ports.AccountingPortfolioRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaAccountingPortfolioAdapter implements AccountingPortfolioRepositoryPort {

    private final AccountingClientJpaRepository clientRepo;
    private final TaxObligationJpaRepository obligationRepo;
    private final SavedWorkpaperJpaRepository workpaperRepo;
    private final AccountantUserJpaRepository userRepo;

    public JpaAccountingPortfolioAdapter(
            AccountingClientJpaRepository clientRepo,
            TaxObligationJpaRepository obligationRepo,
            SavedWorkpaperJpaRepository workpaperRepo,
            AccountantUserJpaRepository userRepo
    ) {
        this.clientRepo = clientRepo;
        this.obligationRepo = obligationRepo;
        this.workpaperRepo = workpaperRepo;
        this.userRepo = userRepo;
    }

    @Override
    public List<AccountingClientEntity> findAllClientsOrderByBusinessNameAsc() {
        return clientRepo.findAllByOrderByBusinessNameAsc();
    }

    @Override
    public List<AccountingClientEntity> findClientsByAccountantUserIdOrderByBusinessNameAsc(Long accountantUserId) {
        return clientRepo.findByAccountantUserIdOrderByBusinessNameAsc(accountantUserId);
    }

    @Override
    public Optional<AccountingClientEntity> findClientById(Long id) {
        return clientRepo.findById(id);
    }

    @Override
    public Optional<AccountingClientEntity> findClientByCleanNit(String cleanNit) {
        return clientRepo.findFirstByNit(cleanNit);
    }

    @Override
    public Optional<AccountingClientEntity> findClientByAccountantUserIdAndCleanNit(Long accountantUserId, String cleanNit) {
        return clientRepo.findFirstByAccountantUserIdAndNit(accountantUserId, cleanNit);
    }

    @Override
    public boolean existsClientById(Long id) {
        return clientRepo.existsById(id);
    }

    @Override
    public AccountingClientEntity saveClient(AccountingClientEntity client) {
        return clientRepo.save(client);
    }

    @Override
    public List<TaxObligationEntity> findAllObligationsOrderByDueDateAsc() {
        return obligationRepo.findAllByOrderByDueDateAsc();
    }

    @Override
    public List<TaxObligationEntity> findObligationsByAccountantUserIdOrderByDueDateAsc(Long accountantUserId) {
        return obligationRepo.findByClient_AccountantUserIdOrderByDueDateAsc(accountantUserId);
    }

    @Override
    public Optional<TaxObligationEntity> findObligationById(Long id) {
        return obligationRepo.findById(id);
    }

    @Override
    public TaxObligationEntity saveObligation(TaxObligationEntity obligation) {
        return obligationRepo.save(obligation);
    }

    @Override
    public List<TaxObligationEntity> saveAllObligations(List<TaxObligationEntity> obligations) {
        return obligationRepo.saveAll(obligations);
    }

    @Override
    public List<SavedWorkpaperEntity> findAllWorkpapersOrderByCreatedAtDesc() {
        return workpaperRepo.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<SavedWorkpaperEntity> findWorkpapersByAccountantUserIdOrderByCreatedAtDesc(Long accountantUserId) {
        return workpaperRepo.findByAccountantUserIdOrderByCreatedAtDesc(accountantUserId);
    }

    @Override
    public List<SavedWorkpaperEntity> findWorkpapersByClientIdOrderByCreatedAtDesc(Long clientId) {
        return workpaperRepo.findByClient_IdOrderByCreatedAtDesc(clientId);
    }

    @Override
    public SavedWorkpaperEntity saveWorkpaper(SavedWorkpaperEntity workpaper) {
        return workpaperRepo.save(workpaper);
    }

    @Override
    public Optional<AccountantUserEntity> findUserByEmail(String email) {
        return userRepo.findByEmailIgnoreCase(email);
    }

    @Override
    public Optional<AccountantUserEntity> findUserBySessionToken(String sessionToken) {
        return userRepo.findBySessionToken(sessionToken);
    }

    @Override
    public AccountantUserEntity saveUser(AccountantUserEntity user) {
        return userRepo.save(user);
    }
}
