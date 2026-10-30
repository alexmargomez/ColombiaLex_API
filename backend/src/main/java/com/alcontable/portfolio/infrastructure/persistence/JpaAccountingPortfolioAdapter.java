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

    public JpaAccountingPortfolioAdapter(
            AccountingClientJpaRepository clientRepo,
            TaxObligationJpaRepository obligationRepo,
            SavedWorkpaperJpaRepository workpaperRepo
    ) {
        this.clientRepo = clientRepo;
        this.obligationRepo = obligationRepo;
        this.workpaperRepo = workpaperRepo;
    }

    @Override
    public List<AccountingClientEntity> findAllClientsOrderByBusinessNameAsc() {
        return clientRepo.findAllByOrderByBusinessNameAsc();
    }

    @Override
    public Optional<AccountingClientEntity> findClientById(Long id) {
        return clientRepo.findById(id);
    }

    @Override
    public Optional<AccountingClientEntity> findClientByCleanNit(String cleanNit) {
        return clientRepo.findByNit(cleanNit);
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
    public List<SavedWorkpaperEntity> findWorkpapersByClientIdOrderByCreatedAtDesc(Long clientId) {
        return workpaperRepo.findByClient_IdOrderByCreatedAtDesc(clientId);
    }

    @Override
    public SavedWorkpaperEntity saveWorkpaper(SavedWorkpaperEntity workpaper) {
        return workpaperRepo.save(workpaper);
    }
}
