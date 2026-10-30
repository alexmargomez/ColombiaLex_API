<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { apiRequest, copyRowsAsTsvForExcel, exportRowsToCsv, formatCOP } from '../services/api';

const props = defineProps<{
  activeClientId?: number | null;
}>();

const emit = defineEmits<{
  (e: 'selectClient', client: any): void;
  (e: 'client-created', client: any): void;
  (e: 'liquidateObligationSanction', obligation: any): void;
  (e: 'open-sanction', obligation: any): void;
}>();

const loading = ref(false);
const errorMsg = ref('');
const feedbackMsg = ref('');
const dashboard = ref<any>(null);
const filterUrgency = ref<string>('ALL');
const searchTerm = ref<string>('');
const showNewClientForm = ref(false);

const newClient = reactive({
  nit: '',
  businessName: '',
  personType: 'JURIDICA',
  taxRegime: 'ORDINARIO',
  ivaPeriodicity: 'BIMESTRAL',
  dianResponsibilities: '05,07,14,48,52',
  municipalityDaneCode: '11001',
  municipalityName: 'BOGOTÁ, D.C.',
  estimatedAnnualIncomeCop: 1450000000,
  employeesCount: 12,
  appliesArt1141Exemption: true,
});

// Calculadora auxiliar de Términos Procesales DIAN (Días Hábiles Ley Emiliani)
const bdaysForm = reactive({
  startDate: '2026-10-29',
  days: 15,
  includeSaturdays: false,
});
const bdaysResult = ref<any>(null);

