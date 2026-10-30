<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { apiRequest, copyRowsAsTsvForExcel, formatCOP } from '../services/api';

const props = defineProps<{
  activeClient?: any | null;
}>();

const loading = ref(false);
const errorMsg = ref('');
const feedbackMsg = ref('');
const planResult = ref<any>(null);

const form = reactive({
  year: 2026,
  // Depuración Art. 383 & 388 E.T. (Ley 2277)
  monthlyGrossIncomeCOP: 16500000,
  independentContractorWithCosts: false,
  monthlyHousingInterestCOP: 2200000,
  monthlyPrepaidMedicineCOP: 820000,
  dependentsCount: 2,
  monthlyVoluntaryPensionAfcCOP: 1800000,
  annualElectronicInvoicePurchasesCOP: 42000000,
  // Comparador Estratégico Régimen Ordinario vs Régimen Simple (RST)
  annualGrossRevenueCOP: 1250000000,
  annualDeductibleCostsAndExpensesCOP: 480000000,
  annualEmployerPensionContributionsCOP: 34000000,
  annualElectronicPaymentsReceivedCOP: 980000000,
  rstActivityGroup: 'GRUPO_2_COMERCIO_INDUSTRIA',
  municipalIcaRatePerThousand: 9.66,
});

watch(
  () => props.activeClient,
  (client) => {
    if (client && client.estimatedAnnualIncomeCop) {
      form.annualGrossRevenueCOP = Number(client.estimatedAnnualIncomeCop);
      form.annualDeductibleCostsAndExpensesCOP = Math.round(Number(client.estimatedAnnualIncomeCop) * 0.42);
      form.annualElectronicPaymentsReceivedCOP = Math.round(Number(client.estimatedAnnualIncomeCop) * 0.78);
      runSimulation();
    }
  }
);

