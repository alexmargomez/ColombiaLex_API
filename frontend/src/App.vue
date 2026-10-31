<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  apiRequest,
  clearStoredAccountantSession,
  formatCOP,
  getStoredAccountantSession,
  type AccountantUserSession,
} from './services/api';
import ApiPlaygroundDocsView from './views/ApiPlaygroundDocsView.vue';
import ClientPortfolioRadarView from './views/ClientPortfolioRadarView.vue';
import PayrollUgppPucView from './views/PayrollUgppPucView.vue';
import ExogenaBatchAuditView from './views/ExogenaBatchAuditView.vue';
import TaxPlanningLaw2277View from './views/TaxPlanningLaw2277View.vue';
import DianSanctionsLiquidatorView from './views/DianSanctionsLiquidatorView.vue';

type TabKey = 'index' | 'portfolio' | 'payroll' | 'exogena' | 'planning' | 'sanctions';

const currentUser = ref<AccountantUserSession | null>(getStoredAccountantSession());
const activeTab = ref<TabKey>(currentUser.value ? 'portfolio' : 'index');
const authRequiredNotice = ref<string | null>(null);

const healthInfo = ref<any>(null);
const legalParameters = ref<any>(null);
const clientsList = ref<any[]>([]);
const activeClient = ref<any | null>(null);
const preselectedObligationForSanction = ref<any | null>(null);

const navItems: Array<{
  key: TabKey;
  code: string;
  label: string;
  subtitle: string;
  icon: string;
  requiresAuth: boolean;
}> = [
  {
    key: 'index',
    code: 'INDEX',
    label: 'Portada & Consultas Rápidas',
    subtitle: 'Herramientas Libres y Acceso CPA',
    icon: 'fa-solid fa-house-laptop',
    requiresAuth: false,
  },
  {
    key: 'portfolio',
    code: 'PT-01',
    label: 'Cartera & Vencimientos',
    subtitle: 'Radar DIAN Privado por Contador',
    icon: 'fa-solid fa-building-columns',
    requiresAuth: true,
  },
  {
    key: 'payroll',
    code: 'PT-02',
    label: 'Nómina UGPP & PUC',
    subtitle: 'Tope 40% Ley 1393 y Partida Doble',
    icon: 'fa-solid fa-file-invoice-dollar',
    requiresAuth: true,
  },
  {
    key: 'exogena',
    code: 'PT-03',
    label: 'Auditoría Exógena 1001',
    subtitle: 'Módulo 11, DANE y Pre-Form. 350',
    icon: 'fa-solid fa-table-list',
    requiresAuth: true,
  },
  {
    key: 'planning',
    code: 'PT-04',
    label: 'Planeación Ley 2277',
    subtitle: 'Art. 383/388 y Ordinario vs RST',
    icon: 'fa-solid fa-scale-balanced',
    requiresAuth: true,
  },
  {
    key: 'sanctions',
    code: 'PT-05',
    label: 'Sanciones & Recibo 490',
    subtitle: 'Art. 641/642, Gradualidad 640 y Mora',
    icon: 'fa-solid fa-gavel',
    requiresAuth: true,
  },
];

const currentModuleMeta = computed(() => {
  return navItems.find((item) => item.key === activeTab.value) || navItems[0];
});

function selectNavTab(item: (typeof navItems)[number]) {
  if (item.requiresAuth && !currentUser.value) {
    activeTab.value = 'index';
    authRequiredNotice.value = `El módulo ${item.code} (${item.label}) hace parte de la estación privada de cada Contador. Inicie sesión, regístrese o ingrese con 1 clic a la Firma Contable Demo.`;
    return;
  }
  authRequiredNotice.value = null;
  activeTab.value = item.key;
}

