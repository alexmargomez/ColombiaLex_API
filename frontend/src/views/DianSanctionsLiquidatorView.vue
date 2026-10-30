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
    <div v-if="feedbackMsg" class="bg-emerald-950/40 border border-emerald-500/40 text-emerald-300 text-xs rounded-xl p-3 flex items-center justify-between">
      <span><i class="fa-solid fa-circle-check mr-1.5"></i>{{ feedbackMsg }}</span>
      <button @click="feedbackMsg = ''" class="text-slate-400 hover:text-white">✕</button>
    </div>

    <div v-if="errorMsg" class="bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs rounded-xl p-3">
      {{ errorMsg }}
    </div>

    <!-- MÓDULO 5 PRINCIPAL: Liquidador Forense de Sanciones DIAN (Art. 641, 642, 644, Gradualidad Art. 640) e Intereses Moratorios -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <!-- Panel de Configuración del Caso Sancionatorio -->
      <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-gavel text-rose-400"></i>
            Liquidador Forense de Sanciones DIAN (Art. 641, 642, 644 y Reducción Art. 640 E.T.)
          </h3>
          <p class="text-xs text-slate-400 mt-1">
            Liquida extemporaneidad o corrección, evalúa reducción al 50% o 75% (Art. 640), aplica el candado de Sanción Mínima 10 UVT (Art. 639) y redondea al múltiplo de $1.000 (Art. 868).
          </p>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
          <div>
            <label class="block text-slate-300 font-semibold mb-1">Declaración / Formulario</label>
            <select v-model="sanctionForm.declarationType" @change="liquidateSanction" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white">
              <option value="RETEFUENTE_350">Retención en la Fuente (Form. 350)</option>
              <option value="IVA_300">IVA Bimestral / Cuatrimestral (Form. 300)</option>
              <option value="RENTA_PJ_110">Renta Persona Jurídica (Form. 110)</option>
              <option value="RENTA_PN_210">Renta Persona Natural (Form. 210)</option>
              <option value="ANTICIPO_RST_2593">Anticipo SIMPLE RST (Form. 2593)</option>
            </select>
          </div>

          <div>
            <label class="block text-slate-300 font-semibold mb-1">Conducta Sancionable (Estatuto Tributario)</label>
            <select v-model="sanctionForm.sanctionConduct" @change="liquidateSanction" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white">
              <option value="EXTEMPORANEIDAD_641">Art. 641 — Extemporaneidad Antes de Emplazamiento (5%/mes)</option>
              <option value="EXTEMPORANEIDAD_EMPLAZAMIENTO_642">Art. 642 — Extemporaneidad Post-Emplazamiento (10%/mes)</option>
              <option value="CORRECCION_VOLUNTARIA_644">Art. 644 — Corrección Voluntaria (10%)</option>
              <option value="CORRECCION_EMPLAZAMIENTO_644">Art. 644 — Corrección Post-Emplazamiento (20%)</option>
            </select>
          </div>

          <div>
            <label class="block text-slate-400 mb-1">Fecha de Vencimiento Legal</label>
            <input v-model="sanctionForm.dueDate" @change="liquidateSanction" type="date" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>

          <div>
            <label class="block text-slate-400 mb-1">Fecha Presentación / Pago</label>
            <input v-model="sanctionForm.filingOrPaymentDate" @change="liquidateSanction" type="date" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>

          <div class="sm:col-span-2">
            <label class="block text-slate-300 font-semibold mb-1">Impuesto a Cargo o Mayor Valor por Corrección (COP)</label>
            <input v-model.number="sanctionForm.taxPayableOrDeltaCOP" @input="liquidateSanction" type="number" step="500000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>

          <div>
            <label class="block text-slate-400 mb-1">Ingresos Brutos (Si Impuesto = $0)</label>
            <input v-model.number="sanctionForm.grossIncomePeriodCOP" @input="liquidateSanction" type="number" step="1000000" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>

          <div>
            <label class="block text-slate-400 mb-1">Tasa Usura Superfinanciera (% EA)</label>
            <input v-model.number="sanctionForm.annualUsuryRatePercent" @input="liquidateSanction" type="number" step="0.5" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>

          <div class="sm:col-span-2">
            <label class="block text-amber-300 font-semibold mb-1">Gradualidad y Favorabilidad Art. 640 E.T. (Años sin la misma sanción)</label>
            <select v-model.number="sanctionForm.yearsWithoutSameInfraction" @change="liquidateSanction" class="w-full bg-slate-950 border border-amber-500/40 rounded-xl px-3 py-2 text-amber-300 font-semibold">
              <option :value="2">≥ 2 años sin antecedente → Reduce Sanción al 50% (Art. 640 num. 1)</option>
              <option :value="1">≥ 1 año sin antecedente → Reduce Sanción al 75% (Art. 640 num. 2)</option>
              <option :value="0">Reincidente (&lt; 1 año) → Sanción Plena 100% sin reducción</option>
            </select>
          </div>
        </div>

        <button @click="liquidateSanction" class="w-full py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs transition">
          <i class="fa-solid fa-calculator mr-1.5"></i>Recalcular Sanción, Art. 640 y Recibo 490
        </button>
      </div>

      <!-- Panel Derecho: Comparativa Sanción Plena vs Art. 640, Piso 10 UVT y Recibo Oficial Formulario 490 -->
      <div v-if="sanctionResult" class="lg:col-span-7 space-y-4">
        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-rose-500/15 text-rose-300 border border-rose-500/30 font-mono">
                {{ sanctionResult.calendarDaysOverdue }} días calendario en mora • {{ sanctionResult.monthsOrFractionOfMonthOverdue }} mes/fracción
              </span>
              <h4 class="text-sm font-bold text-white mt-1.5">{{ sanctionResult.legalBasisArticle }}</h4>
            </div>
            <div class="flex items-center gap-2 text-xs">
              <button @click="copySanctionDictum" class="px-3 py-1.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold">
                <i class="fa-solid fa-copy mr-1"></i>Copiar Liquidación
              </button>
              <button @click="saveSanctionWorkpaperInMariaDB" class="px-3 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold">
                <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente
              </button>
            </div>
          </div>

          <!-- Comparativa Lado a Lado: Plena vs Reducida Art. 640 vs Piso 10 UVT -->
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs font-mono">
            <div class="bg-slate-950 border border-slate-800 rounded-xl p-3.5">
              <div class="text-slate-400 font-sans text-[11px]">1. Sanción Plena Preliminar (100%)</div>
              <div class="text-base font-bold text-rose-400 mt-1">
                {{ formatCOP(sanctionResult.preliminaryFullSanctionCOP) }}
              </div>
              <div class="text-[10px] text-slate-500 font-sans mt-0.5">{{ sanctionResult.calculationBaseType }}</div>
            </div>

            <div class="bg-slate-950 border border-amber-500/30 rounded-xl p-3.5">
              <div class="text-amber-300 font-sans text-[11px]">2. Con Gradualidad Art. 640 E.T.</div>
              <div class="text-base font-bold text-amber-400 mt-1">
                {{ formatCOP(sanctionResult.sanctionAfterArt640BeforeFloorCOP) }}
              </div>
              <div class="text-[10px] text-emerald-400 font-sans mt-0.5">
                Ahorro Art. 640: {{ formatCOP(sanctionResult.savingsFromArt640COP) }}
              </div>
            </div>

            <div class="bg-slate-950 border border-indigo-500/40 rounded-xl p-3.5">
              <div class="text-indigo-300 font-sans text-[11px]">3. Piso Legal Sanción Mínima (10 UVT)</div>
              <div class="text-base font-bold text-white mt-1">
                {{ formatCOP(sanctionResult.minimumSanction10UvtRoundedCOP) }}
              </div>
              <div class="text-[10px] font-sans mt-0.5" :class="sanctionResult.minimumSanctionFloorEnforced ? 'text-rose-400 font-bold' : 'text-emerald-400'">
                {{ sanctionResult.minimumSanctionFloorEnforced ? '🔒 Bloqueada en Piso 10 UVT (Art. 639)' : 'Supera piso mínimo 10 UVT' }}
              </div>
            </div>
          </div>

          <!-- Simulador de Renglones Oficiales Formulario 490 DIAN (Redondeado a Múltiplos de $1.000 Art. 868) -->
          <div class="bg-slate-950 border border-emerald-500/30 rounded-2xl p-4 space-y-2.5 text-xs">
            <div class="flex items-center justify-between">
              <span class="font-bold text-emerald-300 uppercase tracking-wide text-[11px]">
                <i class="fa-solid fa-file-invoice-dollar mr-1.5"></i>
                Borrador Renglones Recibo Oficial de Pago en Bancos — Formulario 490 DIAN (Redondeo Art. 868 E.T.)
              </span>
              <span class="text-[11px] font-mono text-slate-400">
                Firmeza Art. 714 E.T.: {{ sanctionResult.generalFirmnessDateArt714ET }}
              </span>
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-4 gap-3 font-mono pt-1">
              <div class="bg-slate-900 rounded-xl p-3 border border-slate-800">
                <div class="text-[10px] text-slate-400 font-sans">Renglón Impuesto / Capital</div>
                <div class="text-sm font-bold text-white mt-1">{{ formatCOP(sanctionResult.form490CapitalRowCOP) }}</div>
              </div>
              <div class="bg-slate-900 rounded-xl p-3 border border-slate-800">
                <div class="text-[10px] text-slate-400 font-sans">Renglón Sanción Oficial</div>
                <div class="text-sm font-bold text-amber-400 mt-1">{{ formatCOP(sanctionResult.form490SanctionRowCOP) }}</div>
              </div>
              <div class="bg-slate-900 rounded-xl p-3 border border-slate-800">
                <div class="text-[10px] text-slate-400 font-sans">
                  Renglón Intereses ({{ sanctionResult.effectiveMoratoryAnnualRatePercent }}% EA)
                </div>
                <div class="text-sm font-bold text-rose-400 mt-1">{{ formatCOP(sanctionResult.form490InterestRowCOP) }}</div>
              </div>
              <div class="bg-emerald-950/30 rounded-xl p-3 border border-emerald-500/40">
                <div class="text-[10px] text-emerald-300 font-sans font-bold">TOTAL PAGO FORMULARIO 490</div>
                <div class="text-base font-black text-emerald-400 mt-1">{{ formatCOP(sanctionResult.form490TotalPaymentCOP) }}</div>
              </div>
            </div>

            <p class="text-[11px] text-slate-300 pt-1">{{ sanctionResult.forensicDictum }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Sub-Módulo Complementario: Tasador de Horas Extra y Recargos con Reducción Gradual Ley 2101 -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h4 class="text-sm font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-clock text-amber-400"></i>
            Tasador Auxiliar de Horas Extra y Recargos según Etapa Ley 2101 de 2021 (Divisor 240h → 235h → 230h → 220h → 210h)
          </h4>
        </div>
        <button @click="calculateOvertime" class="px-3.5 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs">
          Recalcular Horas Extra
        </button>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-6 gap-3 text-xs">
        <div>
          <label class="block text-slate-400 mb-1">Salario Básico (COP)</label>
          <input v-model.number="overtimeForm.monthlySalary" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Fecha Causación</label>
          <input v-model="overtimeForm.calculationDate" type="date" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">HED Diurna (+25%)</label>
          <input v-model.number="overtimeForm.extraDaytimeHours" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">HEN Nocturna (+75%)</label>
          <input v-model.number="overtimeForm.extraNightHours" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Rec. Nocturno (+35%)</label>
          <input v-model.number="overtimeForm.nightSurchargeHours" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Dom/Festiva (+75%)</label>
          <input v-model.number="overtimeForm.sundayHolidayDaytimeHours" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
      </div>

      <div v-if="overtimeResult" class="flex flex-wrap items-center justify-between gap-4 bg-slate-950 border border-slate-800 rounded-xl p-3.5 text-xs font-mono">
        <div>
          <span class="text-amber-300 font-bold">{{ overtimeResult.law2101Stage }}</span>
          <span class="text-slate-400 ml-2">• Divisor: {{ overtimeResult.monthlyHoursDivisor }} h/mes • Hora ordinaria: {{ formatCOP(overtimeResult.ordinaryHourValueCOP) }}</span>
        </div>
        <div class="text-emerald-400 font-bold">
          Total Horas Extra y Recargos: +{{ formatCOP(overtimeResult.totalOvertimeAndSurchargesCOP) }}
        </div>
      </div>
    </div>
  </div>
</template>
