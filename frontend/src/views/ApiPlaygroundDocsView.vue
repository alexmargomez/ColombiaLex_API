<script setup lang="ts">
const endpoints = [
  {
    method: 'GET',
    path: '/api/v1/accounting/parameters/current',
    desc: 'Consulta los parámetros vigentes e históricos de UVT, SMLMV, Auxilio de Transporte y Sanción Mínima de 10 UVT (2020–2026).',
    sampleCurl: `curl "http://localhost:8085/api/v1/accounting/parameters/current?year=2026"`,
  },
  {
    method: 'GET',
    path: '/api/v1/accounting/portfolio/dashboard',
    desc: 'Módulo 1: Obtiene la Cartera de Clientes del Estudio Contable, el Radar de Obligaciones Tributarias por NIT y los Papeles de Trabajo guardados en MariaDB.',
    sampleCurl: `curl http://localhost:8085/api/v1/accounting/portfolio/dashboard`,
  },
  {
    method: 'POST',
    path: '/api/v1/accounting/portfolio/clients',
    desc: 'Módulo 1: Registra un nuevo cliente en MariaDB, calcula el DV por Módulo 11 DIAN y genera automáticamente su calendario tributario en días hábiles.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/accounting/portfolio/clients \\
  -H "Content-Type: application/json" \\
  -d '{"nit":"900123456","businessName":"Industrias del Café S.A.S.","taxRegime":"ORDINARIO","estimatedAnnualIncomeCop":1850000000}'`,
  },
  {
    method: 'PATCH',
    path: '/api/v1/accounting/portfolio/obligations/{id}/status',
    desc: 'Módulo 1: Actualiza el estado de una obligación en el radar tributario (PENDIENTE, EN_PREPARACION, PRESENTADA, VENCIDA).',
    sampleCurl: `curl -X PATCH http://localhost:8085/api/v1/accounting/portfolio/obligations/1/status \\
  -H "Content-Type: application/json" \\
  -d '{"status":"PRESENTADA"}'`,
  },
  {
    method: 'GET',
    path: '/api/v1/accounting/portfolio/workpapers',
    desc: 'Módulo 1: Consulta los papeles de trabajo guardados en MariaDB (filtrables opcionalmente por clientId).',
    sampleCurl: `curl "http://localhost:8085/api/v1/accounting/portfolio/workpapers?clientId=1"`,
  },
  {
    method: 'POST',
    path: '/api/v1/accounting/portfolio/workpapers',
    desc: 'Módulo 1: Persiste un Papel de Trabajo en el expediente digital del cliente en MariaDB.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/accounting/portfolio/workpapers \\
  -H "Content-Type: application/json" \\
  -d '{"clientId":1,"workpaperType":"NOMINA_UGPP_PUC","title":"Auditoría Nómina Octubre","taxPeriod":"2026-M10","detectedSavingsOrRiskCop":1000000}'`,
  },
  {
    method: 'POST',
    path: '/api/v1/accounting/payroll/batch-audit',
    desc: 'Módulo 2: Audita una planilla completa de empleados en lote contra el tope del 40% no salarial de la UGPP (Ley 1393 Art. 30), calcula PILA + Art. 114-1 E.T. y genera el Comprobante Contable PUC de 6 dígitos cuadrado al peso.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/accounting/payroll/batch-audit \\
  -H "Content-Type: application/json" \\
  -d '{"year":2026,"employerAppliesArt1141Exoneration":true,"employees":[{"document":"79456120","employeeName":"Carlos Ruiz","area":"ADMINISTRACION","baseSalaryCOP":3200000,"nonSalaryPaymentsArt128COP":3800000,"arlRiskClass":"I"}]}'`,
  },
  {
    method: 'POST',
    path: '/api/v1/accounting/exogena/batch-audit',
    desc: 'Módulo 3: Pre-auditor masivo en lote de Información Exógena DIAN (Formato 1001/1007), limpieza y corrección de DV Módulo 11, homologación DANE DIVIPOLA y cruce de retenciones practicadas vs tarifa legal (Art. 177 y 370 E.T.).',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/accounting/exogena/batch-audit \\
  -H "Content-Type: application/json" \\
  -d '{"year":2026,"rawTsvText":"800.197.268-9\\tPROVEEDOR SAS\\tSERVICIOS\\tSI\\t5000000\\t50000\\tMedellin"}'`,
  },
  {
    method: 'POST',
    path: '/api/v1/accounting/tax-planning/law-2277-simulator',
    desc: 'Módulo 4: Ejecuta la hoja de trabajo depurada de Retención Art. 383/388 E.T. con los topes de Ley 2277 (790 UVT, 1.340 UVT y 40%) y compara lado a lado Régimen Ordinario (35% + ICA) vs Régimen Simple de Tributación (RST Art. 908).',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/accounting/tax-planning/law-2277-simulator \\
  -H "Content-Type: application/json" \\
  -d '{"year":2026,"monthlyGrossIncomeCOP":18000000,"annualGrossRevenueCOP":1200000000,"annualDeductibleCostsAndExpensesCOP":420000000,"rstActivityGroup":"GRUPO_2_COMERCIO_INDUSTRIA"}'`,
  },
  {
    method: 'POST',
    path: '/api/v1/accounting/sanctions/liquidate',
    desc: 'Módulo 5: Liquidador forense de Sanciones DIAN (Art. 641, 642, 644), aplicación de gradualidad Art. 640 (50%/75%), candado de Sanción Mínima de 10 UVT (Art. 639), redondeo Art. 868 e intereses moratorios para Formulario 490.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/accounting/sanctions/liquidate \\
  -H "Content-Type: application/json" \\
  -d '{"year":2026,"declarationType":"RETEFUENTE_350","sanctionConduct":"EXTEMPORANEIDAD_641","dueDate":"2026-10-11","filingOrPaymentDate":"2026-10-29","taxPayableOrDeltaCOP":14850000,"voluntaryLiquidationByTaxpayer":true,"yearsWithoutSameInfraction":2}'`,
  },
];
</script>

