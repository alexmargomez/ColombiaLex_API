<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiRequest, formatCOP } from './services/api';
import ClientPortfolioRadarView from './views/ClientPortfolioRadarView.vue';
import PayrollUgppPucView from './views/PayrollUgppPucView.vue';
import ExogenaBatchAuditView from './views/ExogenaBatchAuditView.vue';
import TaxPlanningLaw2277View from './views/TaxPlanningLaw2277View.vue';
import DianSanctionsLiquidatorView from './views/DianSanctionsLiquidatorView.vue';
import ApiPlaygroundDocsView from './views/ApiPlaygroundDocsView.vue';

type TabKey = 'portfolio' | 'payroll' | 'exogena' | 'planning' | 'sanctions' | 'docs';
const activeTab = ref<TabKey>('portfolio');
const healthInfo = ref<any>(null);
const legalParameters = ref<any>(null);
const clientsList = ref<any[]>([]);
const activeClient = ref<any | null>(null);
const preselectedObligationForSanction = ref<any | null>(null);

async function loadInitialContext() {
  try {
    const [health, dash, params] = await Promise.all([
      apiRequest('/api/v1/health').catch(() => null),
      apiRequest('/api/v1/accounting/portfolio/dashboard'),
      apiRequest('/api/v1/accounting/parameters/current?year=2026').catch(() => null),
    ]);
    healthInfo.value = health;
    legalParameters.value = params;
    clientsList.value = dash?.clients || [];
    if (clientsList.value.length > 0 && !activeClient.value) {
      activeClient.value = clientsList.value[0];
    }
  } catch {
    healthInfo.value = null;
  }
}

function handleSelectClient(client: any) {
  activeClient.value = client;
  if (!clientsList.value.find((c) => c.id === client.id)) {
    clientsList.value.push(client);
  }
}

function handleClientCreated(client: any) {
  handleSelectClient(client);
  loadInitialContext();
}

function handleLiquidateObligationSanction(obligation: any) {
  const foundClient = clientsList.value.find((c) => c.id === obligation.clientId);
  if (foundClient) {
    activeClient.value = foundClient;
  }
  preselectedObligationForSanction.value = obligation;
  activeTab.value = 'sanctions';
}

function onClientSelectDropdownChange(event: Event) {
  const id = Number((event.target as HTMLSelectElement).value);
  const found = clientsList.value.find((c) => c.id === id);
  if (found) {
    activeClient.value = found;
  }
}

