package com.alexmar.colombialex.tax.infrastructure.controllers;

import com.alexmar.colombialex.tax.application.usecases.TaxDianUseCase;
import com.alexmar.colombialex.tax.domain.vo.NitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tax")
@Tag(name = "2. Motor Tributario DIAN (NIT Módulo 11, UVT, Retefuente, CIIU)", description = "Cálculo y validación de Dígito de Verificación DIAN, conversión UVT/SMLMV/COP, Retención en la Fuente y actividades CIIU")
public class TaxDianController {

    private final TaxDianUseCase taxDianUseCase;

    public TaxDianController(TaxDianUseCase taxDianUseCase) {
        this.taxDianUseCase = taxDianUseCase;
    }

    @GetMapping("/nit/{nit}")
    @Operation(summary = "Calcular y validar el Dígito de Verificación (DV) de un NIT con el algoritmo oficial Módulo 11 de la DIAN")
    public NitVO.NitVerificationReport verifyNit(@PathVariable String nit) {
        return taxDianUseCase.verifyNit(nit);
    }

    @GetMapping("/uvt-convert")
    @Operation(summary = "Convertir valores entre UVT, Pesos Colombianos (COP) y Salarios Mínimos (SMLMV) para cualquier año (2020-2026)")
    public TaxDianUseCase.UvtConversionResponse convertUnits(
            @RequestParam(defaultValue = "2026") Integer year,
            @RequestParam(defaultValue = "100") BigDecimal amount,
            @RequestParam(defaultValue = "UVT") String unit
    ) {
        return taxDianUseCase.convertUnits(year, amount, unit);
    }

    @GetMapping("/withholding/concepts")
    @Operation(summary = "Listar conceptos y topes oficiales de Retención en la Fuente (UVT y tarifas declarante/no declarante)")
    public List<TaxDianUseCase.WithholdingRuleConcept> getWithholdingConcepts() {
        return taxDianUseCase.listWithholdingConcepts();
    }

    @PostMapping("/withholding")
    @Operation(summary = "Calcular Retención en la Fuente, IVA (19%/5%/0%) y Neto a Pagar según topes de UVT")
    public TaxDianUseCase.WithholdingCalculationResponse calculateWithholding(
            @RequestBody TaxDianUseCase.WithholdingCalculationRequest request
    ) {
        return taxDianUseCase.calculateWithholding(request);
    }

    @GetMapping("/ciiu")
    @Operation(summary = "Buscar actividades económicas del catálogo CIIU Rev. 4 A.C. y su tarifa referencial de Retención/ICA")
    public Map<String, Object> searchCiiu(@RequestParam(required = false) String q) {
        var results = taxDianUseCase.searchCiiu(q);
        return Map.of("count", results.size(), "items", results);
    }
}
