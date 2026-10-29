package com.alexmar.colombialex.geo.application.usecases;

import com.alexmar.colombialex.geo.domain.model.MunicipalityEntity;
import com.alexmar.colombialex.geo.domain.ports.MunicipalityRepositoryPort;
import com.alexmar.colombialex.shared.domain.exceptions.DomainValidationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class QueryDivipolaUseCase {

    private final MunicipalityRepositoryPort repository;

    public QueryDivipolaUseCase(MunicipalityRepositoryPort repository) {
        this.repository = repository;
    }

    public record DepartmentSummary(
            String departmentCode,
            String departmentName,
            String region,
            long municipalitiesRegisteredCount
    ) {}

    public List<DepartmentSummary> listDepartments() {
        List<MunicipalityEntity> all = repository.findAll();
        Map<String, List<MunicipalityEntity>> grouped = all.stream()
                .collect(Collectors.groupingBy(MunicipalityEntity::getDepartmentCode));

        return grouped.entrySet().stream()
                .map(entry -> {
                    MunicipalityEntity first = entry.getValue().get(0);
                    return new DepartmentSummary(
                            entry.getKey(),
                            first.getDepartmentName(),
                            first.getRegion(),
                            entry.getValue().size()
                    );
                })
                .sorted(Comparator.comparing(DepartmentSummary::departmentName))
                .toList();
    }

    public List<MunicipalityEntity> searchMunicipalities(String departmentCode, String q) {
        List<MunicipalityEntity> base = (departmentCode != null && !departmentCode.isBlank())
                ? repository.findByDepartmentCodeOrderByMunicipalityNameAsc(departmentCode.trim())
                : repository.findAll();

        if (q == null || q.isBlank()) {
            return base.stream()
                    .sorted(Comparator.comparing(MunicipalityEntity::getDepartmentName)
                            .thenComparing(MunicipalityEntity::getMunicipalityName))
                    .toList();
        }

        String term = q.trim().toLowerCase();
        return base.stream()
                .filter(m -> m.getDaneCode().contains(term)
                        || m.getMunicipalityName().toLowerCase().contains(term)
                        || m.getDepartmentName().toLowerCase().contains(term)
                        || m.getRegion().toLowerCase().contains(term))
                .sorted(Comparator.comparing(MunicipalityEntity::getMunicipalityName))
                .toList();
    }

    public MunicipalityEntity findByDaneCode(String daneCode) {
        if (daneCode == null || daneCode.isBlank()) {
            throw new DomainValidationException("Debe proporcionar un código DANE válido de 5 dígitos.");
        }
        return repository.findByDaneCode(daneCode.trim())
                .orElseThrow(() -> new DomainValidationException("NOT_FOUND", "No se encontró el municipio con código DANE: " + daneCode));
    }
}
