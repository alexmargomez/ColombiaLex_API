package com.alcontable.portfolio.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class AccountingFirmDataSeeder implements ApplicationRunner {

    private final AccountingClientJpaRepository clientRepo;
    private final TaxObligationJpaRepository obligationRepo;
    private final SavedWorkpaperJpaRepository workpaperRepo;

    public AccountingFirmDataSeeder(
            AccountingClientJpaRepository clientRepo,
            TaxObligationJpaRepository obligationRepo,
            SavedWorkpaperJpaRepository workpaperRepo
    ) {
        this.clientRepo = clientRepo;
        this.obligationRepo = obligationRepo;
        this.workpaperRepo = workpaperRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedPortfolioIfEmpty();
    }

    @Transactional
    public void seedPortfolioIfEmpty() {
        if (clientRepo.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();

        AccountingClientEntity c1 = clientRepo.save(new AccountingClientEntity(
                "901458231", "3", 1,
                "Inversiones y Logística Andina S.A.S.",
                "JURIDICA", "ORDINARIO", "BIMESTRAL",
                "05,07,14,48,52", "11001", "BOGOTÁ, D.C.",
                new BigDecimal("3850000000"), 18, true, "ACTIVO"
        ));

        AccountingClientEntity c2 = clientRepo.save(new AccountingClientEntity(
                "901782109", "2", 9,
                "Arquitectura & Consultoría Digital S.A.S.",
                "JURIDICA", "SIMPLE_RST", "CUATRIMESTRAL",
                "47,48,52", "05001", "MEDELLÍN",
                new BigDecimal("920000000"), 6, true, "ACTIVO"
        ));

        AccountingClientEntity c3 = clientRepo.save(new AccountingClientEntity(
                "800194320", "8", 0,
                "Comercializadora Médica del Valle Ltda.",
                "JURIDICA", "ORDINARIO", "CUATRIMESTRAL",
                "05,07,14,48,52", "76001", "CALI",
                new BigDecimal("1640000000"), 11, true, "ACTIVO"
        ));

        AccountingClientEntity c4 = clientRepo.save(new AccountingClientEntity(
                "79945123", "5", 3,
                "Dr. Alejandro Martínez Restrepo (Especialista)",
                "NATURAL", "PERSONA_NATURAL", "NO_RESPONSABLE",
                "05,14", "11001", "BOGOTÁ, D.C.",
                new BigDecimal("310000000"), 2, true, "ACTIVO"
        ));

        AccountingClientEntity c5 = clientRepo.save(new AccountingClientEntity(
                "900612349", "1", 9,
                "Agroindustrias del Oriente S.A.S.",
                "JURIDICA", "ORDINARIO", "BIMESTRAL",
                "05,07,14,48,52", "68001", "BUCARAMANGA",
                new BigDecimal("4950000000"), 32, true, "ACTIVO"
        ));

        obligationRepo.saveAll(List.of(
                new TaxObligationEntity(c1, "RETEFUENTE_350", "350", "2026-M09", today.minusDays(18), "VENCIDA", new BigDecimal("14850000"), new BigDecimal("743000")),
                new TaxObligationEntity(c1, "IVA_300", "300", "2026-B05", today.plusDays(4), "PENDIENTE", new BigDecimal("42300000"), BigDecimal.ZERO),
                new TaxObligationEntity(c1, "EXOGENA_1001", "1001", "2025-ANUAL", today.minusDays(60), "PRESENTADA", BigDecimal.ZERO, BigDecimal.ZERO),

                new TaxObligationEntity(c2, "ANTICIPO_RST_2593", "2593", "2026-B05", today.plusDays(3), "PENDIENTE", new BigDecimal("6840000"), BigDecimal.ZERO),
                new TaxObligationEntity(c2, "RETEFUENTE_350", "350", "2026-M09", today.minusDays(10), "PRESENTADA", new BigDecimal("2150000"), BigDecimal.ZERO),

                new TaxObligationEntity(c3, "IVA_300", "300", "2026-C02", today.minusDays(35), "VENCIDA", new BigDecimal("19400000"), new BigDecimal("970000")),
                new TaxObligationEntity(c3, "RETEFUENTE_350", "350", "2026-M10", today.plusDays(6), "PENDIENTE", new BigDecimal("7620000"), BigDecimal.ZERO),

                new TaxObligationEntity(c4, "RENTA_PN_210", "210", "2025-ANUAL", today.minusDays(22), "VENCIDA", new BigDecimal("11200000"), new BigDecimal("560000")),

                new TaxObligationEntity(c5, "RETEFUENTE_350", "350", "2026-M10", today.plusDays(2), "PENDIENTE", new BigDecimal("26400000"), BigDecimal.ZERO),
                new TaxObligationEntity(c5, "RENTA_PJ_110", "110", "2025-CUOTA2", today.minusDays(40), "PRESENTADA", new BigDecimal("84500000"), BigDecimal.ZERO)
        ));

        workpaperRepo.saveAll(List.of(
                new SavedWorkpaperEntity(
                        c1,
                        "NOMINA_UGPP_PUC",
                        "Auditoría Nómina Octubre + Comprobante Contable PUC (18 Empleados)",
                        "2026-M10",
                        "Se detectaron 2 empleados con pagos no salariales superiores al tope del 40% (Ley 1393 Art. 30). Exceso gravado en IBC: $2.600.000 COP. Partida doble PUC cuadrada al peso.",
                        new BigDecimal("2600000"),
                        "{\"module\":\"payroll-ugpp\"}"
                ),
                new SavedWorkpaperEntity(
                        c3,
                        "EXOGENA_PREAUDITORIA",
                        "Pre-Auditoría Formato 1001 y Retenciones Practicadas (Q3)",
                        "2026-Q3",
                        "Auditados 12 terceros del balance de prueba: 3 NITs con DV erróneo corregidos por Módulo 11 y 2 alertas de sub-retención por $845.000 COP (Art. 370 E.T.).",
                        new BigDecimal("845000"),
                        "{\"module\":\"exogena-batch\"}"
                ),
                new SavedWorkpaperEntity(
                        c2,
                        "PLANEACION_LEY2277_RST",
                        "Estudio Comparativo Régimen Ordinario vs SIMPLE (RST) 2026",
                        "2026-ANUAL",
                        "La permanencia en Régimen Simple (RST) descontando aportes patronales a pensión y 0.5% de pagos electrónicos genera un ahorro líquido anual de $24.180.000 COP.",
                        new BigDecimal("24180000"),
                        "{\"module\":\"tax-planning\"}"
                ),
                new SavedWorkpaperEntity(
                        c1,
                        "LIQUIDACION_SANCIONES_DIAN",
                        "Liquidación Extemporaneidad Retefuente 350 con Gradualidad Art. 640 (50%)",
                        "2026-M09",
                        "Sanción plena Art. 641 ($742.500) reducida al 50% por Art. 640 y ajustada al piso legal de Sanción Mínima 10 UVT ($523.000 COP) + Intereses de mora.",
                        new BigDecimal("523000"),
                        "{\"module\":\"dian-sanctions\"}"
                )
        ));
    }
}