async function loadInitialContext() {
  try {
    const [health, params] = await Promise.all([
      apiRequest('/api/v1/health').catch(() => null),
      apiRequest('/api/v1/accounting/parameters/current?year=2026').catch(() => null),
    ]);
    healthInfo.value = health;
    legalParameters.value = params;

    if (currentUser.value) {
      const dash = await apiRequest('/api/v1/accounting/portfolio/dashboard');
      clientsList.value = dash?.clients || [];
      if (clientsList.value.length > 0) {
        const stillExists = activeClient.value
          ? clientsList.value.find((c: any) => c.id === activeClient.value.id)
          : null;
        activeClient.value = stillExists || clientsList.value[0];
      } else {
        activeClient.value = null;
      }
    } else {
      clientsList.value = [];
      activeClient.value = null;
    }
  } catch {
    healthInfo.value = null;
  }
}

async function handleAccountantAuthenticated(session: AccountantUserSession) {
  currentUser.value = session;
  authRequiredNotice.value = null;
  activeClient.value = null;
  await loadInitialContext();
  activeTab.value = 'portfolio';
}

function handleEnterWorkstation(target?: string) {
  if (!currentUser.value) return;
  if (target === 'payroll-ugpp') {
    activeTab.value = 'payroll';
  } else if (target === 'exogena-batch') {
    activeTab.value = 'exogena';
  } else {
    activeTab.value = 'portfolio';
  }
}

