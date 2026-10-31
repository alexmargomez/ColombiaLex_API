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
    feedbackMsg.value = `Cliente ${created.businessName} (${created.formattedNit}) registrado con calendario tributario en días hábiles en MariaDB.`;
    showNewClientForm.value = false;
    newClient.nit = '';
    newClient.businessName = '';
    await loadDashboard();
    emit('client-created', created);
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
    <!-- KPIs Ejecutivos de Cabecera de Cartera -->
    <div v-if="dashboard" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <div class="bg-white border border-slate-200 border-l-4 border-l-blue-700 rounded-xl p-4 shadow-xs">
        <div class="text-xs font-semibold text-slate-500 uppercase tracking-wider">Clientes Activos en Cartera</div>
        <div class="text-2xl font-extrabold text-slate-900 font-mono tabular-nums mt-1">
          {{ dashboard.totalActiveClients }}
        </div>
        <div class="text-[11px] text-slate-600 mt-1">
          {{ dashboard.totalObligationsCount }} declaraciones monitorizadas en cartera
        </div>
      </div>

      <div class="bg-white border border-slate-200 border-l-4 border-l-red-600 rounded-xl p-4 shadow-xs">
        <div class="text-xs font-semibold text-red-700 uppercase tracking-wider">Obligaciones Vencidas / Mora</div>
        <div class="text-2xl font-extrabold text-red-700 font-mono tabular-nums mt-1">
          {{ dashboard.overdueObligationsCount }}
        </div>
        <div class="text-[11px] text-red-700 font-mono tabular-nums font-medium mt-1">
          Contingencia Sanción: {{ formatCOP(dashboard.totalEstimatedSanctionRiskCop) }}
        </div>
      </div>

      <div class="bg-white border border-slate-200 border-l-4 border-l-amber-600 rounded-xl p-4 shadow-xs">
        <div class="text-xs font-semibold text-amber-800 uppercase tracking-wider">Vencen en Próximos 7 Días</div>
        <div class="text-2xl font-extrabold text-amber-700 font-mono tabular-nums mt-1">
          {{ dashboard.upcoming7DaysCount }}
        </div>
        <div class="text-[11px] text-slate-600 mt-1">Control en días hábiles por último dígito NIT</div>
      </div>

      <div class="bg-white border border-slate-200 border-l-4 border-l-emerald-600 rounded-xl p-4 shadow-xs">
        <div class="text-xs font-semibold text-emerald-800 uppercase tracking-wider">Cumplimiento de Cartera</div>
        <div class="text-2xl font-extrabold text-emerald-700 font-mono tabular-nums mt-1">
          {{ dashboard.complianceRatePercent }}%
        </div>
        <div class="text-[11px] text-slate-600 mt-1">
          {{ dashboard.presentedObligationsCount }} declaraciones presentadas oportunamente
        </div>
      </div>
    </div>

    <!-- Alertas y Confirmaciones -->
    <div
      v-if="feedbackMsg"
      class="bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-lg p-3 flex items-center justify-between shadow-2xs"
    >
      <span class="font-medium"><i class="fa-solid fa-circle-check text-emerald-600 mr-1.5"></i>{{ feedbackMsg }}</span>
      <button @click="feedbackMsg = ''" class="text-emerald-700 hover:text-emerald-950 font-bold">✕</button>
    </div>

    <div v-if="errorMsg" class="bg-red-50 border border-red-200 text-red-800 text-xs rounded-lg p-3 font-medium">
      <i class="fa-solid fa-triangle-exclamation text-red-600 mr-1.5"></i>{{ errorMsg }}
    </div>

    <!-- Sección 1: Cartera de Clientes del Estudio Contable -->
    <div class="bg-white border border-slate-200 border-t-4 border-t-blue-700 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
        <div>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
              PT-01A
            </span>
            <h3 class="text-base font-bold text-slate-900">
              Directorio de Contribuyentes y Empresas en Cartera (Verificación Módulo 11 DIAN)
            </h3>
          </div>
          <p class="text-xs text-slate-500 mt-0.5">
            Selecciona una empresa para fijar su expediente como contexto activo en Nómina/UGPP, Exógena, Planeación Ley 2277 y Sanciones.
          </p>
        </div>
        <button
          @click="showNewClientForm = !showNewClientForm"
          class="px-4 py-2 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs transition shadow-2xs"
        >
          <i class="fa-solid fa-plus mr-1.5"></i>{{ showNewClientForm ? 'Cerrar Registro' : 'Registrar Nuevo Cliente' }}
        </button>
      </div>

      <!-- Formulario de Alta de Cliente -->
      <div
        v-if="showNewClientForm"
        class="bg-slate-50 border border-slate-200 rounded-xl p-4 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs"
      >
        <div>
          <label class="block text-slate-700 font-semibold mb-1">NIT o Cédula (DV automático)</label>
          <input
            v-model="newClient.nit"
            type="text"
            placeholder="Ej: 901555432"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono focus:outline-none focus:border-blue-600"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Razón Social / Nombre Completo</label>
          <input
            v-model="newClient.businessName"
            type="text"
            placeholder="Ej: Distribuidora El Cóndor S.A.S."
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 focus:outline-none focus:border-blue-600"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Régimen Tributario</label>
          <select
            v-model="newClient.taxRegime"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 focus:outline-none focus:border-blue-600"
          >
            <option value="ORDINARIO">Régimen Ordinario (35%)</option>
            <option value="SIMPLE_RST">Régimen Simple (RST)</option>
            <option value="PERSONA_NATURAL">Persona Natural (Cédula General)</option>
          </select>
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Periodicidad IVA</label>
          <select
            v-model="newClient.ivaPeriodicity"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 focus:outline-none focus:border-blue-600"
          >
            <option value="BIMESTRAL">Bimestral (Formulario 300)</option>
            <option value="CUATRIMESTRAL">Cuatrimestral (Formulario 300)</option>
            <option value="NO_RESPONSABLE">No Responsable de IVA</option>
          </select>
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Ingresos Brutos Anuales Est. (COP)</label>
          <input
            v-model.number="newClient.estimatedAnnualIncomeCop"
            type="number"
            step="50000000"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Número de Empleados</label>
          <input
            v-model.number="newClient.employeesCount"
            type="number"
            min="0"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Ciudad / Municipio DANE</label>
          <input
            v-model="newClient.municipalityName"
            type="text"
            class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 focus:outline-none focus:border-blue-600"
          />
        </div>
        <div class="flex items-end">
          <button
            @click="createNewClient"
            class="w-full py-2 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold transition shadow-2xs"
          >
            <i class="fa-solid fa-floppy-disk mr-1.5"></i>Guardar y Generar Calendario
          </button>
        </div>
      </div>

      <!-- Tarjetas de Empresas en Cartera -->
      <div v-if="dashboard?.clients" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 text-xs">
        <div
          v-for="c in dashboard.clients"
          :key="c.id"
          @click="emit('selectClient', c)"
          class="cursor-pointer rounded-xl p-4 border transition flex flex-col justify-between gap-2.5"
          :class="
            activeClientId === c.id
              ? 'bg-blue-50/60 border-blue-600 ring-2 ring-blue-100'
              : 'bg-white border-slate-200 hover:border-slate-300 hover:bg-slate-50/70'
          "
        >
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-bold text-slate-900 text-sm">{{ c.businessName }}</div>
              <div class="font-mono text-slate-700 text-xs mt-0.5">
                NIT <strong class="text-blue-800">{{ c.formattedNit }}</strong>
                <span class="text-slate-400 mx-1">•</span>
                <span class="text-slate-600">Últ. dígito: </span>
                <strong class="text-slate-900">{{ c.lastNitDigit }}</strong>
              </div>
            </div>
            <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200 font-mono text-[10px] font-bold">
              {{ c.taxRegime }}
            </span>
          </div>
          <div class="flex flex-wrap items-center justify-between text-[11px] text-slate-600 border-t border-slate-200/80 pt-2">
            <span><i class="fa-solid fa-location-dot text-slate-400 mr-1"></i>{{ c.municipalityName }} ({{ c.municipalityDaneCode }})</span>
            <span class="font-mono tabular-nums font-semibold text-slate-800">
              {{ c.employeesCount }} emp • {{ formatCOP(c.estimatedAnnualIncomeCop) }}/año
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Sección 2: Radar Maestro de Obligaciones Tributarias -->
    <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
        <div>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
              PT-01B
            </span>
            <h3 class="text-base font-bold text-slate-900">
              Libro Auxiliar de Vencimientos Tributarios DIAN y Estado de Cumplimiento por NIT
            </h3>
          </div>
          <p class="text-xs text-slate-500 mt-0.5">
            En cualquier declaración vencida, usa <strong class="text-red-700">Liquidar Sanción (1 Clic)</strong> para enviar sus datos al Liquidador Forense Art. 640 y Formulario 490.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <input
            v-model="searchTerm"
            type="text"
            placeholder="Filtrar NIT, empresa o formulario..."
            class="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-slate-900 focus:outline-none focus:border-blue-600"
          />
          <button
            @click="filterUrgency = 'ALL'"
            class="px-3 py-1.5 rounded-lg font-semibold border transition"
            :class="
              filterUrgency === 'ALL'
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            "
          >
            Todas
          </button>
          <button
            @click="filterUrgency = 'CRITICA_MORA'"
            class="px-3 py-1.5 rounded-lg font-semibold border transition"
            :class="
              filterUrgency === 'CRITICA_MORA'
                ? 'bg-red-700 text-white border-red-700'
                : 'bg-red-50 text-red-800 border-red-200 hover:bg-red-100'
            "
          >
            En Mora
          </button>
          <button
            @click="filterUrgency = 'PROXIMA_7_DIAS'"
            class="px-3 py-1.5 rounded-lg font-semibold border transition"
            :class="
              filterUrgency === 'PROXIMA_7_DIAS'
                ? 'bg-amber-600 text-white border-amber-600'
                : 'bg-amber-50 text-amber-800 border-amber-200 hover:bg-amber-100'
            "
          >
            Próximas 7d
          </button>
          <button
            @click="copyObligationsTsv"
            class="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
          >
            <i class="fa-solid fa-table text-emerald-700 mr-1"></i>Copiar Excel
          </button>
          <button
            @click="exportObligationsCsv"
            class="px-3 py-1.5 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white font-semibold transition"
          >
            <i class="fa-solid fa-file-csv mr-1"></i>Exportar CSV
          </button>
        </div>
      </div>

      <div class="overflow-x-auto border border-slate-200 rounded-lg">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-100 border-b-2 border-slate-300 text-slate-700 uppercase text-[11px] font-bold tracking-wider">
              <th class="py-2.5 px-3">NIT (Últ. Dígito)</th>
              <th class="py-2.5 px-3">Contribuyente / Régimen</th>
              <th class="py-2.5 px-3">Declaración / Formulario</th>
              <th class="py-2.5 px-3">Periodo</th>
              <th class="py-2.5 px-3">Fecha Vencimiento</th>
              <th class="py-2.5 px-3 text-right">Impuesto Estimado</th>
              <th class="py-2.5 px-3 text-center">Estado</th>
              <th class="py-2.5 px-3 text-right">Acción Contable</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr
              v-for="ob in filteredObligations"
              :key="ob.id"
              class="even:bg-slate-50/60 hover:bg-blue-50/40 transition-colors"
              :class="ob.status === 'VENCIDA' ? 'bg-red-50/30' : ''"
            >
              <td class="py-2.5 px-3 font-mono text-slate-900 font-semibold whitespace-nowrap">
                {{ ob.clientFormattedNit }}
                <span class="ml-1 px-1.5 py-0.5 rounded bg-slate-100 text-slate-800 border border-slate-300 font-bold text-[10px]">
                  #{{ ob.lastNitDigit }}
                </span>
              </td>
              <td class="py-2.5 px-3">
                <div class="font-bold text-slate-900">{{ ob.clientBusinessName }}</div>
                <div class="text-[10px] text-slate-500 font-medium">{{ ob.clientTaxRegime }}</div>
              </td>
              <td class="py-2.5 px-3 font-mono">
                <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-bold text-[10px] mr-1.5">
                  F-{{ ob.formCode }}
                </span>
                <span class="text-slate-800 font-sans font-medium">{{ ob.declarationType }}</span>
              </td>
              <td class="py-2.5 px-3 font-mono text-slate-700">{{ ob.taxPeriod }}</td>
              <td class="py-2.5 px-3 font-mono whitespace-nowrap">
                <div class="text-slate-900 font-semibold">{{ ob.dueDate }}</div>
                <div
                  class="text-[10px] font-sans"
                  :class="
                    ob.status === 'PRESENTADA'
                      ? 'text-emerald-700 font-medium'
                      : ob.daysUntilOrOverdue < 0
                        ? 'text-red-700 font-bold'
                        : ob.daysUntilOrOverdue <= 7
                          ? 'text-amber-700 font-semibold'
                          : 'text-slate-500'
                  "
                >
                  {{
                    ob.status === 'PRESENTADA'
                      ? 'Presentada oportunamente'
                      : ob.daysUntilOrOverdue < 0
                        ? `${Math.abs(ob.daysUntilOrOverdue)} días en mora`
                        : `Vence en ${ob.daysUntilOrOverdue} días`
                  }}
                </div>
              </td>
              <td class="py-2.5 px-3 text-right font-mono tabular-nums font-semibold text-slate-900 whitespace-nowrap">
                {{ formatCOP(ob.estimatedTaxCop) }}
              </td>
              <td class="py-2.5 px-3 text-center whitespace-nowrap">
                <span
                  class="px-2.5 py-0.5 rounded-full text-[10px] font-bold border"
                  :class="
                    ob.status === 'PRESENTADA'
                      ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                      : ob.status === 'VENCIDA'
                        ? 'bg-red-50 text-red-800 border-red-200'
                        : 'bg-amber-50 text-amber-800 border-amber-200'
                  "
                >
                  {{ ob.status }}
                </span>
              </td>
              <td class="py-2.5 px-3 text-right space-x-1.5 whitespace-nowrap">
                <button
                  v-if="ob.status === 'VENCIDA'"
                  @click="emit('liquidateObligationSanction', ob)"
                  class="px-2.5 py-1 rounded-md bg-red-700 hover:bg-red-800 text-white font-semibold text-[11px] transition shadow-2xs"
                >
                  <i class="fa-solid fa-gavel mr-1"></i>Liquidar Sanción (1 Clic)
                </button>
                <button
                  v-if="ob.status !== 'PRESENTADA'"
                  @click="changeObligationStatus(ob.id, 'PRESENTADA')"
                  class="px-2.5 py-1 rounded-md bg-emerald-50 hover:bg-emerald-100 text-emerald-800 border border-emerald-200 font-semibold text-[11px] transition"
                >
                  Marcar Presentada
                </button>
                <button
                  v-else
                  @click="changeObligationStatus(ob.id, 'PENDIENTE')"
                  class="px-2.5 py-1 rounded-md bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 text-[11px] transition"
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
      <div class="lg:col-span-7 bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-3">
        <div class="border-b border-slate-100 pb-2.5">
          <h3 class="text-sm font-bold text-slate-900 flex items-center gap-2">
            <i class="fa-solid fa-folder-open text-blue-700"></i>
            Expediente Digital de Papeles de Trabajo del Estudio Contable (NIA 230)
          </h3>
          <p class="text-xs text-slate-500 mt-0.5">
            Trazabilidad auditable de liquidaciones de nómina/UGPP, pre-auditorías de Exógena, estudios Ley 2277 y sanciones por cliente.
          </p>
        </div>

        <div class="space-y-2.5 max-h-80 overflow-y-auto pr-1">
          <div
            v-for="wp in dashboard?.recentWorkpapers || []"
            :key="wp.id"
            class="bg-slate-50 border border-slate-200 rounded-lg p-3.5 text-xs space-y-1.5"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="font-bold text-slate-900">{{ wp.title }}</div>
              <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
                {{ wp.workpaperType }} • {{ wp.taxPeriod }}
              </span>
            </div>
            <div class="text-[11px] text-slate-700 font-mono font-semibold">
              Cliente: {{ wp.clientBusinessName }} (NIT {{ wp.clientFormattedNit }})
            </div>
            <p class="text-slate-600 text-[11px]">{{ wp.executiveSummary }}</p>
            <div class="flex items-center justify-between text-[11px] text-slate-600 pt-1.5 border-t border-slate-200">
              <span>Impacto / Hallazgo Cuantificado:</span>
              <span class="font-mono tabular-nums font-bold text-slate-900">{{ formatCOP(wp.detectedSavingsOrRiskCop) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="lg:col-span-5 bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div class="border-b border-slate-100 pb-2.5">
          <h3 class="text-sm font-bold text-slate-900 flex items-center gap-2">
            <i class="fa-solid fa-calendar-check text-blue-700"></i>
            Términos Procesales para Responder Requerimientos DIAN / UGPP (Ley Emiliani)
          </h3>
          <p class="text-xs text-slate-500 mt-0.5">
            Determina la fecha exacta de vencimiento en días hábiles descontando domingos y los 18 festivos nacionales.
          </p>
        </div>

        <div class="grid grid-cols-2 gap-3 text-xs">
          <div>
            <label class="block text-slate-700 font-semibold mb-1">Fecha Notificación Acto DIAN</label>
            <input
              v-model="bdaysForm.startDate"
              type="date"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono focus:outline-none focus:border-blue-600"
            />
          </div>
          <div>
            <label class="block text-slate-700 font-semibold mb-1">Plazo en Días Hábiles</label>
            <input
              v-model.number="bdaysForm.days"
              type="number"
              min="1"
              max="365"
              class="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
            />
          </div>
        </div>
        <button
          @click="calculateBusinessDays"
          class="w-full py-2 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs transition shadow-2xs"
        >
          Calcular Fecha Límite de Radicación
        </button>

        <div v-if="bdaysResult" class="bg-blue-50/70 border border-blue-200 rounded-lg p-3.5 text-xs space-y-1">
          <div class="text-slate-600 font-medium">Fecha Máxima Legal para Radicar Respuesta:</div>
          <div class="text-xl font-extrabold text-blue-900 font-mono">{{ bdaysResult.resultingDueDate }}</div>
          <div class="text-[11px] text-slate-600">
            Transcurren <strong class="text-slate-900">{{ bdaysResult.totalCalendarDaysElapsed }}</strong> días calendario y se omitieron
            <strong class="text-slate-900">{{ bdaysResult.holidaysEncountered?.length || 0 }}</strong> festivos nacionales.
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
