package com.alcontable.exogena.infrastructure.controllers;

import com.alcontable.exogena.application.usecases.QueryDivipolaUseCase;
import com.alcontable.exogena.domain.model.MunicipalityEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "4. Geografía Oficial DANE / DIVIPOLA", description = "Consulta de Departamentos y Municipios de Colombia con código DANE de 5 dígitos, región y coordenadas")
public class GeoDivipolaController {

    private final QueryDivipolaUseCase queryDivipolaUseCase;

    public GeoDivipolaController(QueryDivipolaUseCase queryDivipolaUseCase) {
        this.queryDivipolaUseCase = queryDivipolaUseCase;
    }

    @GetMapping("/health")
    @Operation(summary = "Estado de salud del motor ALcontable API (Java 21 + Spring Boot 3)")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "ALcontable API",
                "runtime", "Java " + System.getProperty("java.version") + " (Spring Boot 3.3)",
                "architecture", "DDD + Hexagonal (Ports & Adapters)",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/geo/departments")
    @Operation(summary = "Listar departamentos de Colombia con su código DANE de 2 dígitos y región")
    public List<QueryDivipolaUseCase.DepartmentSummary> listDepartments() {
        return queryDivipolaUseCase.listDepartments();
    }

    @GetMapping("/geo/municipalities")
    @Operation(summary = "Buscar municipios de Colombia por código de departamento o texto (nombre/código DANE)")
    public Map<String, Object> searchMunicipalities(
            @RequestParam(required = false) String departmentCode,
            @RequestParam(required = false) String q
    ) {
        var list = queryDivipolaUseCase.searchMunicipalities(departmentCode, q);
        return Map.of("count", list.size(), "items", list);
    }

    @GetMapping("/geo/municipalities/{daneCode}")
    @Operation(summary = "Consultar un municipio específico por su código oficial DANE de 5 dígitos (ej. 11001, 68001, 68895)")
    public MunicipalityEntity getByDaneCode(@PathVariable String daneCode) {
        return queryDivipolaUseCase.findByDaneCode(daneCode);
    }
}
