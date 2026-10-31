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
    <!-- Cabecera del Papel de Trabajo PT-04 -->
    <div class="bg-white border border-slate-200 border-t-4 border-t-blue-700 rounded-xl p-5 shadow-xs flex flex-wrap items-center justify-between gap-4">
      <div>
        <div class="flex items-center gap-2">
          <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
            PT-04
          </span>
          <h3 class="text-base font-bold text-slate-900">
            Estudio de Planeación Tributaria: Depurador Ley 2277 (Art. 383/388 E.T.) &amp; Comparador Ordinario vs SIMPLE (RST)
          </h3>
        </div>
        <p class="text-xs text-slate-500 mt-0.5">
          Controla los 6 topes simultáneos en UVT de la Ley 2277 (790 UVT, 1.340 UVT y 40%) y compara lado a lado la Tasa Efectiva de Tributación (TET).
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2 text-xs">
        <button
          @click="applyPreset('DIRECTIVO')"
          class="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
        >
          Caso Directivo $22M
        </button>
        <button
          @click="applyPreset('CONSULTOR')"
          class="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
        >
          Consultor Independiente
        </button>
        <button
          @click="applyPreset('PYME_COMERCIAL')"
          class="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
        >
          PYME Comercial $1.850M
        </button>
        <button
          @click="copyExecutiveDictumForClient"
          class="px-3.5 py-1.5 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold transition shadow-2xs"
        >
          <i class="fa-solid fa-file-signature mr-1"></i>Copiar Dictamen Ejecutivo
        </button>
        <button
          @click="savePlanningWorkpaperInMariaDB"
          class="px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-900 text-white font-semibold transition"
        >
          <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente (MariaDB)
        </button>
      </div>
    </div>

    <div
      v-if="feedbackMsg"
      class="bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-lg p-3 flex items-center justify-between"
    >
      <span class="font-medium"><i class="fa-solid fa-circle-check text-emerald-600 mr-1.5"></i>{{ feedbackMsg }}</span>
      <button @click="feedbackMsg = ''" class="text-emerald-700 hover:text-emerald-950 font-bold">✕</button>
    </div>

    <!-- SECCIÓN A: Depurador Maestro Art. 383 & 388 E.T. (Ley 2277) -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <div class="lg:col-span-5 bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div class="flex items-center gap-2 border-b border-slate-100 pb-2.5">
          <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
            PT-04A
          </span>
          <h4 class="text-sm font-bold text-slate-900">
            1. Parámetros Rentas de Trabajo / Honorarios (Art. 383 y 388 E.T.)
          </h4>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div class="sm:col-span-2">
            <label class="block text-slate-700 font-semibold mb-1">Ingreso Laboral / Honorarios Brutos Mensuales (COP)</label>
            <input
              v-model.number="form.monthlyGrossIncomeCOP"
              @input="runSimulation"
              type="number"
              step="500000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
          <div>
            <label class="block text-slate-600 font-medium mb-1">Intereses Vivienda / Mes (100 UVT)</label>
            <input
              v-model.number="form.monthlyHousingInterestCOP"
              @input="runSimulation"
              type="number"
              step="100000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
          <div>
            <label class="block text-slate-600 font-medium mb-1">Medicina Prepagada / Mes (16 UVT)</label>
            <input
              v-model.number="form.monthlyPrepaidMedicineCOP"
              @input="runSimulation"
              type="number"
              step="50000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
          <div>
            <label class="block text-slate-600 font-medium mb-1">N° Dependientes (Máx 4)</label>
            <input
              v-model.number="form.dependentsCount"
              @input="runSimulation"
              type="number"
              min="0"
              max="4"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
          <div>
            <label class="block text-slate-600 font-medium mb-1">Aportes Voluntarios AFC / FPV Mes</label>
            <input
              v-model.number="form.monthlyVoluntaryPensionAfcCOP"
              @input="runSimulation"
              type="number"
              step="100000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
          <div class="sm:col-span-2">
            <label class="block text-slate-600 font-medium mb-1">Compras Anuales Factura Electrónica (1% hasta 240 UVT)</label>
            <input
              v-model.number="form.annualElectronicInvoicePurchasesCOP"
              @input="runSimulation"
              type="number"
              step="1000000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
          <div class="sm:col-span-2 pt-1">
            <label class="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-slate-700 font-medium cursor-pointer">
              <input
                v-model="form.independentContractorWithCosts"
                @change="runSimulation"
                type="checkbox"
                class="accent-blue-700"
              />
              <span>Independiente imputa costos y gastos (renuncia al 25% de renta exenta — Art. 206 num. 10 E.T.)</span>
            </label>
          </div>
        </div>
      </div>

      <!-- Papel de Trabajo Depurado Ley 2277 -->
      <div v-if="planResult?.law2277Depuration" class="lg:col-span-7 bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
          <div>
            <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-50 text-blue-800 border border-blue-200 font-mono">
              {{ planResult.law2277Depuration.art383BracketDescription }}
            </span>
            <h4 class="text-sm font-bold text-slate-900 mt-1.5">
              Papel de Trabajo Depuración Art. 388 E.T. y Control de Topes Ley 2277
            </h4>
          </div>
          <button
            @click="copyWorkpaperForExcel"
            class="px-3 py-1.5 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white font-semibold text-xs transition"
          >
            <i class="fa-solid fa-table mr-1"></i>Copiar Papel a Excel
          </button>
        </div>

        <!-- Medidores de Recorte por Límite Ley 2277 -->
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs font-mono tabular-nums">
          <div class="bg-slate-50 border border-slate-200 rounded-lg p-3">
            <div class="text-slate-600 font-sans text-[11px] font-semibold">Renta Exenta 25% Aceptada</div>
            <div class="text-sm font-extrabold text-slate-900 mt-1">
              {{ formatCOP(planResult.law2277Depuration.exempt25AcceptedCOP) }}
            </div>
            <div class="text-[10px] text-slate-500 mt-0.5">
              Tope 790 UVT/año: {{ formatCOP(planResult.law2277Depuration.exempt25MonthlyCap790UvtCOP) }}/mes
            </div>
          </div>

          <div class="bg-amber-50/70 border border-amber-200 rounded-lg p-3">
            <div class="text-amber-900 font-sans text-[11px] font-semibold">Recorte por Tope 40% / 1.340 UVT</div>
            <div class="text-sm font-extrabold text-amber-800 mt-1">
              {{ formatCOP(planResult.law2277Depuration.benefitsTruncatedByLaw2277COP) }}
            </div>
            <div class="text-[10px] text-amber-800/80 mt-0.5">
              Máx permitido: {{ formatCOP(planResult.law2277Depuration.effectiveBenefitsAllowedCOP) }}
            </div>
          </div>

          <div class="bg-blue-50/70 border border-blue-200 rounded-lg p-3">
            <div class="text-blue-900 font-sans text-[11px] font-semibold">Retención Art. 383 vs Honorarios 11%</div>
            <div class="text-sm font-extrabold text-blue-950 mt-1">
              {{ formatCOP(planResult.law2277Depuration.withholdingArt383COP) }}
              <span class="text-[10px] text-slate-500 font-normal">
                vs {{ formatCOP(planResult.law2277Depuration.flatHonorarios11PercentWithholdingCOP) }}
              </span>
            </div>
            <div class="text-[10px] text-emerald-700 font-bold mt-0.5">
              Ahorro mensual: {{ formatCOP(planResult.law2277Depuration.monthlySavingsArt383VsHonorariosCOP) }}
            </div>
          </div>
        </div>

        <!-- Tabla Contable de Renglones de Depuración -->
        <div class="overflow-x-auto border border-slate-200 rounded-lg">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-100 border-b-2 border-slate-300 text-slate-700 uppercase text-[11px] font-bold tracking-wider">
                <th class="py-2 px-3">Renglón</th>
                <th class="py-2 px-3">Concepto del Papel de Trabajo</th>
                <th class="py-2 px-3">Soporte Estatuto Tributario</th>
                <th class="py-2 px-3 text-right">Valor Depurado (COP)</th>
                <th class="py-2 px-3 text-right">Equivalente UVT</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white">
              <tr
                v-for="step in planResult.law2277Depuration.workpaperSteps"
                :key="step.stepCode"
                class="even:bg-slate-50/60 hover:bg-blue-50/40"
              >
                <td class="py-2 px-3 font-mono font-bold text-blue-800">{{ step.stepCode }}</td>
                <td class="py-2 px-3 font-semibold text-slate-900">{{ step.description }}</td>
                <td class="py-2 px-3 font-mono text-[11px] text-slate-600">{{ step.legalReference }}</td>
                <td class="py-2 px-3 text-right font-mono tabular-nums font-bold text-slate-900">
                  {{ formatCOP(step.amountCOP) }}
                </td>
                <td class="py-2 px-3 text-right font-mono tabular-nums text-slate-600">
                  {{ step.amountUVT }} UVT
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- SECCIÓN B: Comparador Estratégico Lado a Lado Régimen Ordinario vs Régimen Simple (RST) -->
    <div v-if="planResult?.regimeComparison" class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex items-center gap-2 border-b border-slate-100 pb-3">
        <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
          PT-04B
        </span>
        <h4 class="text-base font-bold text-slate-900">
          2. Comparativo Estratégico Lado a Lado: Régimen Ordinario (Art. 240 E.T. 35% + ICA) vs. Régimen Simple de Tributación (RST — Art. 908 E.T.)
        </h4>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3 text-xs">
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Ingresos Brutos Anuales (COP)</label>
          <input
            v-model.number="form.annualGrossRevenueCOP"
            @input="runSimulation"
            type="number"
            step="50000000"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Costos y Gastos Soportados (COP)</label>
          <input
            v-model.number="form.annualDeductibleCostsAndExpensesCOP"
            @input="runSimulation"
            type="number"
            step="25000000"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Aporte Pensión Empleador 12%/Año</label>
          <input
            v-model.number="form.annualEmployerPensionContributionsCOP"
            @input="runSimulation"
            type="number"
            step="2000000"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Recaudos Medios Electrónicos/Año</label>
          <input
            v-model.number="form.annualElectronicPaymentsReceivedCOP"
            @input="runSimulation"
            type="number"
            step="25000000"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Grupo Actividad RST (Art. 908)</label>
          <select
            v-model="form.rstActivityGroup"
            @change="runSimulation"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-medium"
          >
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
          class="rounded-xl p-4 border space-y-2.5"
          :class="
            planResult.regimeComparison.recommendedRegime === 'ORDINARIO'
              ? 'bg-blue-50/40 border-2 border-blue-700 shadow-xs'
              : 'bg-slate-50 border-slate-200'
          "
        >
          <div class="flex items-center justify-between border-b border-slate-200 pb-2">
            <div>
              <span class="font-bold text-slate-900 text-sm">A. Régimen Ordinario de Renta (35% + ICA)</span>
              <span
                v-if="planResult.regimeComparison.recommendedRegime === 'ORDINARIO'"
                class="ml-2 px-2 py-0.5 rounded bg-blue-700 text-white text-[10px] font-bold uppercase"
              >
                Recomendado
              </span>
            </div>
            <span class="font-mono tabular-nums text-slate-800 font-bold bg-white px-2 py-0.5 rounded border border-slate-200">
              TET: {{ planResult.regimeComparison.ordinaryEffectiveTaxRatePercent }}%
            </span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-slate-700">
            <span class="font-sans">Renta Líquida Gravable:</span>
            <span class="font-semibold text-slate-900">{{ formatCOP(planResult.regimeComparison.ordinaryNetTaxableIncomeCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-slate-700">
            <span class="font-sans">Impuesto de Renta (35%):</span>
            <span class="font-semibold text-slate-900">{{ formatCOP(planResult.regimeComparison.ordinaryIncomeTax35PercentCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-slate-700">
            <span class="font-sans">ICA Municipal Anual:</span>
            <span class="font-semibold text-slate-900">{{ formatCOP(planResult.regimeComparison.ordinaryMunicipalIcaCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-base font-extrabold text-slate-900 border-t-2 border-b-4 border-double border-slate-700 py-2">
            <span class="font-sans text-sm">Total Carga Ordinario:</span>
            <span>{{ formatCOP(planResult.regimeComparison.ordinaryTotalTaxBurdenCOP) }}</span>
          </div>
        </div>

        <div
          class="rounded-xl p-4 border space-y-2.5"
          :class="
            planResult.regimeComparison.recommendedRegime === 'SIMPLE_RST'
              ? 'bg-blue-50/40 border-2 border-blue-700 shadow-xs'
              : 'bg-slate-50 border-slate-200'
          "
        >
          <div class="flex items-center justify-between border-b border-slate-200 pb-2">
            <div>
              <span class="font-bold text-slate-900 text-sm">
                B. Régimen Simple — RST ({{ planResult.regimeComparison.rstConsolidatedRatePercent }}%)
              </span>
              <span
                v-if="planResult.regimeComparison.recommendedRegime === 'SIMPLE_RST'"
                class="ml-2 px-2 py-0.5 rounded bg-blue-700 text-white text-[10px] font-bold uppercase"
              >
                Recomendado
              </span>
            </div>
            <span class="font-mono tabular-nums text-blue-900 font-bold bg-white px-2 py-0.5 rounded border border-blue-200">
              TET: {{ planResult.regimeComparison.rstEffectiveTaxRatePercent }}%
            </span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-slate-700">
            <span class="font-sans">Impuesto Unificado Bruto (Incluye ICA):</span>
            <span class="font-semibold text-slate-900">{{ formatCOP(planResult.regimeComparison.rstGrossSimpleTaxCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-emerald-800">
            <span class="font-sans">(-) Descuento 100% Pensión Empleador:</span>
            <span class="font-semibold">-{{ formatCOP(planResult.regimeComparison.rstEmployerPensionCreditCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-emerald-800">
            <span class="font-sans">(-) Descuento 0.5% Pagos Electrónicos:</span>
            <span class="font-semibold">-{{ formatCOP(planResult.regimeComparison.rstElectronicPayments05PercentCreditCOP) }}</span>
          </div>
          <div class="flex justify-between font-mono tabular-nums text-base font-extrabold text-blue-950 border-t-2 border-b-4 border-double border-slate-700 py-2">
            <span class="font-sans text-sm">Total Neto a Pagar RST:</span>
            <span>{{ formatCOP(planResult.regimeComparison.rstNetTaxPayableCOP) }}</span>
          </div>
        </div>
      </div>

      <!-- Veredicto Ejecutivo -->
      <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4 flex flex-wrap items-center justify-between gap-3 text-xs">
        <div class="text-emerald-950 font-semibold">
          <i class="fa-solid fa-Scale-balanced text-emerald-700 mr-1.5"></i>
          {{ planResult.regimeComparison.executiveVerdict }}
        </div>
        <div class="font-mono tabular-nums text-slate-800 font-bold bg-white px-3 py-1 rounded border border-emerald-200">
          Punto de Equilibrio Costos/Gastos: {{ planResult.regimeComparison.breakEvenCostRatioPercent }}%
        </div>
      </div>
    </div>
  </div>
</template>
