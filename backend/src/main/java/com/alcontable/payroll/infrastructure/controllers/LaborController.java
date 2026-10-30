package com.alcontable.payroll.infrastructure.controllers;

import com.alcontable.payroll.application.usecases.CalculateContractSettlementUseCase;
import com.alcontable.payroll.application.usecases.CalculateOvertimeUseCase;
import com.alcontable.payroll.domain.model.LaborLegalConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/labor")
@Tag(name = "1. Derecho Laboral & Nómina (CST + Ley 2101)", description = "Cálculo de horas extra, recargos nocturnos/dominicales, cesantías, prima, vacaciones, seguridad social e indemnización Art. 64 CST")
public class LaborController {

    private final CalculateOvertimeUseCase calculateOvertimeUseCase;
    private final CalculateContractSettlementUseCase calculateContractSettlementUseCase;

    public LaborController(
            CalculateOvertimeUseCase calculateOvertimeUseCase,
            CalculateContractSettlementUseCase calculateContractSettlementUseCase
    ) {
        this.calculateOvertimeUseCase = calculateOvertimeUseCase;
        this.calculateContractSettlementUseCase = calculateContractSettlementUseCase;
    }

    @GetMapping("/parameters")
    @Operation(summary = "Consultar histórico oficial de SMLMV, Auxilio de Transporte, UVT y Jornada Ley 2101 (2020-2026)")
    public Map<String, Object> getLegalParameters() {
        return Map.of(
                "country", "CO",
                "currency", "COP",
                "historicalParameters", LaborLegalConstants.allYears()
        );
    }

    @PostMapping("/overtime")
    @Operation(summary = "Calcular Horas Extra y Recargos (Diurnos, Nocturnos, Dominicales y Festivos) según Ley 2101")
    public CalculateOvertimeUseCase.OvertimeCalculationResponse calculateOvertime(
            @RequestBody CalculateOvertimeUseCase.OvertimeRequest request
    ) {
        return calculateOvertimeUseCase.execute(request);
    }

    @PostMapping("/settlement")
    @Operation(summary = "Liquidar Prestaciones Sociales (Cesantías, Intereses 12%, Prima, Vacaciones), Seguridad Social e Indemnización Art. 64 CST")
    public CalculateContractSettlementUseCase.SettlementResponse calculateSettlement(
            @RequestBody CalculateContractSettlementUseCase.SettlementRequest request
    ) {
        return calculateContractSettlementUseCase.execute(request);
    }
}
