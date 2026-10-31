<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { apiRequest, formatCOP } from '../services/api';

const props = defineProps<{
  activeClient?: any | null;
  preselectedObligation?: any | null;
}>();

const loading = ref(false);
const errorMsg = ref('');
const feedbackMsg = ref('');
const sanctionResult = ref<any>(null);

const sanctionForm = reactive({
  year: 2026,
  declarationType: 'RETEFUENTE_350',
  sanctionConduct: 'EXTEMPORANEIDAD_641',
  dueDate: '2026-10-11',
  filingOrPaymentDate: '2026-10-29',
  taxPayableOrDeltaCOP: 14850000,
  grossIncomePeriodCOP: 0,
  netWorthPreviousYearCOP: 0,
  voluntaryLiquidationByTaxpayer: true,
  yearsWithoutSameInfraction: 2,
  annualUsuryRatePercent: 28.5,
});

// Asistente complementario de Horas Extra Ley 2101
const overtimeForm = reactive({
  monthlySalary: 2500000,
  calculationDate: '2026-08-15',
  extraDaytimeHours: 6,
  extraNightHours: 3,
  nightSurchargeHours: 8,
  sundayHolidayDaytimeHours: 8,
  sundayHolidayNightHours: 0,
  extraSundayHolidayDaytimeHours: 2,
  extraSundayHolidayNightHours: 0,
});
const overtimeResult = ref<any>(null);

watch(
  () => props.preselectedObligation,
  (ob) => {
    if (ob) {
      sanctionForm.declarationType = ob.declarationType || 'RETEFUENTE_350';
      sanctionForm.dueDate = ob.dueDate || '2026-10-11';
      sanctionForm.filingOrPaymentDate = new Date().toISOString().slice(0, 10);
      sanctionForm.taxPayableOrDeltaCOP = Number(ob.estimatedTaxCop || 10000000);
      feedbackMsg.value = `Obligación vencida de ${ob.clientBusinessName} (${ob.declarationType} - ${ob.taxPeriod}) cargada automáticamente desde el Radar.`;
      liquidateSanction();
    }
  },
  { immediate: true }
);