<template>
  <div class="space-y-4">
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-code text-amber-400"></i>
          Referencia de Endpoints REST &amp; Motores de Procesamiento en Lote ALcontable (Java 21 + Spring Boot 3.3 + MariaDB 11)
        </h3>
        <p class="text-xs text-slate-400 mt-1">
          Integra los motores de auditoría UGPP, asiento contable PUC, pre-auditoría de Exógena, planeación Ley 2277 y sanciones DIAN desde cualquier ERP.
        </p>
      </div>
      <a
        href="/api/v1/swagger-ui.html"
        target="_blank"
        class="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition"
      >
        <i class="fa-solid fa-arrow-up-right-from-square mr-1.5"></i>Abrir Swagger UI Oficial
      </a>
    </div>

    <div class="grid grid-cols-1 gap-4">
      <div
        v-for="ep in endpoints"
        :key="ep.method + ep.path"
        class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-3"
      >
        <div class="flex flex-wrap items-center gap-2.5">
          <span
            class="px-2.5 py-0.5 rounded font-mono text-xs font-bold"
            :class="
              ep.method === 'POST'
                ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                : ep.method === 'PATCH'
                  ? 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                  : 'bg-indigo-500/15 text-indigo-300 border border-indigo-500/30'
            "
          >
            {{ ep.method }}
          </span>
          <code class="text-sm font-bold text-white font-mono">{{ ep.path }}</code>
        </div>
        <p class="text-xs text-slate-300">{{ ep.desc }}</p>
        <pre
          class="bg-slate-950 border border-slate-800 rounded-xl p-3 text-[11px] text-amber-300 font-mono overflow-x-auto"
        >{{ ep.sampleCurl }}</pre>
      </div>
    </div>
  </div>
</template>
