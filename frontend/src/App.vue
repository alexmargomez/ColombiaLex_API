<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiRequest } from './services/api';
import OvertimeCalculatorView from './views/OvertimeCalculatorView.vue';
import ContractSettlementView from './views/ContractSettlementView.vue';
import DianTaxToolsView from './views/DianTaxToolsView.vue';
import EmilianiCalendarView from './views/EmilianiCalendarView.vue';
import DivipolaExplorerView from './views/DivipolaExplorerView.vue';
import ApiPlaygroundDocsView from './views/ApiPlaygroundDocsView.vue';

type TabKey = 'overtime' | 'settlement' | 'tax' | 'calendar' | 'divipola' | 'docs';
const activeTab = ref<TabKey>('overtime');
const healthInfo = ref<any>(null);

onMounted(async () => {
  try {
    healthInfo.value = await apiRequest('/api/v1/health');
  } catch {
    healthInfo.value = null;
  }
});
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
    <!-- Top Header -->
    <header class="border-b border-slate-800 bg-slate-900/90 backdrop-blur sticky top-0 z-30">
      <div class="max-w-7xl mx-auto px-4 py-3.5 flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <div
            class="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-400 via-blue-600 to-rose-600 flex items-center justify-center shadow-lg"
          >
            <i class="fa-solid fa-scale-balanced text-white text-lg"></i>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-base font-black tracking-tight text-white">ColombiaLex API</h1>
              <span
                class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30"
              >
                Java 21 LTS • Spring Boot 3.3 • DDD Hexagonal
              </span>
            </div>
            <p class="text-xs text-slate-400">
              Motor Normativo Laboral (CST + Ley 2101), Tributario (DIAN Módulo 11 / UVT), Festivos
              (Ley Emiliani) &amp; DANE DIVIPOLA
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2 text-xs">
          <span
            class="px-3 py-1 rounded-full border font-mono text-[11px]"
            :class="
              healthInfo
                ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                : 'bg-slate-800 text-slate-400 border-slate-700'
            "
          >
            <i class="fa-solid fa-circle text-[8px] mr-1.5"></i>
            {{ healthInfo ? healthInfo.runtime : 'Conectando Backend Java 21...' }}
          </span>
        </div>
      </div>

      <!-- Navigation Tabs -->
      <div class="max-w-7xl mx-auto px-4 pb-2 flex flex-wrap gap-1.5 text-xs">
        <button
          @click="activeTab = 'overtime'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'overtime'
              ? 'bg-amber-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-clock mr-1.5"></i>1. Horas Extra &amp; Ley 2101
        </button>

        <button
          @click="activeTab = 'settlement'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'settlement'
              ? 'bg-emerald-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-file-invoice-dollar mr-1.5"></i>2. Liquidación, Cesantías &amp; Art. 64
        </button>

        <button
          @click="activeTab = 'tax'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'tax'
              ? 'bg-indigo-500 text-white font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-fingerprint mr-1.5"></i>3. DIAN NIT Módulo 11, UVT &amp; Retefuente
        </button>

        <button
          @click="activeTab = 'calendar'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'calendar'
              ? 'bg-amber-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-calendar-days mr-1.5"></i>4. Festivos Ley Emiliani &amp; Días Hábiles
        </button>

        <button
          @click="activeTab = 'divipola'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'divipola'
              ? 'bg-cyan-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-map-location-dot mr-1.5"></i>5. DANE / DIVIPOLA
        </button>

        <button
          @click="activeTab = 'docs'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'docs'
              ? 'bg-white text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-code mr-1.5"></i>6. API REST &amp; Swagger
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="max-w-7xl w-full mx-auto px-4 py-6 flex-1">
      <OvertimeCalculatorView v-if="activeTab === 'overtime'" />
      <ContractSettlementView v-else-if="activeTab === 'settlement'" />
      <DianTaxToolsView v-else-if="activeTab === 'tax'" />
      <EmilianiCalendarView v-else-if="activeTab === 'calendar'" />
      <DivipolaExplorerView v-else-if="activeTab === 'divipola'" />
      <ApiPlaygroundDocsView v-else-if="activeTab === 'docs'" />
    </main>
  </div>
</template>
