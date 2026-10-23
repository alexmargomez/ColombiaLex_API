<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { apiRequest } from '../services/api';

const selectedYear = ref(2026);
const holidaysData = ref<any>(null);

const addDaysForm = reactive({
  startDate: '2026-03-18',
  days: 15,
  includeSaturdays: false,
});
const addDaysResult = ref<any>(null);

async function loadHolidays() {
  holidaysData.value = await apiRequest(`/api/v1/calendar/holidays/${selectedYear.value}`);
}

async function calcBusinessDays() {
  addDaysResult.value = await apiRequest(
    `/api/v1/calendar/business-days/add?startDate=${addDaysForm.startDate}&days=${addDaysForm.days}&includeSaturdays=${addDaysForm.includeSaturdays}`
  );
}

onMounted(() => {
  loadHolidays();
  calcBusinessDays();
});
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Calculadora de Términos en Días Hábiles -->
    <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-calendar-check text-emerald-400"></i>
          Calculadora de Términos Legales en Días Hábiles
        </h3>
        <p class="text-xs text-slate-400 mt-1">
          Ideal para vencimientos de PQRSD (15 días hábiles), tutelas, recursos administrativos y
          facturas comerciales en Colombia.
        </p>
      </div>

      <div class="space-y-3 text-xs">
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Fecha de Radicación / Inicio</label>
          <input
            v-model="addDaysForm.startDate"
            type="date"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-300 font-semibold mb-1">Días Hábiles a Sumar</label>
          <input
            v-model.number="addDaysForm.days"
            type="number"
            min="1"
            max="365"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <label class="flex items-center gap-2 cursor-pointer text-slate-300 pt-1">
          <input v-model="addDaysForm.includeSaturdays" type="checkbox" class="accent-emerald-500" />
          <span>Contar los Sábados como día hábil</span>
        </label>

        <button
          @click="calcBusinessDays"
          class="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition"
        >
          Calcular Fecha Exacta de Vencimiento
        </button>
      </div>

      <div v-if="addDaysResult" class="bg-slate-950 border border-emerald-500/30 rounded-xl p-4 space-y-2 text-xs">
        <div class="text-slate-400">Fecha Límite / Vencimiento Hábiles:</div>
        <div class="text-2xl font-black text-emerald-400 font-mono">
          {{ addDaysResult.resultingDueDate }}
        </div>
        <div class="text-[11px] text-slate-400">
          Transcurren <strong>{{ addDaysResult.totalCalendarDaysElapsed }}</strong> días calendario y se
          saltaron <strong>{{ addDaysResult.holidaysEncountered.length }}</strong> festivos oficiales.
        </div>
      </div>
    </div>

    <!-- Calendario de los 18 Festivos Oficiales por Año (Ley Emiliani) -->
    <div class="lg:col-span-7 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-sun text-amber-400"></i>
            Calendario Oficial de Festivos de Colombia (Ley 51 de 1983 — Ley Emiliani)
          </h3>
          <p v-if="holidaysData" class="text-xs text-slate-400">
            Domingo de Pascua calculado: <strong class="text-amber-300 font-mono">{{ holidaysData.easterSunday }}</strong> •
            {{ holidaysData.totalHolidays }} festivos nacionales.
          </p>
        </div>

        <select
          v-model.number="selectedYear"
          @change="loadHolidays"
          class="bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-xs text-white font-mono"
        >
          <option v-for="y in [2024, 2025, 2026, 2027, 2028, 2029, 2030]" :key="y" :value="y">
            Año {{ y }}
          </option>
        </select>
      </div>

      <div v-if="holidaysData" class="grid grid-cols-1 sm:grid-cols-2 gap-2.5 text-xs">
        <div
          v-for="h in holidaysData.holidays"
          :key="h.observedDate + h.name"
          class="bg-slate-950 border border-slate-800 rounded-xl px-3.5 py-2.5 flex items-center justify-between gap-2"
        >
          <div>
            <div class="font-bold text-white">{{ h.name }}</div>
            <div class="text-[10px] text-slate-400">
              {{ h.dayOfWeekNameEs }} •
              <span v-if="h.movedByEmilianiLaw" class="text-amber-300">
                Trasladado Ley Emiliani (Orig: {{ h.originalHistoricalDate }})
              </span>
              <span v-else class="text-slate-500">Fecha Fija / Litúrgica</span>
            </div>
          </div>
          <span class="px-2.5 py-1 rounded-lg bg-slate-900 border border-slate-700 font-mono font-bold text-amber-400">
            {{ h.observedDate }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