async function loadDashboard() {
  loading.value = true;
  errorMsg.value = '';
  try {
    dashboard.value = await apiRequest('/api/v1/accounting/portfolio/dashboard');
    if (dashboard.value?.clients?.length && !props.activeClientId) {
      emit('selectClient', dashboard.value.clients[0]);
    }
  } catch (e: any) {
    errorMsg.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function createNewClient() {
  errorMsg.value = '';
  feedbackMsg.value = '';
  try {
    const created = await apiRequest('/api/v1/accounting/portfolio/clients', {
      method: 'POST',
      body: JSON.stringify(newClient),
    });
    feedbackMsg.value = `Cliente ${created.businessName} (${created.formattedNit}) creado con calendario tributario automático en MariaDB.`;
    showNewClientForm.value = false;
    newClient.nit = '';
    newClient.businessName = '';
    await loadDashboard();
    emit('selectClient', created);
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

async function changeObligationStatus(obligationId: number, status: string) {
  try {
    await apiRequest(`/api/v1/accounting/portfolio/obligations/${obligationId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
    await loadDashboard();
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

async function calculateBusinessDays() {
  try {
    bdaysResult.value = await apiRequest(
      `/api/v1/calendar/business-days/add?startDate=${bdaysForm.startDate}&days=${bdaysForm.days}&includeSaturdays=${bdaysForm.includeSaturdays}`
    );
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

const filteredObligations = computed(() => {
  const list: any[] = dashboard.value?.obligations || [];
  return list.filter((ob) => {
    const matchesUrgency = filterUrgency.value === 'ALL' || ob.urgencyLevel === filterUrgency.value;
    const q = searchTerm.value.trim().toLowerCase();
    const matchesSearch =
      !q ||
      ob.clientBusinessName.toLowerCase().includes(q) ||
      ob.clientFormattedNit.toLowerCase().includes(q) ||
      ob.declarationType.toLowerCase().includes(q);
    return matchesUrgency && matchesSearch;
  });
});

function exportObligationsCsv() {
  const headers = [
    'NIT',
    'Ultimo_Digito',
    'Cliente',
    'Regimen',
    'Formulario',
    'Obligacion',
    'Periodo',
    'Fecha_Vencimiento',
    'Estado',
    'Impuesto_Estimado_COP',
    'Riesgo_Sancion_COP',
  ];
  const rows = filteredObligations.value.map((o: any) => [
    o.clientFormattedNit,
    o.lastNitDigit,
    o.clientBusinessName,
    o.clientTaxRegime,
    o.formCode,
    o.declarationType,
    o.taxPeriod,
    o.dueDate,
    o.status,
    o.estimatedTaxCop,
    o.calculatedSanctionCop,
  ]);
  exportRowsToCsv('radar_obligaciones_alcontable.csv', headers, rows);
}

async function copyObligationsTsv() {
  const headers = ['NIT', 'Cliente', 'Formulario', 'Periodo', 'Vencimiento', 'Estado', 'Impuesto_COP', 'Sancion_COP'];
  const rows = filteredObligations.value.map((o: any) => [
    o.clientFormattedNit,
    o.clientBusinessName,
    o.declarationType,
    o.taxPeriod,
    o.dueDate,
    o.status,
    o.estimatedTaxCop,
    o.calculatedSanctionCop,
  ]);
  await copyRowsAsTsvForExcel(headers, rows);
  feedbackMsg.value = 'Tabla de obligaciones copiada al portapapeles en formato TSV lista para pegar en Excel.';
}

onMounted(() => {
  loadDashboard();
  calculateBusinessDays();
});
</script>

<template>
  <div class="space-y-6">
    <!-- KPIs de Cabecera de Cartera -->
    <div v-if="dashboard" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-4">
        <div class="text-xs text-slate-400">Clientes Activos en Cartera (MariaDB)</div>
        <div class="text-2xl font-black text-white font-mono mt-1">{{ dashboard.totalActiveClients }}</div>
        <div class="text-[11px] text-indigo-400 mt-1">
          {{ dashboard.totalObligationsCount }} declaraciones monitorizadas
        </div>
      </div>

      <div class="bg-slate-900 border border-rose-500/40 rounded-2xl p-4">
        <div class="text-xs text-rose-300 font-semibold">Obligaciones Vencidas / En Mora</div>
        <div class="text-2xl font-black text-rose-400 font-mono mt-1">
          {{ dashboard.overdueObligationsCount }}
        </div>
        <div class="text-[11px] text-rose-300/80 font-mono mt-1">
          Riesgo Sanción: {{ formatCOP(dashboard.totalEstimatedSanctionRiskCop) }}
        </div>
      </div>

      <div class="bg-slate-900 border border-amber-500/40 rounded-2xl p-4">
        <div class="text-xs text-amber-300 font-semibold">Vencen en los Próximos 7 Días</div>
        <div class="text-2xl font-black text-amber-400 font-mono mt-1">
          {{ dashboard.upcoming7DaysCount }}
        </div>
        <div class="text-[11px] text-slate-400 mt-1">Control automático por último dígito del NIT</div>
      </div>

      <div class="bg-slate-900 border border-emerald-500/40 rounded-2xl p-4">
        <div class="text-xs text-emerald-300 font-semibold">Cumplimiento de Cartera</div>
        <div class="text-2xl font-black text-emerald-400 font-mono mt-1">
          {{ dashboard.complianceRatePercent }}%
        </div>
        <div class="text-[11px] text-slate-400 mt-1">
          {{ dashboard.presentedObligationsCount }} declaraciones presentadas al día
        </div>
      </div>
    </div>

    <div v-if="feedbackMsg" class="bg-emerald-950/40 border border-emerald-500/40 text-emerald-300 text-xs rounded-xl p-3 flex items-center justify-between">
      <span><i class="fa-solid fa-circle-check mr-1.5"></i>{{ feedbackMsg }}</span>
      <button @click="feedbackMsg = ''" class="text-slate-400 hover:text-white">✕</button>
    </div>

    <div v-if="errorMsg" class="bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs rounded-xl p-3">
      {{ errorMsg }}
    </div>

    <!-- Sección 1: Cartera de Clientes y Nuevo Cliente -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-building-shield text-amber-400"></i>
            Cartera de Clientes del Estudio Contable (NITs con DV Módulo 11)
          </h3>
          <p class="text-xs text-slate-400">
            Selecciona un cliente para fijarlo como contexto activo en Nómina/UGPP, Exógena, Planeación Ley 2277 y Sanciones.
          </p>
        </div>
        <button
          @click="showNewClientForm = !showNewClientForm"
          class="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs transition"
        >
          <i class="fa-solid fa-plus mr-1.5"></i>{{ showNewClientForm ? 'Cerrar Formulario' : 'Agregar Cliente a Cartera' }}
        </button>
      </div>

      <!-- Formulario Nuevo Cliente -->
      <div v-if="showNewClientForm" class="bg-slate-950 border border-amber-500/30 rounded-xl p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
        <div>
          <label class="block text-slate-300 font-semibold mb-1">NIT o Cédula (DV se calcula solo)</label>
          <input v-model="newClient.nit" type="text" placeholder="Ej: 901555432" class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Razón Social / Nombre Cliente</label>
          <input v-model="newClient.businessName" type="text" placeholder="Ej: Distribuidora El Cóndor S.A.S." class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white" />
        </div>
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Régimen Tributario</label>
          <select v-model="newClient.taxRegime" class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white">
            <option value="ORDINARIO">Régimen Ordinario (35%)</option>
            <option value="SIMPLE_RST">Régimen Simple (RST)</option>
            <option value="PERSONA_NATURAL">Persona Natural (Cédula General)</option>
          </select>
        </div>
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Periodicidad IVA</label>
          <select v-model="newClient.ivaPeriodicity" class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white">
            <option value="BIMESTRAL">Bimestral (Formulario 300)</option>
            <option value="CUATRIMESTRAL">Cuatrimestral (Formulario 300)</option>
            <option value="NO_RESPONSABLE">No Responsable de IVA</option>
          </select>
        </div>
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Ingresos Brutos Anuales Est. (COP)</label>
          <input v-model.number="newClient.estimatedAnnualIncomeCop" type="number" step="50000000" class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Número de Empleados</label>
          <input v-model.number="newClient.employeesCount" type="number" min="0" class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Ciudad / Municipio DANE</label>
          <input v-model="newClient.municipalityName" type="text" class="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-white" />
        </div>
        <div class="flex items-end">
          <button @click="createNewClient" class="w-full py-2 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold transition">
            Guardar Cliente y Generar Calendario
          </button>
        </div>
      </div>

      <!-- Tarjetas Compactas de Clientes -->
      <div v-if="dashboard?.clients" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 text-xs">
        <div
          v-for="c in dashboard.clients"
          :key="c.id"
          @click="emit('selectClient', c)"
          class="cursor-pointer rounded-xl p-3.5 border transition flex flex-col justify-between gap-2"
          :class="
            activeClientId === c.id
              ? 'bg-amber-500/10 border-amber-500/50'
              : 'bg-slate-950 border-slate-800 hover:border-slate-700'
          "
        >
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-bold text-white text-sm">{{ c.businessName }}</div>
              <div class="font-mono text-amber-300 text-xs mt-0.5">
                NIT {{ c.formattedNit }}
                <span class="text-slate-400">• Último dígito: </span>
                <strong class="text-white">{{ c.lastNitDigit }}</strong>
              </div>
            </div>
            <span class="px-2 py-0.5 rounded bg-slate-800 text-cyan-300 font-mono text-[10px] font-bold">
              {{ c.taxRegime }}
            </span>
          </div>
          <div class="flex flex-wrap items-center justify-between text-[11px] text-slate-400 border-t border-slate-800/80 pt-2">
            <span>{{ c.municipalityName }} ({{ c.municipalityDaneCode }})</span>
            <span class="font-mono text-emerald-400">{{ c.employeesCount }} emp • {{ formatCOP(c.estimatedAnnualIncomeCop) }}/año</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Sección 2: Radar Maestro de Obligaciones Tributarias -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-satellite-dish text-rose-400"></i>
            Radar de Vencimientos Tributarios DIAN y Semáforo de Mora por NIT
          </h3>
          <p class="text-xs text-slate-400">
            Haz clic en <strong class="text-rose-300">Liquidar Sanción (1 Clic)</strong> en cualquier obligación vencida para abrir el Liquidador Forense con sus datos exactos.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <input
            v-model="searchTerm"
            type="text"
            placeholder="Filtrar por NIT, cliente o impuesto..."
            class="bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-white"
          />
          <button
            @click="filterUrgency = 'ALL'"
            class="px-3 py-1.5 rounded-xl font-semibold"
            :class="filterUrgency === 'ALL' ? 'bg-white text-slate-950' : 'bg-slate-800 text-slate-300'"
          >
            Todas
          </button>
          <button
            @click="filterUrgency = 'CRITICA_MORA'"
            class="px-3 py-1.5 rounded-xl font-semibold"
            :class="filterUrgency === 'CRITICA_MORA' ? 'bg-rose-500 text-white' : 'bg-slate-800 text-rose-300'"
          >
            En Mora
          </button>
          <button
            @click="filterUrgency = 'PROXIMA_7_DIAS'"
            class="px-3 py-1.5 rounded-xl font-semibold"
            :class="filterUrgency === 'PROXIMA_7_DIAS' ? 'bg-amber-500 text-slate-950' : 'bg-slate-800 text-amber-300'"
          >
            Próximas 7d
          </button>
          <button
            @click="copyObligationsTsv"
            class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-emerald-300 font-semibold"
          >
            <i class="fa-solid fa-copy mr-1"></i>Copiar Excel
          </button>
          <button
            @click="exportObligationsCsv"
            class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-cyan-300 font-semibold"
          >
            <i class="fa-solid fa-file-csv mr-1"></i>Exportar CSV
          </button>
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="border-b border-slate-800 text-slate-400 uppercase text-[10px]">
              <th class="py-2.5 px-3">NIT (Últ. Dígito)</th>
              <th class="py-2.5 px-3">Cliente / Régimen</th>
              <th class="py-2.5 px-3">Declaración / Form.</th>
              <th class="py-2.5 px-3">Periodo</th>
              <th class="py-2.5 px-3">Vencimiento</th>
              <th class="py-2.5 px-3 text-right">Impuesto Est.</th>
              <th class="py-2.5 px-3 text-center">Estado</th>
              <th class="py-2.5 px-3 text-right">Acciones Rápidas</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/70">
            <tr v-for="ob in filteredObligations" :key="ob.id" class="hover:bg-slate-950/60">
              <td class="py-2.5 px-3 font-mono text-white">
                {{ ob.clientFormattedNit }}
                <span class="ml-1 px-1.5 py-0.5 rounded bg-slate-800 text-amber-300 font-bold text-[10px]">
                  #{{ ob.lastNitDigit }}
                </span>
              </td>
              <td class="py-2.5 px-3">
                <div class="font-semibold text-white">{{ ob.clientBusinessName }}</div>
                <div class="text-[10px] text-slate-400">{{ ob.clientTaxRegime }}</div>
              </td>
              <td class="py-2.5 px-3 font-mono">
                <span class="px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-300 font-bold mr-1">
                  F-{{ ob.formCode }}
                </span>
                <span class="text-slate-200">{{ ob.declarationType }}</span>
              </td>
              <td class="py-2.5 px-3 font-mono text-slate-300">{{ ob.taxPeriod }}</td>
              <td class="py-2.5 px-3 font-mono">
                <div class="text-white">{{ ob.dueDate }}</div>
                <div
                  class="text-[10px]"
                  :class="
                    ob.daysUntilOrOverdue < 0
                      ? 'text-rose-400 font-bold'
                      : ob.daysUntilOrOverdue <= 7
                        ? 'text-amber-300'
                        : 'text-slate-400'
                  "
                >
                  {{
                    ob.status === 'PRESENTADA'
                      ? 'Presentada a tiempo'
                      : ob.daysUntilOrOverdue < 0
                        ? `${Math.abs(ob.daysUntilOrOverdue)} días en mora`
                        : `Vence en ${ob.daysUntilOrOverdue} días`
                  }}
                </div>
              </td>
              <td class="py-2.5 px-3 text-right font-mono text-white">
                {{ formatCOP(ob.estimatedTaxCop) }}
              </td>
              <td class="py-2.5 px-3 text-center">
                <span
                  class="px-2.5 py-1 rounded-full text-[10px] font-bold"
                  :class="
                    ob.status === 'PRESENTADA'
                      ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                      : ob.status === 'VENCIDA'
                        ? 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                        : 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                  "
                >
                  {{ ob.status }}
                </span>
              </td>
              <td class="py-2.5 px-3 text-right space-x-1.5">
                <button
                  v-if="ob.status === 'VENCIDA'"
                  @click="emit('liquidateObligationSanction', ob)"
                  class="px-2.5 py-1 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-bold text-[11px] transition"
                >
                  <i class="fa-solid fa-gavel mr-1"></i>Liquidar Sanción (1 Clic)
                </button>
                <button
                  v-if="ob.status !== 'PRESENTADA'"
                  @click="changeObligationStatus(ob.id, 'PRESENTADA')"
                  class="px-2.5 py-1 rounded-lg bg-emerald-500/20 hover:bg-emerald-500/30 text-emerald-300 font-semibold text-[11px] transition"
                >
                  Marcar Presentada
                </button>
                <button
                  v-else
                  @click="changeObligationStatus(ob.id, 'PENDIENTE')"
                  class="px-2.5 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-[11px] transition"
                >
                  Reabrir
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Sección 3: Expediente Digital de Papeles de Trabajo Guardados en MariaDB y Calculadora de Términos -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <div class="lg:col-span-7 bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-3">
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-folder-tree text-cyan-400"></i>
          Expediente Digital de Papeles de Trabajo Guardados en MariaDB
        </h3>
        <p class="text-xs text-slate-400">
          Historial auditable de liquidaciones de nómina/UGPP, pre-auditorías de Exógena, estudios Ley 2277 y sanciones guardadas por cliente.
        </p>

        <div class="space-y-2.5 max-h-80 overflow-y-auto pr-1">
          <div
            v-for="wp in dashboard?.recentWorkpapers || []"
            :key="wp.id"
            class="bg-slate-950 border border-slate-800 rounded-xl p-3.5 text-xs space-y-1.5"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="font-bold text-white">{{ wp.title }}</div>
              <span class="px-2 py-0.5 rounded bg-cyan-500/15 text-cyan-300 font-mono text-[10px] font-bold">
                {{ wp.workpaperType }} • {{ wp.taxPeriod }}
              </span>
            </div>
            <div class="text-[11px] text-amber-300 font-mono">
              Cliente: {{ wp.clientBusinessName }} (NIT {{ wp.clientFormattedNit }})
            </div>
            <p class="text-slate-300 text-[11px]">{{ wp.executiveSummary }}</p>
            <div class="flex items-center justify-between text-[11px] text-slate-400 pt-1 border-t border-slate-900">
              <span>Impacto / Hallazgo Cuantificado:</span>
              <span class="font-mono font-bold text-emerald-400">{{ formatCOP(wp.detectedSavingsOrRiskCop) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-calendar-check text-emerald-400"></i>
            Calculadora de Plazos para Responder Requerimientos DIAN / UGPP (Días Hábiles Ley Emiliani)
          </h3>
          <p class="text-xs text-slate-400 mt-1">
            Calcula la fecha exacta límite descontando domingos y los 18 festivos oficiales de Colombia.
          </p>
        </div>

        <div class="grid grid-cols-2 gap-3 text-xs">
          <div>
            <label class="block text-slate-300 font-semibold mb-1">Fecha Notificación Acto DIAN</label>
            <input v-model="bdaysForm.startDate" type="date" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
          <div>
            <label class="block text-slate-300 font-semibold mb-1">Plazo en Días Hábiles</label>
            <input v-model.number="bdaysForm.days" type="number" min="1" max="365" class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono" />
          </div>
        </div>
        <button @click="calculateBusinessDays" class="w-full py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition">
          Calcular Vencimiento del Término Legal
        </button>

        <div v-if="bdaysResult" class="bg-slate-950 border border-emerald-500/30 rounded-xl p-3.5 text-xs space-y-1">
          <div class="text-slate-400">Fecha Máxima para Radicar Respuesta:</div>
          <div class="text-xl font-black text-emerald-400 font-mono">{{ bdaysResult.resultingDueDate }}</div>
          <div class="text-[11px] text-slate-400">
            Transcurren <strong>{{ bdaysResult.totalCalendarDaysElapsed }}</strong> días calendario y se omitieron
            <strong>{{ bdaysResult.holidaysEncountered?.length || 0 }}</strong> festivos nacionales.
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