async function runSimulation() {
  loading.value = true;
  errorMsg.value = '';
  try {
    planResult.value = await apiRequest('/api/v1/accounting/tax-planning/law-2277-simulator', {
      method: 'POST',
      body: JSON.stringify(form),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  } finally {
    loading.value = false;
  }
}

function applyPreset(presetType: 'DIRECTIVO' | 'CONSULTOR' | 'PYME_COMERCIAL') {
  if (presetType === 'DIRECTIVO') {
    form.monthlyGrossIncomeCOP = 22000000;
    form.monthlyHousingInterestCOP = 3800000;
    form.monthlyPrepaidMedicineCOP = 950000;
    form.dependentsCount = 3;
    form.monthlyVoluntaryPensionAfcCOP = 3000000;
  } else if (presetType === 'CONSULTOR') {
    form.monthlyGrossIncomeCOP = 11500000;
    form.monthlyHousingInterestCOP = 1200000;
    form.monthlyPrepaidMedicineCOP = 650000;
    form.dependentsCount = 1;
    form.monthlyVoluntaryPensionAfcCOP = 1000000;
    form.rstActivityGroup = 'GRUPO_4_PROFESIONALES_CONSULTORIA';
    form.annualGrossRevenueCOP = 480000000;
    form.annualDeductibleCostsAndExpensesCOP = 95000000;
  } else {
    form.annualGrossRevenueCOP = 1850000000;
    form.annualDeductibleCostsAndExpensesCOP = 720000000;
    form.annualEmployerPensionContributionsCOP = 48000000;
    form.annualElectronicPaymentsReceivedCOP = 1500000000;
    form.rstActivityGroup = 'GRUPO_2_COMERCIO_INDUSTRIA';
  }
  runSimulation();
}

async function copyExecutiveDictumForClient() {
  if (!planResult.value) return;
  const dep = planResult.value.law2277Depuration;
  const rst = planResult.value.regimeComparison;
  const clientName = props.activeClient?.businessName || 'Cliente';
  const text = [
    `📊 DICTAMEN DE PLANEACIÓN TRIBUTARIA ALCONTABLE (${planResult.value.year}) — ${clientName}`,
    `1. Depuración Retención Art. 383/388 E.T. (Ley 2277):`,
    `   • Ingreso Bruto Mensual: ${formatCOP(dep.monthlyGrossIncomeCOP)}`,
    `   • Beneficios Aceptados (Tope 40% / 1.340 UVT): ${formatCOP(dep.effectiveBenefitsAllowedCOP)} (Recorte Ley 2277: ${formatCOP(dep.benefitsTruncatedByLaw2277COP)})`,
    `   • Base Gravable Depurada: ${formatCOP(dep.taxableBaseCOP)} (${dep.taxableBaseUVT} UVT — ${dep.art383BracketDescription})`,
    `   • Retención Art. 383: ${formatCOP(dep.withholdingArt383COP)} vs Honorarios 11%: ${formatCOP(dep.flatHonorarios11PercentWithholdingCOP)} (Ahorro: ${formatCOP(dep.monthlySavingsArt383VsHonorariosCOP)}/mes)`,
    ``,
    `2. Comparativo Régimen Ordinario vs Régimen Simple (RST):`,
    `   • Carga Total Ordinario (Renta 35% + ICA): ${formatCOP(rst.ordinaryTotalTaxBurdenCOP)} (TET ${rst.ordinaryEffectiveTaxRatePercent}%)`,
    `   • Carga Neta SIMPLE (RST con descuentos pensión + 0.5% medios electrónicos): ${formatCOP(rst.rstNetTaxPayableCOP)} (TET ${rst.rstEffectiveTaxRatePercent}%)`,
    `   • ${rst.executiveVerdict}`,
    `   • Punto de equilibrio de costos/gastos: ${rst.breakEvenCostRatioPercent}%`,
  ].join('\n');

  await navigator.clipboard.writeText(text);
  feedbackMsg.value = 'Dictamen ejecutivo copiado al portapapeles listo para enviar por WhatsApp o Correo al gerente.';
}

async function copyWorkpaperForExcel() {
  if (!planResult.value?.law2277Depuration?.workpaperSteps) return;
  const headers = ['Paso', 'Concepto_Papel_de_Trabajo', 'Norma_Estatuto_Tributario', 'Valor_COP', 'Equivalente_UVT'];
  const rows = planResult.value.law2277Depuration.workpaperSteps.map((s: any) => [
    s.stepCode,
    s.description,
    s.legalReference,
    s.amountCOP,
    s.amountUVT,
  ]);
  await copyRowsAsTsvForExcel(headers, rows);
  feedbackMsg.value = 'Papel de trabajo de depuración Ley 2277 copiado en formato TSV para pegar en Excel.';
}

async function savePlanningWorkpaperInMariaDB() {
  if (!planResult.value) return;
  try {
    const rst = planResult.value.regimeComparison;
    const clientName = props.activeClient?.businessName || 'Estudio Contable';
    await apiRequest('/api/v1/accounting/portfolio/workpapers', {
      method: 'POST',
      body: JSON.stringify({
        clientId: props.activeClient?.id || null,
        workpaperType: 'PLANEACION_LEY2277_RST',
        title: `Estudio Planeación Ley 2277 & Comparativo Ordinario vs RST — ${clientName}`,
        taxPeriod: `${form.year}-ANUAL`,
        executiveSummary: rst.executiveVerdict,
        detectedSavingsOrRiskCop: rst.annualTaxSavingsCOP,
        payloadJson: JSON.stringify(planResult.value),
      }),
    });
    feedbackMsg.value = 'Estudio de Planeación Tributaria Ley 2277 y RST guardado en MariaDB en el expediente del cliente.';
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

onMounted(() => {
  runSimulation();
});
</script>

<template>
  <div class="space-y-6">
    <!-- Barra de Presets y Acciones Consultivas -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-chess-knight text-indigo-400"></i>
          Estudio de Planeación Tributaria: Depurador Ley 2277 (Art. 383/388 E.T.) &amp; Comparador Régimen Ordinario vs SIMPLE (RST)
        </h3>
        <p class="text-xs text-slate-400 mt-0.5">
          Controla los 6 topes simultáneos en UVT de la Ley 2277 (790 UVT, 1.340 UVT, 40%) y compara lado a lado la carga fiscal entre Régimen Ordinario y RST.
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2 text-xs">
        <button @click="applyPreset('DIRECTIVO')" class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-amber-300 font-semibold">
          ⚡ Caso Directivo $22M
        </button>
        <button @click="applyPreset('CONSULTOR')" class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-cyan-300 font-semibold">
          ⚡ Consultor Independiente
        </button>
        <button @click="applyPreset('PYME_COMERCIAL')" class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-emerald-300 font-semibold">
          ⚡ PYME Comercial $1.850M
        </button>
        <button @click="copyExecutiveDictumForClient" class="px-3.5 py-1.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold">
          <i class="fa-solid fa-share-nodes mr-1"></i>Copiar Dictamen para Gerencia
        </button>
        <button @click="savePlanningWorkpaperInMariaDB" class="px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold">
          <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente (MariaDB)
        </button>
      </div>
    </div>

    <div v-if="feedbackMsg" class="bg-emerald-950/40 border border-emerald-500/40 text-emerald-300 text-xs rounded-xl p-3 flex items-center justify-between">
      <span><i class="fa-solid fa-circle-check mr-1.5"></i>{{ feedbackMsg }}</span>
      <button @click="feedbackMsg = ''" class="text-slate-400 hover:text-white">✕</button>
    </div>

    <!-- SECCIÓN A: Depurador Maestro Art. 383 & 388 E.T. (Ley 2277) -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
        <h4 class="text-sm font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-sliders text-amber-400"></i>
          1. Parámetros de Depuración Rentas de Trabajo / Honorarios (Art. 383 y 388 E.T.)
        </h4>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div class="sm:col-span-2">
            <label class="block text-slate-300 font-semibold mb-1">Ingreso Laboral / Honorarios Brutos Mensuales (COP)</label>
            <input v-model.number="form.monthlyGrossIncomeCOP" @input="runSimulation" type="number" step="500000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
          <div>
            <label class="block text-slate-400 mb-1">Intereses Vivienda / Mes (Tope 100 UVT)</label>
            <input v-model.number="form.monthlyHousingInterestCOP" @input="runSimulation" type="number" step="100000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
          <div>
            <label class="block text-slate-400 mb-1">Medicina Prepagada / Mes (Tope 16 UVT)</label>
            <input v-model.number="form.monthlyPrepaidMedicineCOP" @input="runSimulation" type="number" step="50000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
          <div>
            <label class="block text-slate-400 mb-1">N° Dependientes (Tope 32 UVT/mes + 72 UVT/año)</label>
            <input v-model.number="form.dependentsCount" @input="runSimulation" type="number" min="0" max="4" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
          <div>
            <label class="block text-slate-400 mb-1">Aportes Voluntarios AFC / FPV Mes</label>
            <input v-model.number="form.monthlyVoluntaryPensionAfcCOP" @input="runSimulation" type="number" step="100000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
          <div class="sm:col-span-2">
            <label class="block text-slate-400 mb-1">Compras Anuales con Factura Electrónica (1% deducible hasta 240 UVT)</label>
            <input v-model.number="form.annualElectronicInvoicePurchasesCOP" @input="runSimulation" type="number" step="1000000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
        </div>
      </div>

      <!-- Papel de Trabajo Depurado Ley 2277 -->
      <div v-if="planResult?.law2277Depuration" class="lg:col-span-7 bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-indigo-500/15 text-indigo-300 border border-indigo-500/30">
              {{ planResult.law2277Depuration.art383BracketDescription }}
            </span>
            <h4 class="text-sm font-bold text-white mt-1.5">
              Papel de Trabajo Depuración Art. 388 E.T. y Control de Topes Ley 2277
            </h4>
          </div>
          <button @click="copyWorkpaperForExcel" class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-emerald-300 font-semibold text-xs">
            <i class="fa-solid fa-copy mr-1"></i>Copiar Papel a Excel
          </button>
        </div>

        <!-- Medidores de Recorte por Límite Ley 2277 -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs font-mono">
          <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
            <div class="text-slate-400 font-sans text-[11px]">Renta Exenta 25% Aceptada</div>
            <div class="text-sm font-bold text-emerald-400 mt-1">
              {{ formatCOP(planResult.law2277Depuration.exempt25AcceptedCOP) }}
            </div>
            <div class="text-[10px] text-slate-500">
              Tope 790 UVT/año: {{ formatCOP(planResult.law2277Depuration.exempt25MonthlyCap790UvtCOP) }}/mes
            </div>
          </div>

          <div class="bg-slate-950 border border-amber-500/30 rounded-xl p-3">
            <div class="text-amber-300 font-sans text-[11px]">Recorte por Tope 40% / 1.340 UVT</div>
            <div class="text-sm font-bold text-amber-400 mt-1">
              {{ formatCOP(planResult.law2277Depuration.benefitsTruncatedByLaw2277COP) }}
            </div>
            <div class="text-[10px] text-slate-500">
              Máx permitido: {{ formatCOP(planResult.law2277Depuration.effectiveBenefitsAllowedCOP) }}
            </div>
          </div>

          <div class="bg-slate-950 border border-indigo-500/40 rounded-xl p-3">
            <div class="text-indigo-300 font-sans text-[11px]">Retención Art. 383 vs Honorarios 11%</div>
            <div class="text-sm font-black text-white mt-1">
              {{ formatCOP(planResult.law2277Depuration.withholdingArt383COP) }}
              <span class="text-[10px] text-slate-400">vs {{ formatCOP(planResult.law2277Depuration.flatHonorarios11PercentWithholdingCOP) }}</span>
            </div>
            <div class="text-[10px] text-emerald-400">
              Ahorro mensual: {{ formatCOP(planResult.law2277Depuration.monthlySavingsArt383VsHonorariosCOP) }}
            </div>
          </div>
        </div>

        <div class="space-y-1.5 text-xs">
          <div
            v-for="step in planResult.law2277Depuration.workpaperSteps"
            :key="step.stepCode"
            class="bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2 flex items-center justify-between gap-2"
          >
            <div>
              <span class="font-bold text-white">{{ step.stepCode }}. {{ step.description }}</span>
              <span class="text-[10px] text-indigo-400 ml-2 font-mono">({{ step.legalReference }})</span>
            </div>
            <div class="text-right font-mono">
              <span class="font-bold text-emerald-400">{{ formatCOP(step.amountCOP) }}</span>
              <span class="text-[10px] text-slate-500 ml-2">{{ step.amountUVT }} UVT</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- SECCIÓN B: Comparador Estratégico Lado a Lado Régimen Ordinario vs Régimen Simple (RST) -->
    <div v-if="planResult?.regimeComparison" class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <h4 class="text-base font-bold text-white flex items-center gap-2">
        <i class="fa-solid fa-code-compare text-emerald-400"></i>
        2. Simulador Estratégico Lado a Lado: Régimen Ordinario (Art. 240 E.T. 35% + ICA) vs. Régimen Simple de Tributación (RST — Art. 908 E.T.)
      </h4>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 text-xs">
        <div>
          <label class="block text-slate-400 mb-1">Ingresos Brutos Anuales (COP)</label>
          <input v-model.number="form.annualGrossRevenueCOP" @input="runSimulation" type="number" step="50000000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Costos y Gastos Soportados (COP)</label>
          <input v-model.number="form.annualDeductibleCostsAndExpensesCOP" @input="runSimulation" type="number" step="25000000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Aporte Pensión Empleador 12%/Año</label>
          <input v-model.number="form.annualEmployerPensionContributionsCOP" @input="runSimulation" type="number" step="2000000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Recaudos Medios Electrónicos/Año</label>
          <input v-model.number="form.annualElectronicPaymentsReceivedCOP" @input="runSimulation" type="number" step="25000000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Grupo Actividad RST (Art. 908)</label>
          <select v-model="form.rstActivityGroup" @change="runSimulation" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white">
            <option value="GRUPO_1_TIENDAS">Grupo 1: Tiendas / Peluquerías</option>
            <option value="GRUPO_2_COMERCIO_INDUSTRIA">Grupo 2: Comercio / Industria / Técnicos</option>
            <option value="GRUPO_3_RESTAURANTES_TRANSPORTE">Grupo 3: Restaurantes / Transporte</option>
            <option value="GRUPO_4_PROFESIONALES_CONSULTORIA">Grupo 4: Consultoría / Profesiones Liberales</option>
          </select>
        </div>
      </div>

      <!-- Tarjetas Comparativas Lado a Lado -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        <div
          class="rounded-2xl p-4 border space-y-2"
          :class="
            planResult.regimeComparison.recommendedRegime === 'ORDINARIO'
              ? 'bg-emerald-950/20 border-emerald-500/50'
              : 'bg-slate-950 border-slate-800'
          "
        >
          <div class="flex items-center justify-between">
            <span class="font-bold text-white text-sm">A. Régimen Ordinario de Renta (35% + ICA)</span>
            <span class="font-mono text-amber-300 font-bold">
              TET: {{ planResult.regimeComparison.ordinaryEffectiveTaxRatePercent }}%
            </span>
          </div>
          <div class="flex justify-between font-mono text-slate-300">
            <span>Renta Líquida Gravable:</span>
            <span>{{ formatCOP(planResult.regimeComparison.ordinaryNetTaxableIncomeCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono text-slate-300">
            <span>Impuesto de Renta (35%):</span>
            <span>{{ formatCOP(planResult.regimeComparison.ordinaryIncomeTax35PercentCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono text-slate-300">
            <span>ICA Municipal Anual:</span>
            <span>{{ formatCOP(planResult.regimeComparison.ordinaryMunicipalIcaCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono text-base font-black text-white border-t border-slate-800 pt-2">
            <span>Total Carga Ordinario:</span>
            <span>{{ formatCOP(planResult.regimeComparison.ordinaryTotalTaxBurdenCOP) }}</span>
          </div>
        </div>

        <div
          class="rounded-2xl p-4 border space-y-2"
          :class="
            planResult.regimeComparison.recommendedRegime === 'SIMPLE_RST'
              ? 'bg-emerald-950/20 border-emerald-500/50'
              : 'bg-slate-950 border-slate-800'
          "
        >
          <div class="flex items-center justify-between">
            <span class="font-bold text-white text-sm">
              B. Régimen Simple de Tributación — RST ({{ planResult.regimeComparison.rstConsolidatedRatePercent }}%)
            </span>
            <span class="font-mono text-emerald-300 font-bold">
              TET: {{ planResult.regimeComparison.rstEffectiveTaxRatePercent }}%
            </span>
          </div>
          <div class="flex justify-between font-mono text-slate-300">
            <span>Impuesto Unificado Bruto (Incluye ICA):</span>
            <span>{{ formatCOP(planResult.regimeComparison.rstGrossSimpleTaxCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono text-emerald-400">
            <span>(-) Descuento 100% Pensión Empleador:</span>
            <span>-{{ formatCOP(planResult.regimeComparison.rstEmployerPensionCreditCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono text-emerald-400">
            <span>(-) Descuento 0.5% Pagos Electrónicos:</span>
            <span>-{{ formatCOP(planResult.regimeComparison.rstElectronicPayments05PercentCreditCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono text-base font-black text-emerald-400 border-t border-slate-800 pt-2">
            <span>Total Neto a Pagar RST:</span>
            <span>{{ formatCOP(planResult.regimeComparison.rstNetTaxPayableCOP) }}</span>
          </div>
        </div>
      </div>

      <!-- Veredicto Ejecutivo -->
      <div class="bg-emerald-950/30 border border-emerald-500/40 rounded-xl p-4 flex flex-wrap items-center justify-between gap-3 text-xs">
        <div class="text-emerald-200 font-semibold">
          <i class="fa-solid fa-trophy text-amber-400 mr-1.5"></i>
          {{ planResult.regimeComparison.executiveVerdict }}
        </div>
        <div class="font-mono text-amber-300 font-bold">
          Punto de Equilibrio Costos/Gastos: {{ planResult.regimeComparison.breakEvenCostRatioPercent }}%
        </div>
      </div>
    </div>
  </div>
</template>