function handleLogout() {
  clearStoredAccountantSession();
  currentUser.value = null;
  clientsList.value = [];
  activeClient.value = null;
  authRequiredNotice.value = null;
  activeTab.value = 'index';
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
  <div class="min-h-screen bg-slate-100 text-slate-800 flex flex-col lg:flex-row">
    <!-- Barra Lateral Ejecutiva ERP Contable (Sidebar) -->
    <aside class="w-full lg:w-72 bg-slate-900 text-slate-200 flex flex-col border-b lg:border-b-0 lg:border-r border-slate-800 shrink-0">
      <!-- Identidad Institucional -->
      <div class="px-5 py-4 border-b border-slate-800 flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-blue-700 text-white flex items-center justify-center shadow-sm shrink-0">
          <i class="fa-solid fa-landmark text-base"></i>
        </div>
        <div>
          <div class="flex items-center gap-2">
            <span class="text-base font-extrabold tracking-tight text-white">ALcontable</span>
            <span class="px-1.5 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider bg-blue-900/80 text-blue-200 border border-blue-700/60">
              ERP Suite
            </span>
          </div>
          <p class="text-[11px] text-slate-400">Estación de Auditoría Tributaria &amp; Contable</p>
        </div>
      </div>

      <!-- Tarjeta de Sesión del Contador Público en Sidebar -->
      <div v-if="currentUser" class="mx-4 mt-4 p-3 rounded-lg bg-slate-800/90 border border-slate-700/80 text-xs space-y-1.5">
        <div class="flex items-center justify-between text-[10px] uppercase tracking-wider text-emerald-400 font-bold">
          <span><i class="fa-solid fa-user-check mr-1"></i>Contador Activo</span>
          <span class="font-mono text-slate-300">{{ currentUser.professionalCardNumber }}</span>
        </div>
        <div class="font-bold text-white truncate" :title="currentUser.fullName">
          {{ currentUser.fullName }}
        </div>
        <div class="text-[11px] text-blue-300 truncate" :title="currentUser.firmName">
          {{ currentUser.firmName }}
        </div>
        <div class="pt-1.5 border-t border-slate-700/80 flex items-center justify-between">
          <span class="text-[10px] font-mono text-slate-400">{{ clientsList.length }} cliente(s) en cartera</span>
          <button
            type="button"
            @click="handleLogout"
            class="text-[11px] font-semibold text-red-300 hover:text-red-200 transition cursor-pointer"
          >
            <i class="fa-solid fa-power-off mr-1"></i>Salir
          </button>
        </div>
      </div>

      <div v-else class="mx-4 mt-4 p-3 rounded-lg bg-slate-800/60 border border-slate-700/70 text-xs space-y-1.5">
        <div class="flex items-center justify-between text-[10px] uppercase tracking-wider text-amber-300 font-bold">
          <span><i class="fa-solid fa-unlock-keyhole mr-1"></i>Modo Portada Pública</span>
          <span class="font-mono text-slate-400">SIN SESIÓN</span>
        </div>
        <p class="text-[11px] text-slate-300 leading-snug">
          Use las herramientas rápidas libres o inicie sesión para abrir su cartera privada de clientes.
        </p>
      </div>

      <!-- Ficha Resumen de Empresa Activa en Sidebar -->
      <div v-if="currentUser && activeClient" class="mx-4 mt-2.5 p-3 rounded-lg bg-slate-800/90 border border-slate-700/80 text-xs space-y-1">
        <div class="flex items-center justify-between text-[10px] uppercase tracking-wider text-slate-400 font-semibold">
          <span>Expediente Activo</span>
          <span class="px-1.5 py-0.5 rounded bg-blue-950 text-blue-300 border border-blue-800 font-mono">
            {{ activeClient.taxRegime }}
          </span>
        </div>
        <div class="font-bold text-white truncate" :title="activeClient.businessName">
          {{ activeClient.businessName }}
        </div>
        <div class="font-mono text-[11px] text-slate-300 flex items-center justify-between">
          <span>NIT {{ activeClient.formattedNit }}</span>
          <span class="text-slate-400">Dígito #{{ activeClient.lastNitDigit }}</span>
        </div>
      </div>

      <!-- Menú de Portada + 5 Módulos Privados del Contador -->
      <nav class="p-3 space-y-1 flex-1">
        <div class="px-2 pb-1.5 text-[10px] font-bold uppercase tracking-wider text-slate-400">
          Navegación &amp; Papeles de Trabajo
        </div>
        <button
          v-for="item in navItems"
          :key="item.key"
          @click="selectNavTab(item)"
          class="w-full text-left px-3 py-2.5 rounded-lg transition flex items-start gap-3 group cursor-pointer"
          :class="
            activeTab === item.key
              ? 'bg-blue-700 text-white shadow-sm'
              : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
          "
        >
          <div
            class="w-7 h-7 rounded-md flex items-center justify-center text-xs mt-0.5 shrink-0"
            :class="
              activeTab === item.key
                ? 'bg-blue-800 text-white'
                : 'bg-slate-800 text-slate-400 group-hover:text-slate-200'
            "
          >
            <i :class="item.icon"></i>
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center justify-between gap-1">
              <span class="text-xs font-semibold truncate">{{ item.label }}</span>
              <span
                class="font-mono text-[10px] px-1.5 py-0.2 rounded flex items-center gap-1"
                :class="
                  activeTab === item.key
                    ? 'bg-blue-800 text-blue-100 font-bold'
                    : 'bg-slate-800 text-slate-400'
                "
              >
                <i v-if="item.requiresAuth && !currentUser" class="fa-solid fa-lock text-[8px]"></i>
                {{ item.code }}
              </span>
            </div>
            <p
              class="text-[11px] truncate mt-0.5"
              :class="activeTab === item.key ? 'text-blue-100' : 'text-slate-400'"
            >
              {{ item.subtitle }}
            </p>
          </div>
        </button>
      </nav>

      <!-- Pie Institucional de Sidebar -->
      <div class="p-4 border-t border-slate-800 text-[11px] text-slate-400 space-y-1.5">
        <div class="flex items-center justify-between">
          <span>Estándar Papeles:</span>
          <span class="font-mono text-slate-200 font-semibold">NIA 230 • Decreto 2650</span>
        </div>
        <div class="flex items-center justify-between">
          <span>Marco Normativo:</span>
          <span class="font-mono text-slate-300">E.T. • Ley 2277 • UGPP</span>
        </div>
      </div>
    </aside>

    <!-- Área Derecha: Barra Superior de Contexto + Lienzo de Papeles de Trabajo -->
    <div class="flex-1 flex flex-col min-w-0">
      <!-- Topbar Ejecutiva Blanca -->
      <header class="bg-white border-b border-slate-200 sticky top-0 z-20 shadow-xs">
        <div class="max-w-7xl mx-auto px-6 py-3 flex flex-wrap items-center justify-between gap-4">
          <div class="flex items-center gap-3">
            <span class="px-2.5 py-1 rounded-md bg-blue-50 text-blue-800 border border-blue-200 font-mono text-xs font-bold">
              {{ currentModuleMeta.code }}
            </span>
            <div>
              <h1 class="text-sm font-bold text-slate-900">{{ currentModuleMeta.label }}</h1>
              <p class="text-xs text-slate-500">{{ currentModuleMeta.subtitle }}</p>
            </div>
          </div>

          <!-- Selector Global de Cliente Activo e Indicadores Normativos Oficiales -->
          <div class="flex flex-wrap items-center gap-3 text-xs">
            <div
              v-if="currentUser && clientsList.length > 0"
              class="flex items-center gap-2 bg-slate-50 border border-slate-300 rounded-lg px-3 py-1.5"
            >
              <span class="text-slate-600 font-semibold text-[11px]">
                <i class="fa-solid fa-building text-blue-700 mr-1"></i>Cliente:
              </span>
              <select
                :value="activeClient?.id"
                @change="onClientSelectDropdownChange"
                class="bg-transparent text-slate-900 font-bold focus:outline-none text-xs cursor-pointer"
              >
                <option v-for="c in clientsList" :key="c.id" :value="c.id" class="bg-white text-slate-900">
                  {{ c.businessName }} (NIT {{ c.formattedNit }} • {{ c.taxRegime }})
                </option>
              </select>
            </div>

            <div class="hidden xl:flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-50 border border-slate-200 font-mono text-[11px] text-slate-700">
              <span>
                UVT {{ legalParameters?.year || 2026 }}:
                <strong class="text-slate-900 font-bold">{{ formatCOP(legalParameters?.uvtCOP || 52294) }}</strong>
              </span>
              <span class="text-slate-300">|</span>
              <span>
                Sanción Mín. 10 UVT:
                <strong class="text-red-700 font-bold">{{ formatCOP(legalParameters?.minimumSanction10UvtCOP || 523000) }}</strong>
              </span>
            </div>

            <span
              class="px-2.5 py-1 rounded-full border font-mono text-[11px] font-medium"
              :class="
                healthInfo
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                  : 'bg-slate-100 text-slate-600 border-slate-300'
              "
            >
              <i
                class="fa-solid fa-circle text-[7px] mr-1"
                :class="healthInfo ? 'text-emerald-600' : 'text-slate-400'"
              ></i>
              {{ healthInfo ? 'Motor Contable Activo' : 'Conectando...' }}
            </span>
          </div>
        </div>
      </header>

      <!-- Lienzo Principal de Papeles de Trabajo (Gris Papel Contable #F1F5F9) -->
      <main class="max-w-7xl w-full mx-auto px-6 py-6 flex-1 space-y-4">
        <div
          v-if="authRequiredNotice"
          class="p-4 rounded-xl bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center justify-between gap-4"
        >
          <div class="flex items-center gap-2.5">
            <i class="fa-solid fa-lock text-amber-700 text-sm"></i>
            <span class="font-semibold">{{ authRequiredNotice }}</span>
          </div>
          <button
            type="button"
            @click="authRequiredNotice = null"
            class="text-amber-700 hover:text-amber-900 font-bold cursor-pointer"
          >
            ✕
          </button>
        </div>

        <ApiPlaygroundDocsView
          v-if="activeTab === 'index' || !currentUser"
          :currentUser="currentUser"
          :taxParams="legalParameters"
          @authenticated="handleAccountantAuthenticated"
          @enterWorkstation="handleEnterWorkstation"
        />
        <ClientPortfolioRadarView
          v-else-if="activeTab === 'portfolio'"
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
      </main>
    </div>
  </div>
</template>