async function liquidateSanction() {
  loading.value = true;
  errorMsg.value = '';
  try {
    sanctionResult.value = await apiRequest('/api/v1/accounting/sanctions/liquidate', {
      method: 'POST',
      body: JSON.stringify(sanctionForm),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function copySanctionDictum() {
  if (!sanctionResult.value) return;
  const r = sanctionResult.value;
  const text = [
    `⚖️ LIQUIDACIÓN FORENSE DE SANCIÓN E INTERESES DIAN (ALcontable)`,
    `• Declaración: ${r.declarationType} (${r.legalBasisArticle})`,
    `• Vencimiento: ${r.dueDate} → Pago: ${r.filingOrPaymentDate} (${r.calendarDaysOverdue} días mora / ${r.monthsOrFractionOfMonthOverdue} fracción mes)`,
    `• Sanción Plena (100%): ${formatCOP(r.preliminaryFullSanctionCOP)}`,
    `• Gradualidad Aplicada: ${r.art640GradualityApplied}`,
    `• Control Piso Sanción Mínima 10 UVT (Art. 639): ${r.minimumSanctionFloorEnforced ? 'SÍ APLICÓ PISO 10 UVT' : 'Supera piso mínimo'}`,
    `• Renglón Capital Impuesto (Form 490): ${formatCOP(r.form490CapitalRowCOP)}`,
    `• Renglón Sanción Redondeada (Art. 868): ${formatCOP(r.form490SanctionRowCOP)}`,
    `• Renglón Intereses Mora (${r.effectiveMoratoryAnnualRatePercent}% EA): ${formatCOP(r.form490InterestRowCOP)}`,
    `• TOTAL A PAGAR RECIBO 490 DIAN: ${formatCOP(r.form490TotalPaymentCOP)}`,
  ].join('\n');
  await navigator.clipboard.writeText(text);
  feedbackMsg.value = 'Liquidación forense de sanción e intereses copiada al portapapeles lista para enviar al cliente.';
}

async function saveSanctionWorkpaperInMariaDB() {
  if (!sanctionResult.value) return;
  try {
    const r = sanctionResult.value;
    const clientName = props.activeClient?.businessName || 'Estudio Contable';
    await apiRequest('/api/v1/accounting/portfolio/workpapers', {
      method: 'POST',
      body: JSON.stringify({
        clientId: props.activeClient?.id || null,
        workpaperType: 'LIQUIDACION_SANCIONES_DIAN',
        title: `Liquidación Sanción ${r.declarationType} + Art. 640 — ${clientName}`,
        taxPeriod: `${r.year}`,
        executiveSummary: r.forensicDictum,
        detectedSavingsOrRiskCop: r.form490TotalPaymentCOP,
        payloadJson: JSON.stringify(r),
      }),
    });
    feedbackMsg.value = 'Liquidación de Sanción e Intereses DIAN guardada en MariaDB en el expediente del cliente.';
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

async function calculateOvertime() {
  try {
    overtimeResult.value = await apiRequest('/api/v1/labor/overtime', {
      method: 'POST',
      body: JSON.stringify(overtimeForm),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

onMounted(() => {
  liquidateSanction();
  calculateOvertime();
});
</script>

<template>
  <div class="space-y-6">
    <div
      v-if="feedbackMsg"
      class="bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-lg p-3 flex items-center justify-between"
    >
      <span class="font-medium"><i class="fa-solid fa-circle-check text-emerald-600 mr-1.5"></i>{{ feedbackMsg }}</span>
      <button @click="feedbackMsg = ''" class="text-emerald-700 hover:text-emerald-950 font-bold">✕</button>
    </div>

    <div v-if="errorMsg" class="bg-red-50 border border-red-200 text-red-800 text-xs rounded-lg p-3 font-medium">
      <i class="fa-solid fa-triangle-exclamation text-red-600 mr-1.5"></i>{{ errorMsg }}
    </div>

    <!-- MÓDULO 5 PRINCIPAL: Liquidador Forense de Sanciones DIAN (Art. 641, 642, 644, Gradualidad Art. 640) e Intereses Moratorios -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- Panel de Configuración del Caso Sancionatorio -->
      <div class="lg:col-span-5 bg-white border border-slate-200 border-t-4 border-t-blue-700 rounded-xl p-5 shadow-xs space-y-4">
        <div class="border-b border-slate-100 pb-3">
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
              PT-05A
            </span>
            <h3 class="text-base font-bold text-slate-900">
              Liquidador Forense de Sanciones DIAN (Art. 641, 642, 644 y Gradualidad Art. 640 E.T.)
            </h3>
          </div>
          <p class="text-xs text-slate-500 mt-1">
            Liquida extemporaneidad o corrección, evalúa reducción al 50% o 75% (Art. 640), aplica el piso mínimo de 10 UVT (Art. 639) y redondea al múltiplo de $1.000 (Art. 868).
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div>
            <label class="block text-slate-700 font-semibold mb-1">Declaración / Formulario</label>
            <select
              v-model="sanctionForm.declarationType"
              @change="liquidateSanction"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-medium focus:outline-none focus:border-blue-600"
            >
              <option value="RETEFUENTE_350">Retención en la Fuente (Form. 350)</option>
              <option value="IVA_300">IVA Bimestral / Cuatrimestral (Form. 300)</option>
              <option value="RENTA_PJ_110">Renta Persona Jurídica (Form. 110)</option>
              <option value="RENTA_PN_210">Renta Persona Natural (Form. 210)</option>
              <option value="ANTICIPO_RST_2593">Anticipo SIMPLE RST (Form. 2593)</option>
            </select>
          </div>

          <div>
            <label class="block text-slate-700 font-semibold mb-1">Conducta Sancionable (Estatuto Tributario)</label>
            <select
              v-model="sanctionForm.sanctionConduct"
              @change="liquidateSanction"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-medium focus:outline-none focus:border-blue-600"
            >
              <option value="EXTEMPORANEIDAD_641">Art. 641 — Extemporaneidad Antes de Emplazamiento (5%/mes)</option>
              <option value="EXTEMPORANEIDAD_EMPLAZAMIENTO_642">Art. 642 — Extemporaneidad Post-Emplazamiento (10%/mes)</option>
              <option value="CORRECCION_VOLUNTARIA_644">Art. 644 — Corrección Voluntaria (10%)</option>
              <option value="CORRECCION_EMPLAZAMIENTO_644">Art. 644 — Corrección Post-Emplazamiento (20%)</option>
            </select>
          </div>

          <div>
            <label class="block text-slate-600 font-medium mb-1">Fecha de Vencimiento Legal</label>
            <input
              v-model="sanctionForm.dueDate"
              @change="liquidateSanction"
              type="date"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono focus:outline-none focus:border-blue-600"
            />
          </div>

          <div>
            <label class="block text-slate-600 font-medium mb-1">Fecha Presentación / Pago</label>
            <input
              v-model="sanctionForm.filingOrPaymentDate"
              @change="liquidateSanction"
              type="date"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono focus:outline-none focus:border-blue-600"
            />
          </div>

          <div class="sm:col-span-2">
            <label class="block text-slate-700 font-semibold mb-1">Impuesto a Cargo o Mayor Valor por Corrección (COP)</label>
            <input
              v-model.number="sanctionForm.taxPayableOrDeltaCOP"
              @input="liquidateSanction"
              type="number"
              step="500000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>

          <div>
            <label class="block text-slate-600 font-medium mb-1">Ingresos Brutos (Si Impuesto = $0)</label>
            <input
              v-model.number="sanctionForm.grossIncomePeriodCOP"
              @input="liquidateSanction"
              type="number"
              step="1000000"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>

          <div>
            <label class="block text-slate-600 font-medium mb-1">Tasa Usura Superfinanciera (% EA)</label>
            <input
              v-model.number="sanctionForm.annualUsuryRatePercent"
              @input="liquidateSanction"
              type="number"
              step="0.5"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>

          <div class="sm:col-span-2">
            <label class="block text-slate-700 font-semibold mb-1">Gradualidad y Favorabilidad Art. 640 E.T. (Años sin la misma sanción)</label>
            <select
              v-model.number="sanctionForm.yearsWithoutSameInfraction"
              @change="liquidateSanction"
              class="w-full bg-blue-50/50 border border-blue-300 rounded-lg px-3 py-2 text-blue-950 font-semibold focus:outline-none focus:border-blue-600"
            >
              <option :value="2">≥ 2 años sin antecedente → Reduce Sanción al 50% (Art. 640 num. 1)</option>
              <option :value="1">≥ 1 año sin antecedente → Reduce Sanción al 75% (Art. 640 num. 2)</option>
              <option :value="0">Reincidente (&lt; 1 año) → Sanción Plena 100% sin reducción</option>
            </select>
          </div>
        </div>

        <button
          @click="liquidateSanction"
          class="w-full py-2.5 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs transition shadow-2xs"
        >
          <i class="fa-solid fa-calculator mr-1.5"></i>Recalcular Sanción, Art. 640 y Recibo 490
        </button>
      </div>

      <!-- Panel Derecho: Comparativa Sanción Plena vs Art. 640, Piso 10 UVT y Recibo Oficial Formulario 490 -->
      <div v-if="sanctionResult" class="lg:col-span-7 space-y-4">
        <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
          <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
            <div>
              <span
                class="px-2.5 py-0.5 rounded-full text-[11px] font-bold border font-mono"
                :class="
                  sanctionResult.calendarDaysOverdue > 0
                    ? 'bg-red-50 text-red-800 border-red-200'
                    : 'bg-emerald-50 text-emerald-800 border-emerald-200'
                "
              >
                {{
                  sanctionResult.calendarDaysOverdue > 0
                    ? `${sanctionResult.calendarDaysOverdue} días calendario en mora • ${sanctionResult.monthsOrFractionOfMonthOverdue} mes/fracción`
                    : 'Presentación Oportuna (0 días en mora)'
                }}
              </span>
              <h4 class="text-sm font-bold text-slate-900 mt-1.5">{{ sanctionResult.legalBasisArticle }}</h4>
            </div>
            <div class="flex items-center gap-2 text-xs">
              <button
                @click="copySanctionDictum"
                class="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
              >
                <i class="fa-solid fa-copy text-blue-700 mr-1"></i>Copiar Liquidación
              </button>
              <button
                @click="saveSanctionWorkpaperInMariaDB"
                class="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-900 text-white font-semibold transition"
              >
                <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente
              </button>
            </div>
          </div>

          <!-- Comparativa Lado a Lado: Plena vs Reducida Art. 640 vs Piso 10 UVT -->
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs font-mono tabular-nums">
            <div class="bg-slate-50 border border-slate-200 rounded-lg p-3.5">
              <div class="text-slate-600 font-sans text-[11px] font-semibold">1. Sanción Plena Preliminar (100%)</div>
              <div class="text-base font-extrabold text-slate-900 mt-1">
                {{ formatCOP(sanctionResult.preliminaryFullSanctionCOP) }}
              </div>
              <div class="text-[10px] text-slate-500 font-sans mt-0.5">{{ sanctionResult.calculationBaseType }}</div>
            </div>

            <div class="bg-amber-50/70 border border-amber-200 rounded-lg p-3.5">
              <div class="text-amber-900 font-sans text-[11px] font-semibold">2. Con Gradualidad Art. 640 E.T.</div>
              <div class="text-base font-extrabold text-amber-800 mt-1">
                {{ formatCOP(sanctionResult.sanctionAfterArt640BeforeFloorCOP) }}
              </div>
              <div class="text-[10px] text-emerald-700 font-sans font-bold mt-0.5">
                Ahorro Art. 640: {{ formatCOP(sanctionResult.savingsFromArt640COP) }}
              </div>
            </div>

            <div class="bg-slate-50 border border-slate-200 rounded-lg p-3.5">
              <div class="text-slate-600 font-sans text-[11px] font-semibold">3. Piso Sanción Mínima (10 UVT)</div>
              <div class="text-base font-extrabold text-slate-900 mt-1">
                {{ formatCOP(sanctionResult.minimumSanction10UvtRoundedCOP) }}
              </div>
              <div
                class="text-[10px] font-sans mt-0.5 font-bold"
                :class="sanctionResult.minimumSanctionFloorEnforced ? 'text-red-700' : 'text-emerald-700'"
              >
                {{
                  sanctionResult.minimumSanctionFloorEnforced
                    ? '🔒 Bloqueada en Piso 10 UVT (Art. 639)'
                    : 'Supera piso mínimo 10 UVT'
                }}
              </div>
            </div>
          </div>

          <!-- Formato Institucional Oficial DIAN: Recibo Oficial de Pago en Bancos Formulario 490 -->
          <div class="border-2 border-[#004830] rounded-xl overflow-hidden bg-white shadow-xs">
            <div class="bg-[#004830] text-white px-4 py-2.5 flex flex-wrap items-center justify-between gap-2 text-xs">
              <div class="font-bold tracking-wide uppercase flex items-center gap-2">
                <i class="fa-solid fa-building-columns"></i>
                <span>DIAN • Formulario 490 — Recibo Oficial de Pago Tributos en Bancos (Art. 868 E.T.)</span>
              </div>
              <span class="font-mono text-[11px] text-emerald-100">
                Firmeza Art. 714 E.T.: {{ sanctionResult.generalFirmnessDateArt714ET }}
              </span>
            </div>

            <div class="p-4 space-y-3 text-xs">
              <div class="grid grid-cols-1 sm:grid-cols-4 gap-3 font-mono tabular-nums">
                <div class="bg-slate-50 rounded-lg p-3 border border-slate-300">
                  <div class="text-[10px] text-slate-600 font-sans font-semibold flex justify-between">
                    <span>Pago de Impuesto</span>
                    <span class="font-mono font-bold text-slate-800">Casilla 41</span>
                  </div>
                  <div class="text-sm font-bold text-slate-900 text-right mt-1.5">
                    {{ formatCOP(sanctionResult.form490CapitalRowCOP) }}
                  </div>
                </div>
                <div class="bg-slate-50 rounded-lg p-3 border border-slate-300">
                  <div class="text-[10px] text-slate-600 font-sans font-semibold flex justify-between">
                    <span>Pago de Sanciones</span>
                    <span class="font-mono font-bold text-slate-800">Casilla 42</span>
                  </div>
                  <div class="text-sm font-bold text-amber-800 text-right mt-1.5">
                    {{ formatCOP(sanctionResult.form490SanctionRowCOP) }}
                  </div>
                </div>
                <div class="bg-slate-50 rounded-lg p-3 border border-slate-300">
                  <div class="text-[10px] text-slate-600 font-sans font-semibold flex justify-between">
                    <span>Intereses ({{ sanctionResult.effectiveMoratoryAnnualRatePercent }}%)</span>
                    <span class="font-mono font-bold text-slate-800">Casilla 43</span>
                  </div>
                  <div class="text-sm font-bold text-red-700 text-right mt-1.5">
                    {{ formatCOP(sanctionResult.form490InterestRowCOP) }}
                  </div>
                </div>
                <div class="bg-emerald-50 rounded-lg p-3 border-2 border-[#004830]">
                  <div class="text-[10px] text-[#004830] font-sans font-extrabold flex justify-between">
                    <span>TOTAL A PAGAR</span>
                    <span class="font-mono font-bold">Casilla 48</span>
                  </div>
                  <div class="text-base font-extrabold text-[#004830] text-right mt-1.5">
                    {{ formatCOP(sanctionResult.form490TotalPaymentCOP) }}
                  </div>
                </div>
              </div>

              <div class="bg-slate-50 border border-slate-200 rounded-lg p-3 text-[11px] text-slate-700 leading-relaxed">
                <strong class="text-slate-900">Dictamen Forense:</strong> {{ sanctionResult.forensicDictum }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Sub-Módulo Complementario: Tasador de Horas Extra y Recargos con Reducción Gradual Ley 2101 -->
    <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
        <div class="flex items-center gap-2">
          <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-300 font-mono text-[10px] font-bold">
            ANEXO LEY 2101
          </span>
          <h4 class="text-sm font-bold text-slate-900">
            Tasador de Horas Extra y Recargos según Etapa Ley 2101 de 2021 (Divisor 240h → 235h → 230h → 220h → 210h)
          </h4>
        </div>
        <button
          @click="calculateOvertime"
          class="px-3.5 py-1.5 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs shadow-2xs"
        >
          Recalcular Horas Extra
        </button>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-6 gap-3 text-xs">
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Salario Básico (COP)</label>
          <input
            v-model.number="overtimeForm.monthlySalary"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Fecha Causación</label>
          <input
            v-model="overtimeForm.calculationDate"
            type="date"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">HED Diurna (+25%)</label>
          <input
            v-model.number="overtimeForm.extraDaytimeHours"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">HEN Nocturna (+75%)</label>
          <input
            v-model.number="overtimeForm.extraNightHours"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Rec. Nocturno (+35%)</label>
          <input
            v-model.number="overtimeForm.nightSurchargeHours"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Dom/Festiva (+75%)</label>
          <input
            v-model.number="overtimeForm.sundayHolidayDaytimeHours"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
      </div>

      <div
        v-if="overtimeResult"
        class="flex flex-wrap items-center justify-between gap-4 bg-slate-50 border border-slate-200 rounded-lg p-3.5 text-xs font-mono tabular-nums"
      >
        <div>
          <span class="text-blue-800 font-bold">{{ overtimeResult.law2101Stage }}</span>
          <span class="text-slate-600 ml-2">
            • Divisor: {{ overtimeResult.monthlyHoursDivisor }} h/mes • Hora ordinaria: {{ formatCOP(overtimeResult.ordinaryHourValueCOP) }}
          </span>
        </div>
        <div class="text-emerald-800 font-extrabold">
          Total Horas Extra y Recargos: +{{ formatCOP(overtimeResult.totalOvertimeAndSurchargesCOP) }}
        </div>
      </div>
    </div>
  </div>
</template>
