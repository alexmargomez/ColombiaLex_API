<script setup lang="ts">
const endpoints = [
  {
    method: 'POST',
    path: '/api/v1/labor/overtime',
    desc: 'Calcula horas extra diurnas, nocturnas, recargos y dominicales/festivas aplicando la reducción gradual de la Ley 2101.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/labor/overtime \\
  -H "Content-Type: application/json" \\
  -d '{"monthlySalary":2500000,"calculationDate":"2026-08-15","extraDaytimeHours":6,"extraNightHours":3}'`,
  },
  {
    method: 'POST',
    path: '/api/v1/labor/settlement',
    desc: 'Liquida Cesantías, Intereses (12%), Prima, Vacaciones, Seguridad Social PILA e Indemnización Art. 64 CST.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/labor/settlement \\
  -H "Content-Type: application/json" \\
  -d '{"year":2026,"monthlyBaseSalary":2200000,"workedDaysTotal":540,"contractType":"INDEFINIDO","dismissalWithoutJustCause":true}'`,
  },
  {
    method: 'GET',
    path: '/api/v1/tax/nit/{nit}',
    desc: 'Calcula y verifica el Dígito de Verificación (DV) de cualquier NIT colombiano usando el algoritmo Módulo 11 DIAN.',
    sampleCurl: `curl http://localhost:8085/api/v1/tax/nit/800197268-4`,
  },
  {
    method: 'POST',
    path: '/api/v1/tax/withholding',
    desc: 'Evalúa topes mínimos en UVT y calcula Retención en la Fuente, IVA y Neto a Pagar.',
    sampleCurl: `curl -X POST http://localhost:8085/api/v1/tax/withholding \\
  -H "Content-Type: application/json" \\
  -d '{"year":2026,"conceptCode":"COMPRAS_GENERALES","subtotalBeforeVatCOP":3500000,"declarantTaxpayer":true,"vatRatePercent":19}'`,
  },
  {
    method: 'GET',
    path: '/api/v1/calendar/holidays/{year}',
    desc: 'Genera los 18 días festivos oficiales de Colombia para cualquier año aplicando la Ley Emiliani (Ley 51 de 1983).',
    sampleCurl: `curl http://localhost:8085/api/v1/calendar/holidays/2026`,
  },
  {
    method: 'GET',
    path: '/api/v1/geo/municipalities?q=Zapatoca',
    desc: 'Consulta departamentos y municipios oficiales del catálogo DANE / DIVIPOLA.',
    sampleCurl: `curl "http://localhost:8085/api/v1/geo/municipalities?q=Zapatoca"`,
  },
];
</script>

<template>
  <div class="space-y-4">
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-code text-amber-400"></i>
          Referencia Rápida de Endpoints REST &amp; OpenAPI 3.1 (Java 21 + Spring Boot 3.3)
        </h3>
        <p class="text-xs text-slate-400 mt-1">
          Todos los endpoints son públicos y gratuitos para que cualquier desarrollador los consuma
          desde su aplicación.
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
        :key="ep.path"
        class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-3"
      >
        <div class="flex flex-wrap items-center gap-2.5">
          <span
            class="px-2.5 py-0.5 rounded font-mono text-xs font-bold"
            :class="
              ep.method === 'POST'
                ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
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
