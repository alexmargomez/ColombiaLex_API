package com.alcontable.portfolio.infrastructure.controllers;

import com.alcontable.exogena.application.usecases.ExogenaBatchAuditUseCase;
import com.alcontable.payroll.application.usecases.PayrollBatchAuditAndPucUseCase;
import com.alcontable.payroll.domain.model.LaborLegalConstants;
import com.alcontable.portfolio.application.usecases.ClientPortfolioUseCase;
import com.alcontable.sanctions.application.usecases.DianSanctionAndInterestUseCase;
import com.alcontable.taxplanning.application.usecases.TaxPlanningAndLaw2277UseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounting")
@Tag(
        name = "0. Estación de Trabajo Contable ALcontable (Cartera, UGPP+PUC, Exógena, Ley 2277/RST & Sanciones DIAN)",
        description = "Motores profesionales de procesamiento en lote, auditoría UGPP, comprobantes contables PUC, pre-auditoría de Exógena, planeación tributaria Ley 2277/RST y liquidación forense de sanciones DIAN con persistencia en MariaDB"
)
public class AccountingWorkstationController {

    private final ClientPortfolioUseCase portfolioUseCase;
    private final PayrollBatchAuditAndPucUseCase payrollBatchUseCase;
    private final ExogenaBatchAuditUseCase exogenaBatchUseCase;
    private final TaxPlanningAndLaw2277UseCase taxPlanningUseCase;
    private final DianSanctionAndInterestUseCase sanctionUseCase;

    public AccountingWorkstationController(
            ClientPortfolioUseCase portfolioUseCase,
            PayrollBatchAuditAndPucUseCase payrollBatchUseCase,
            ExogenaBatchAuditUseCase exogenaBatchUseCase,
            TaxPlanningAndLaw2277UseCase taxPlanningUseCase,
            DianSanctionAndInterestUseCase sanctionUseCase
    ) {
        this.portfolioUseCase = portfolioUseCase;
        this.payrollBatchUseCase = payrollBatchUseCase;
        this.exogenaBatchUseCase = exogenaBatchUseCase;
        this.taxPlanningUseCase = taxPlanningUseCase;
        this.sanctionUseCase = sanctionUseCase;
    }

    @GetMapping("/parameters/current")
    @Operation(summary = "Consultar parámetros vigentes UVT, SMLMV, Auxilio de Transporte y Sanción Mínima 10 UVT")
    public Map<String, Object> getCurrentParameters(@RequestParam(defaultValue = "2026") int year) {
        var p = LaborLegalConstants.forYear(year);
        return Map.of(
                "year", p.year(),
                "uvtCOP", p.uvtCOP(),
                "smlmvCOP", p.smlmvCOP(),
                "transportAllowanceCOP", p.transportAllowanceCOP(),
                "minimumSanction10UvtCOP", p.uvtCOP().multiply(java.math.BigDecimal.TEN),
                "allYears", LaborLegalConstants.allYears()
        );
    }

    @GetMapping("/portfolio/dashboard")
    @Operation(summary = "Módulo 1: Obtener Cartera de Clientes, Radar de Obligaciones Tributarias y Papeles de Trabajo guardados en MariaDB")
    public ClientPortfolioUseCase.PortfolioDashboardResponse getPortfolioDashboard() {
        return portfolioUseCase.getDashboard();
    }

    @PostMapping("/portfolio/clients")
    @Operation(summary = "Módulo 1: Registrar nuevo cliente en la cartera contable (calcula DV Módulo 11 y autogenera calendario de obligaciones)")
    public ClientPortfolioUseCase.ClientSummaryDto createClient(
            @RequestBody ClientPortfolioUseCase.CreateClientRequest request
    ) {
        return portfolioUseCase.createClient(request);
    }

    @PatchMapping("/portfolio/obligations/{id}/status")
    @Operation(summary = "Módulo 1: Actualizar estado de una obligación tributaria (PENDIENTE, EN_PREPARACION, PRESENTADA, VENCIDA)")
    public ClientPortfolioUseCase.ObligationRadarDto updateObligationStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        return portfolioUseCase.updateObligationStatus(id, body != null ? body.get("status") : null);
    }

    @GetMapping("/portfolio/workpapers")
    @Operation(summary = "Módulo 1: Consultar papeles de trabajo guardados en MariaDB (filtrables por clientId)")
    public List<ClientPortfolioUseCase.WorkpaperDto> listWorkpapers(@RequestParam(required = false) Long clientId) {
        return portfolioUseCase.listWorkpapers(clientId);
    }

    @PostMapping("/portfolio/workpapers")
    @Operation(summary = "Guardar un Papel de Trabajo en el expediente del cliente en MariaDB")
    public ClientPortfolioUseCase.WorkpaperDto saveWorkpaper(
            @RequestBody ClientPortfolioUseCase.SaveWorkpaperRequest request
    ) {
        return portfolioUseCase.saveWorkpaper(request);
    }

    @PostMapping("/payroll/batch-audit")
    @Operation(summary = "Módulo 2: Auditar planilla de nómina multi-empleado en lote (Tope 40% UGPP Ley 1393, PILA, Art. 114-1) y generar Comprobante Contable PUC cuadrado")
    public PayrollBatchAuditAndPucUseCase.PayrollBatchAuditResponse auditPayrollBatch(
            @RequestBody PayrollBatchAuditAndPucUseCase.PayrollBatchRequest request
    ) {
        return payrollBatchUseCase.execute(request);
    }

    @PostMapping("/exogena/batch-audit")
    @Operation(summary = "Módulo 3: Pre-auditoría masiva en lote de Exógena DIAN, limpieza de NITs/DV Módulo 11, homologación DANE y cruce de retenciones practicadas")
    public ExogenaBatchAuditUseCase.ExogenaBatchAuditResponse auditExogenaBatch(
            @RequestBody ExogenaBatchAuditUseCase.ExogenaBatchRequest request
    ) {
        return exogenaBatchUseCase.execute(request);
    }

    @PostMapping("/tax-planning/law-2277-simulator")
    @Operation(summary = "Módulo 4: Depurador completo Art. 383/388 E.T. (Ley 2277 topes 790 y 1.340 UVT) y Comparador Estratégico Régimen Ordinario vs SIMPLE (RST)")
    public TaxPlanningAndLaw2277UseCase.TaxPlanningResponse simulateTaxPlanning(
            @RequestBody TaxPlanningAndLaw2277UseCase.Law2277AndRstRequest request
    ) {
        return taxPlanningUseCase.execute(request);
    }

    @PostMapping("/sanctions/liquidate")
    @Operation(summary = "Módulo 5: Liquidador forense de Sanciones DIAN (Art. 641, 642, 644, gradualidad Art. 640, piso 10 UVT Art. 639, redondeo Art. 868) e Intereses Moratorios")
    public DianSanctionAndInterestUseCase.SanctionLiquidationResponse liquidateSanction(
            @RequestBody DianSanctionAndInterestUseCase.SanctionLiquidationRequest request
    ) {
        return sanctionUseCase.execute(request);
    }
}