onMounted(() => {
  loadInitialContext();
});
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
    <!-- Top Workstation Header & Active Client Context Bar -->
    <header class="border-b border-slate-800 bg-slate-900/95 backdrop-blur sticky top-0 z-30">
      <div class="max-w-7xl mx-auto px-4 py-3 flex flex-wrap items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <div
            class="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-400 via-emerald-500 to-indigo-600 flex items-center justify-center shadow-lg"
          >
            <i class="fa-solid fa-scale-balanced text-slate-950 text-lg font-black"></i>
          </div>
          <div>
            <div class="flex items-center gap-2">
              <h1 class="text-base font-black tracking-tight text-white">ALcontable</h1>
              <span
                class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30"
              >
                Estación de Trabajo para Estudios Contables • DIAN &amp; UGPP
              </span>
            </div>
            <p class="text-xs text-slate-400">
              Cartera de Clientes en MariaDB • Auditoría UGPP 40% + Asiento PUC • Pre-Auditor Exógena en Lote • Planeación Ley 2277/RST • Sanciones Art. 640
            </p>
          </div>
        </div>

        <!-- Selector Global de Cliente Activo e Indicadores Normativos -->
        <div class="flex flex-wrap items-center gap-2.5 text-xs">
          <div v-if="clientsList.length > 0" class="flex items-center gap-2 bg-slate-950 border border-amber-500/40 rounded-xl px-3 py-1.5">
            <span class="text-amber-300 font-bold text-[11px]">
              <i class="fa-solid fa-briefcase mr-1"></i>Cliente Activo:
            </span>
            <select
              :value="activeClient?.id"
              @change="onClientSelectDropdownChange"
              class="bg-transparent text-white font-semibold focus:outline-none text-xs"
            >
              <option v-for="c in clientsList" :key="c.id" :value="c.id" class="bg-slate-900 text-white">
                {{ c.businessName }} (NIT {{ c.formattedNit }} • {{ c.taxRegime }})
              </option>
            </select>
          </div>

          <span class="hidden xl:inline-flex px-2.5 py-1 rounded-full bg-slate-950 border border-slate-800 font-mono text-[11px] text-slate-300">
            UVT {{ legalParameters?.year || 2026 }}:
            <strong class="text-amber-300 ml-1">{{ formatCOP(legalParameters?.uvtCOP || 52300) }}</strong>
            • Sanción Mín. 10 UVT:
            <strong class="text-rose-300 ml-1">{{ formatCOP(legalParameters?.minimumSanction10UvtCOP || 523000) }}</strong>
          </span>

          <span
            class="px-2.5 py-1 rounded-full border font-mono text-[11px]"
            :class="
              healthInfo
                ? 'bg-emerald-500/10 text-emerald-300 border-emerald-500/30'
                : 'bg-slate-800 text-slate-400 border-slate-700'
            "
          >
            <i class="fa-solid fa-circle text-[8px] mr-1"></i>
            {{ healthInfo ? healthInfo.service : 'Conectando...' }}
          </span>
        </div>
      </div>

      <!-- Barra de Módulos de la Estación Contable -->
      <div class="max-w-7xl mx-auto px-4 pb-2.5 flex flex-wrap gap-1.5 text-xs">
        <button
          @click="activeTab = 'portfolio'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'portfolio'
              ? 'bg-amber-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-satellite-dish mr-1.5"></i>1. Cartera de Clientes &amp; Radar Vencimientos (BD)
        </button>

        <button
          @click="activeTab = 'payroll'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'payroll'
              ? 'bg-emerald-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-users-gear mr-1.5"></i>2. Nómina en Lote, UGPP 40% &amp; Comprobante PUC
        </button>

        <button
          @click="activeTab = 'exogena'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'exogena'
              ? 'bg-cyan-500 text-slate-950 font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-file-shield mr-1.5"></i>3. Pre-Auditor Exógena en Lote &amp; Retenciones
        </button>

        <button
          @click="activeTab = 'planning'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'planning'
              ? 'bg-indigo-500 text-white font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-chess-knight mr-1.5"></i>4. Planeación Ley 2277 &amp; Ordinario vs SIMPLE
        </button>

        <button
          @click="activeTab = 'sanctions'"
          class="px-3.5 py-2 rounded-xl font-semibold transition"
          :class="
            activeTab === 'sanctions'
              ? 'bg-rose-600 text-white font-bold'
              : 'bg-slate-800/70 text-slate-300 hover:bg-slate-800'
          "
        >
          <i class="fa-solid fa-gavel mr-1.5"></i>5. Liquidador Sanciones Art. 640 &amp; Recibo 490
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

    <!-- Main Workstation Area -->
    <main class="max-w-7xl w-full mx-auto px-4 py-6 flex-1">
      <ClientPortfolioRadarView
        v-if="activeTab === 'portfolio'"
        :activeClientId="activeClient?.id"
        @selectClient="handleSelectClient"
        @client-created="handleClientCreated"
        @liquidateObligationSanction="handleLiquidateObligationSanction"
        @open-sanction="handleLiquidateObligationSanction"
      />
      <PayrollUgppPucView
        v-else-if="activeTab === 'payroll'"
        :activeClient="activeClient"
      />
      <ExogenaBatchAuditView
        v-else-if="activeTab === 'exogena'"
        :activeClient="activeClient"
      />
      <TaxPlanningLaw2277View
        v-else-if="activeTab === 'planning'"
        :activeClient="activeClient"
      />
      <DianSanctionsLiquidatorView
        v-else-if="activeTab === 'sanctions'"
        :activeClient="activeClient"
        :preselectedObligation="preselectedObligationForSanction"
      />
      <ApiPlaygroundDocsView v-else-if="activeTab === 'docs'" />
    </main>
  </div>
</template>
