<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { apiRequest, formatCOP } from '../services/api';

const loading = ref(false);
const errorMsg = ref('');
const result = ref<any>(null);

const form = reactive({
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

async function calculate() {
  loading.value = true;
  errorMsg.value = '';
  try {
    result.value = await apiRequest('/api/v1/labor/overtime', {
      method: 'POST',
      body: JSON.stringify(form),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  calculate();
});
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Panel de Entrada -->
    <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-clock text-amber-400"></i>
          Parámetros de Jornada y Horas Laboradas
        </h3>
        <p class="text-xs text-slate-400 mt-1">
          El motor aplica automáticamente el divisor mensual de la <strong>Ley 2101 de 2021</strong>
          (48h → 47h → 46h → 44h → 42h semanales) según la fecha seleccionada.
        </p>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
        <div class="sm:col-span-2">
          <label class="block text-slate-300 font-semibold mb-1">Salario Básico Mensual (COP)</label>
          <input
            v-model.number="form.monthlySalary"
            type="number"
            min="100000"
            step="50000"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono text-sm"
          />
        </div>

        <div class="sm:col-span-2">
          <label class="block text-slate-300 font-semibold mb-1">
            Fecha de Causación (Determina Etapa Ley 2101)
          </label>
          <input
            v-model="form.calculationDate"
            type="date"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">H. Extra Diurna (HED +25%)</label>
          <input
            v-model.number="form.extraDaytimeHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">H. Extra Nocturna (HEN +75%)</label>
          <input
            v-model.number="form.extraNightHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Recargo Nocturno Ord. (RN +35%)</label>
          <input
            v-model.number="form.nightSurchargeHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Dom/Festiva Diurna (+75%)</label>
          <input
            v-model.number="form.sundayHolidayDaytimeHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Dom/Festiva Nocturna (+110%)</label>
          <input
            v-model.number="form.sundayHolidayNightHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Extra Dom/Fest Diurna (+100%)</label>
          <input
            v-model.number="form.extraSundayHolidayDaytimeHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div class="sm:col-span-2">
          <label class="block text-slate-400 mb-1">Extra Dom/Fest Nocturna (HENDF +150%)</label>
          <input
            v-model.number="form.extraSundayHolidayNightHours"
            type="number"
            min="0"
            step="0.5"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>
      </div>

      <button
        @click="calculate"
        :disabled="loading"
        class="w-full py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition"
      >
        <i class="fa-solid fa-calculator mr-1.5"></i>
        {{ loading ? 'Calculando...' : 'Calcular Horas Extra y Recargos (Ley 2101)' }}
      </button>

      <div v-if="errorMsg" class="text-xs text-rose-400 bg-rose-950/30 border border-rose-500/30 rounded-xl p-3">
        {{ errorMsg }}
      </div>
    </div>

    <!-- Panel de Resultados -->
    <div v-if="result" class="lg:col-span-7 space-y-4">
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30">
              {{ result.law2101Stage }}
            </span>
            <h4 class="text-sm font-bold text-white mt-2">
              Desglose Matemático de Horas Extra y Recargos (CST Art. 168 &amp; 179)
            </h4>
          </div>
          <div class="text-right">
            <div class="text-[11px] text-slate-400">Valor Hora Ordinaria</div>
            <div class="text-lg font-black text-amber-400 font-mono">
              {{ formatCOP(result.ordinaryHourValueCOP) }}
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div class="bg-slate-950 border border-slate-800 rounded-xl p-3.5">
            <div class="text-[11px] text-slate-400">Salario Base Mensual</div>
            <div class="text-base font-bold text-white font-mono mt-1">
              {{ formatCOP(result.monthlySalaryCOP) }}
            </div>
            <div class="text-[10px] text-slate-500 mt-0.5">
              Divisor: {{ result.monthlyHoursDivisor }} h/mes ({{ result.maxWeeklyHours }} h/sem)
            </div>
          </div>

          <div class="bg-slate-950 border border-slate-800 rounded-xl p-3.5">
            <div class="text-[11px] text-slate-400">Total Horas Extra / Recargos</div>
            <div class="text-base font-bold text-emerald-400 font-mono mt-1">
              +{{ formatCOP(result.totalOvertimeAndSurchargesCOP) }}
            </div>
            <div class="text-[10px] text-slate-500 mt-0.5">
              {{ result.totalHoursReported }} horas reportadas en el periodo
            </div>
          </div>

          <div class="bg-slate-950 border border-emerald-500/30 rounded-xl p-3.5">
            <div class="text-[11px] text-emerald-300">Total Devengado (Salario + Extras)</div>
            <div class="text-lg font-black text-emerald-400 font-mono mt-1">
              {{ formatCOP(result.totalSalaryPlusOvertimeCOP) }}
            </div>
          </div>
        </div>

        <div class="space-y-2">
          <div
            v-for="item in result.breakdown"
            :key="item.code"
            class="bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-xs"
            :class="item.quantityHours > 0 ? 'border-amber-500/30 bg-amber-950/10' : 'opacity-65'"
          >
            <div>
              <div class="flex items-center gap-2">
                <span class="px-2 py-0.5 rounded bg-slate-800 text-amber-300 font-mono font-bold text-[11px]">
                  {{ item.code }} ({{ item.percentageSurcharge }})
                </span>
                <span class="font-semibold text-white">{{ item.concept }}</span>
              </div>
              <div class="text-[11px] text-slate-400 font-mono mt-1">
                {{ item.formula }} • <span class="text-slate-500">{{ item.legalBasis }}</span>
              </div>
            </div>
            <div class="text-right font-mono">
              <div class="text-sm font-bold text-emerald-400">{{ formatCOP(item.totalCOP) }}</div>
              <div class="text-[10px] text-slate-500">{{ formatCOP(item.unitHourValueCOP) }}/hora</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
