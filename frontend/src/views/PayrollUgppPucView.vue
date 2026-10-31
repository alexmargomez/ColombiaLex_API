<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { apiRequest, copyRowsAsTsvForExcel, exportRowsToCsv, formatCOP, parseColombianCurrency } from '../services/api';

const props = defineProps<{
  activeClient?: any | null;
}>();

const loading = ref(false);
const errorMsg = ref('');
const feedbackMsg = ref('');
const year = ref(2026);
const applyArt114 = ref(true);
const showPasteBox = ref(false);
const rawPastePayroll = ref('');
const auditResult = ref<any>(null);

watch(
  () => props.activeClient,
  (client) => {
    if (client && typeof client.appliesArt1141Exemption === 'boolean') {
      applyArt114.value = client.appliesArt1141Exemption;
      runPayrollBatchAudit();
    }
  }
);

interface PayrollRow {
  document: string;
  employeeName: string;
  area: string;
  baseSalaryCOP: number;
  overtimeAndSurchargesCOP: number;
  commissionsCOP: number;
  nonSalaryPaymentsArt128COP: number;
  arlRiskClass: string;
  integralSalary: boolean;
}

const employees = ref<PayrollRow[]>([
  {
    document: '79456120',
    employeeName: 'Carlos Andrés Ruiz (Gerente Operativo - Riesgo UGPP)',
    area: 'ADMINISTRACION',
    baseSalaryCOP: 3200000,
    overtimeAndSurchargesCOP: 0,
    commissionsCOP: 0,
    nonSalaryPaymentsArt128COP: 3800000, // 54.2% no salarial -> Exceso UGPP 40%
    arlRiskClass: 'I',
    integralSalary: false,
  },
  {
    document: '52891340',
    employeeName: 'Laura Valentina Gómez (Directora Comercial - FSP)',
    area: 'VENTAS',
    baseSalaryCOP: 6800000,
    overtimeAndSurchargesCOP: 0,
    commissionsCOP: 1400000,
    nonSalaryPaymentsArt128COP: 2500000,
    arlRiskClass: 'II',
    integralSalary: false,
  },
  {
    document: '1018442910',
    employeeName: 'Mateo Hernández (Analista Contable - Aux. Transporte)',
    area: 'ADMINISTRACION',
    baseSalaryCOP: 2100000,
    overtimeAndSurchargesCOP: 285000,
    commissionsCOP: 0,
    nonSalaryPaymentsArt128COP: 300000,
    arlRiskClass: 'I',
    integralSalary: false,
  },
  {
    document: '1032998123',
    employeeName: 'Diana Marcela Pardo (Coordinadora Logística - Riesgo UGPP)',
    area: 'ADMINISTRACION',
    baseSalaryCOP: 2400000,
    overtimeAndSurchargesCOP: 180000,
    commissionsCOP: 0,
    nonSalaryPaymentsArt128COP: 2600000, // Supera tope 40% Ley 1393
    arlRiskClass: 'III',
    integralSalary: false,
  },
  {
    document: '80112398',
    employeeName: 'Jorge Eliécer Vargas (Técnico Planta)',
    area: 'ADMINISTRACION',
    baseSalaryCOP: 1650000,
    overtimeAndSurchargesCOP: 310000,
    commissionsCOP: 0,
    nonSalaryPaymentsArt128COP: 0,
    arlRiskClass: 'IV',
    integralSalary: false,
  },
]);

// Liquidador individual de terminación de contrato Art. 64 CST
const settlementForm = reactive({
  year: 2026,
  monthlyBaseSalary: 2600000,
  monthlyVariableCommissions: 300000,
  workedDaysTotal: 720,
  workedDaysCurrentYear: 210,
  workedDaysCurrentSemester: 90,
  pendingVacationDaysAccounted: 360,
  contractType: 'INDEFINIDO',
  dismissalWithoutJustCause: true,
  remainingDaysFixedTerm: 90,
  arlRiskClass: 'I',
  applyArt114Exoneration: true,
});
const settlementResult = ref<any>(null);

async function runPayrollBatchAudit() {
  loading.value = true;
  errorMsg.value = '';
  try {
    auditResult.value = await apiRequest('/api/v1/accounting/payroll/batch-audit', {
      method: 'POST',
      body: JSON.stringify({
        year: year.value,
        employerAppliesArt1141Exoneration: applyArt114.value,
        employees: employees.value,
      }),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  } finally {
    loading.value = false;
  }
}

function addEmployeeRow() {
  employees.value.push({
    document: '1000123456',
    employeeName: 'Nuevo Empleado Planilla',
    area: 'ADMINISTRACION',
    baseSalaryCOP: 1800000,
    overtimeAndSurchargesCOP: 0,
    commissionsCOP: 0,
    nonSalaryPaymentsArt128COP: 400000,
    arlRiskClass: 'I',
    integralSalary: false,
  });
}

function removeEmployeeRow(idx: number) {
  if (employees.value.length > 1) {
    employees.value.splice(idx, 1);
  }
}

function parsePastedPayrollFromExcel() {
  const lines = rawPastePayroll.value.split(/\r?\n/).filter((l) => l.trim().length > 0);
  const parsed: PayrollRow[] = [];
  for (const line of lines) {
    const sep = line.includes('\t') ? '\t' : line.includes(';') ? ';' : ',';
    const cols = line.split(sep);
    if (cols.length < 3) continue;
    const doc = cols[0].trim();
    if (doc.toLowerCase().includes('cedula') || doc.toLowerCase().includes('documento')) continue;
    parsed.push({
      document: doc.replace(/[^0-9a-zA-Z]/g, ''),
      employeeName: cols[1]?.trim() || 'Empleado',
      area: cols[2]?.trim().toUpperCase().includes('VENT') ? 'VENTAS' : 'ADMINISTRACION',
      baseSalaryCOP: parseColombianCurrency(cols[3], 1550000),
      overtimeAndSurchargesCOP: parseColombianCurrency(cols[4], 0),
      commissionsCOP: parseColombianCurrency(cols[5], 0),
      nonSalaryPaymentsArt128COP: parseColombianCurrency(cols[6], 0),
      arlRiskClass: ['I', 'II', 'III', 'IV', 'V'].includes(cols[7]?.trim().toUpperCase())
        ? cols[7].trim().toUpperCase()
        : 'I',
      integralSalary: false,
    });
  }
  if (parsed.length > 0) {
    employees.value = parsed;
    showPasteBox.value = false;
    feedbackMsg.value = `Se importaron ${parsed.length} empleados desde Excel y se recalculó la auditoría UGPP + PUC.`;
    runPayrollBatchAudit();
  }
}

async function copyPucJournalForExcel() {
  if (!auditResult.value?.pucJournalEntries) return;
  const headers = ['Cuenta_PUC', 'Nombre_Cuenta', 'Centro_Costo', 'Naturaleza', 'Debito_COP', 'Credito_COP', 'Detalle_Contable'];
  const rows = auditResult.value.pucJournalEntries.map((j: any) => [
    j.pucCode,
    j.accountName,
    j.costCenterArea,
    j.nature,
    j.debitCOP,
    j.creditCOP,
    j.accountingNote,
  ]);
  await copyRowsAsTsvForExcel(headers, rows);
  feedbackMsg.value = 'Comprobante Contable PUC (Débitos y Créditos) copiado al portapapeles listo para pegar en Excel / Siigo / World Office.';
}

function downloadPucCsv() {
  if (!auditResult.value?.pucJournalEntries) return;
  const headers = ['Cuenta_PUC', 'Nombre_Cuenta', 'Centro_Costo', 'Naturaleza', 'Debito_COP', 'Credito_COP', 'Detalle_Contable'];
  const rows = auditResult.value.pucJournalEntries.map((j: any) => [
    j.pucCode,
    j.accountName,
    j.costCenterArea,
    j.nature,
    j.debitCOP,
    j.creditCOP,
    j.accountingNote,
  ]);
  exportRowsToCsv('comprobante_nomina_puc_alcontable.csv', headers, rows);
}

async function savePayrollWorkpaperInMariaDB() {
  if (!auditResult.value) return;
  try {
    const clientName = props.activeClient?.businessName || 'Estudio Contable';
    await apiRequest('/api/v1/accounting/portfolio/workpapers', {
      method: 'POST',
      body: JSON.stringify({
        clientId: props.activeClient?.id || null,
        workpaperType: 'NOMINA_UGPP_PUC',
        title: `Planilla Nómina (${auditResult.value.employeesProcessed} emp) + Asiento PUC — ${clientName}`,
        taxPeriod: `${year.value}-M10`,
        executiveSummary: `Empleados con exceso >40% UGPP (Ley 1393): ${auditResult.value.employeesWithUgppRiskCount}. Exceso gravado IBC: ${formatCOP(auditResult.value.totalUgppExcessGravadoCOP)}. Partida doble PUC cuadrada por ${formatCOP(auditResult.value.totalPucDebitCOP)}.`,
        detectedSavingsOrRiskCop: auditResult.value.totalAnnualOmittedPilaRiskCOP,
        payloadJson: JSON.stringify(auditResult.value),
      }),
    });
    feedbackMsg.value = 'Papel de trabajo de Nómina, UGPP y Comprobante PUC guardado en MariaDB en el expediente del cliente.';
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

async function runIndividualSettlement() {
  try {
    settlementResult.value = await apiRequest('/api/v1/labor/settlement', {
      method: 'POST',
      body: JSON.stringify(settlementForm),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

onMounted(() => {
  runPayrollBatchAudit();
  runIndividualSettlement();
});
</script>

<template>
  <div class="space-y-6">
    <!-- Papel de Trabajo PT-02A: Planilla de Nómina y Parámetros de Auditoría UGPP -->
    <div class="bg-white border border-slate-200 border-t-4 border-t-blue-700 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 pb-3">
        <div>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
              PT-02A
            </span>
            <h3 class="text-base font-bold text-slate-900">
              Auditoría Masiva de Nómina, Tope 40% UGPP (Ley 1393 Art. 30) &amp; Comprobante Contable PUC
            </h3>
          </div>
          <p class="text-xs text-slate-500 mt-0.5">
            Audita la planilla mensual, recalcula el IBC sumando el exceso no salarial superior al 40% y genera el asiento contable PUC de 6 dígitos cuadrado por partida doble.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <select
            v-model.number="year"
            @change="runPayrollBatchAudit"
            class="bg-white border border-slate-300 rounded-lg px-3 py-2 text-slate-900 font-mono font-semibold focus:outline-none focus:border-blue-600"
          >
            <option :value="2026">Vigencia 2026</option>
            <option :value="2025">Vigencia 2025</option>
            <option :value="2024">Vigencia 2024</option>
          </select>

          <label class="flex items-center gap-2 bg-slate-50 border border-slate-300 rounded-lg px-3 py-2 text-slate-700 font-medium cursor-pointer">
            <input v-model="applyArt114" @change="runPayrollBatchAudit" type="checkbox" class="accent-blue-700" />
            <span>Exoneración Art. 114-1 E.T. (&lt; 10 SMLMV)</span>
          </label>

          <button
            @click="showPasteBox = !showPasteBox"
            class="px-3 py-2 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
          >
            <i class="fa-solid fa-paste text-blue-700 mr-1"></i>Pegar Nómina de Excel
          </button>

          <button
            @click="addEmployeeRow"
            class="px-3 py-2 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
          >
            <i class="fa-solid fa-user-plus text-slate-600 mr-1"></i>+ Empleado
          </button>

          <button
            @click="runPayrollBatchAudit"
            class="px-4 py-2 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold transition shadow-2xs"
          >
            <i class="fa-solid fa-calculator mr-1.5"></i>Auditar Planilla y Generar PUC
          </button>
        </div>
      </div>

      <!-- Caja de Pegado desde Excel -->
      <div v-if="showPasteBox" class="bg-slate-50 border border-slate-300 rounded-xl p-4 space-y-2 text-xs">
        <div class="text-slate-700 font-semibold">
          Pega aquí las filas desde Excel (Columnas: Documento | Nombre | Área [ADMIN/VENTAS] | Salario Básico | Horas Extra | Comisiones | Pagos No Salariales | Riesgo ARL):
        </div>
        <textarea
          v-model="rawPastePayroll"
          rows="4"
          placeholder="79456120&#9;Carlos Ruiz&#9;ADMINISTRACION&#9;3200000&#9;0&#9;0&#9;3800000&#9;I"
          class="w-full bg-white border border-slate-300 rounded-lg p-3 text-slate-900 font-mono text-xs focus:outline-none focus:border-blue-600"
        ></textarea>
        <div class="flex justify-end gap-2">
          <button
            @click="parsePastedPayrollFromExcel"
            class="px-4 py-2 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold"
          >
            Procesar Lote Pegado
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
      <div v-if="errorMsg" class="bg-red-50 border border-red-200 text-red-800 text-xs rounded-lg p-3 font-medium">
        <i class="fa-solid fa-triangle-exclamation text-red-600 mr-1.5"></i>{{ errorMsg }}
      </div>

      <!-- Grilla Editable de Planilla de Empleados -->
      <div class="overflow-x-auto border border-slate-200 rounded-lg">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="bg-slate-100 border-b-2 border-slate-300 text-slate-700 uppercase text-[11px] font-bold tracking-wider">
              <th class="py-2.5 px-2.5">Cédula</th>
              <th class="py-2.5 px-2.5">Trabajador</th>
              <th class="py-2.5 px-2.5">Centro PUC</th>
              <th class="py-2.5 px-2.5 text-right">Salario Básico</th>
              <th class="py-2.5 px-2.5 text-right">H. Extra / Rec.</th>
              <th class="py-2.5 px-2.5 text-right">Comisiones</th>
              <th class="py-2.5 px-2.5 text-right text-amber-900 bg-amber-50/60">No Salarial (Art. 128)</th>
              <th class="py-2.5 px-2.5 text-center">ARL</th>
              <th class="py-2.5 px-2.5"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="(emp, idx) in employees" :key="idx" class="even:bg-slate-50/50 hover:bg-blue-50/30">
              <td class="py-1.5 px-2">
                <input
                  v-model="emp.document"
                  class="w-24 bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-mono focus:outline-none focus:border-blue-600"
                />
              </td>
              <td class="py-1.5 px-2">
                <input
                  v-model="emp.employeeName"
                  class="w-60 bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-medium focus:outline-none focus:border-blue-600"
                />
              </td>
              <td class="py-1.5 px-2">
                <select
                  v-model="emp.area"
                  class="bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-medium focus:outline-none focus:border-blue-600"
                >
                  <option value="ADMINISTRACION">5105 Admin</option>
                  <option value="VENTAS">5205 Ventas</option>
                </select>
              </td>
              <td class="py-1.5 px-2">
                <input
                  v-model.number="emp.baseSalaryCOP"
                  type="number"
                  step="100000"
                  class="w-28 bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
                />
              </td>
              <td class="py-1.5 px-2">
                <input
                  v-model.number="emp.overtimeAndSurchargesCOP"
                  type="number"
                  step="50000"
                  class="w-24 bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
                />
              </td>
              <td class="py-1.5 px-2">
                <input
                  v-model.number="emp.commissionsCOP"
                  type="number"
                  step="50000"
                  class="w-24 bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-mono tabular-nums text-right focus:outline-none focus:border-blue-600"
                />
              </td>
              <td class="py-1.5 px-2 bg-amber-50/30">
                <input
                  v-model.number="emp.nonSalaryPaymentsArt128COP"
                  type="number"
                  step="100000"
                  class="w-28 bg-white border border-amber-300 rounded px-2 py-1 text-amber-900 font-mono tabular-nums font-semibold text-right focus:outline-none focus:border-amber-600"
                />
              </td>
              <td class="py-1.5 px-2 text-center">
                <select
                  v-model="emp.arlRiskClass"
                  class="bg-white border border-slate-300 rounded px-2 py-1 text-slate-900 font-mono focus:outline-none focus:border-blue-600"
                >
                  <option value="I">I</option>
                  <option value="II">II</option>
                  <option value="III">III</option>
                  <option value="IV">IV</option>
                  <option value="V">V</option>
                </select>
              </td>
              <td class="py-1.5 px-2 text-right">
                <button @click="removeEmployeeRow(idx)" class="text-slate-400 hover:text-red-600 font-bold">✕</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Resultados de Auditoría UGPP en Lote y Comprobante Contable PUC -->
    <div v-if="auditResult" class="space-y-6">
      <!-- KPIs del Lote de Nómina -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-white border border-slate-200 border-l-4 border-l-red-600 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-red-700 uppercase tracking-wider">Riesgo Fiscalización UGPP</div>
          <div class="text-2xl font-extrabold text-red-700 font-mono tabular-nums mt-1">
            {{ auditResult.employeesWithUgppRiskCount }} / {{ auditResult.employeesProcessed }} emp
          </div>
          <div class="text-[11px] text-red-700 font-mono tabular-nums font-medium mt-1">
            Exceso &gt;40% gravado: {{ formatCOP(auditResult.totalUgppExcessGravadoCOP) }}
          </div>
        </div>

        <div class="bg-white border border-slate-200 border-l-4 border-l-amber-600 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-amber-800 uppercase tracking-wider">Aporte PILA Omitido (Riesgo)</div>
          <div class="text-2xl font-extrabold text-amber-700 font-mono tabular-nums mt-1">
            {{ formatCOP(auditResult.totalMonthlyOmittedPilaRiskCOP) }}/mes
          </div>
          <div class="text-[11px] text-slate-600 font-mono tabular-nums mt-1">
            Proyección anual: {{ formatCOP(auditResult.totalAnnualOmittedPilaRiskCOP) }}
          </div>
        </div>

        <div class="bg-white border border-slate-200 border-l-4 border-l-blue-700 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-slate-500 uppercase tracking-wider">Neto a Dispersar (Cta 250505)</div>
          <div class="text-2xl font-extrabold text-slate-900 font-mono tabular-nums mt-1">
            {{ formatCOP(auditResult.totalNetPayDisbursementCOP) }}
          </div>
          <div class="text-[11px] text-slate-600 mt-1">Descontando Salud 4%, Pensión 4% y FSP</div>
        </div>

        <div class="bg-white border border-slate-200 border-l-4 border-l-emerald-600 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-emerald-800 uppercase tracking-wider">Costo Total Real Empresa</div>
          <div class="text-2xl font-extrabold text-emerald-700 font-mono tabular-nums mt-1">
            {{ formatCOP(auditResult.totalEmployerRealCostCOP) }}
          </div>
          <div class="text-[11px] text-slate-600 mt-1">Incluye PILA + Parafiscales + Provisiones</div>
        </div>
      </div>

      <!-- Matriz de Auditoría UGPP Empleado por Empleado -->
      <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-3">
        <div class="flex items-center gap-2 border-b border-slate-100 pb-2.5">
          <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
            PT-02B
          </span>
          <h4 class="text-sm font-bold text-slate-900">
            Hoja de Trabajo de Auditoría UGPP (Tope 40% No Salarial — Art. 30 Ley 1393 de 2010) y Costo Patronal
          </h4>
        </div>

        <div class="overflow-x-auto border border-slate-200 rounded-lg">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-100 border-b-2 border-slate-300 text-slate-700 uppercase text-[11px] font-bold tracking-wider">
                <th class="py-2.5 px-2.5">Trabajador</th>
                <th class="py-2.5 px-2.5 text-right">Total Salarial</th>
                <th class="py-2.5 px-2.5 text-right">No Salarial</th>
                <th class="py-2.5 px-2.5 text-center">% No Sal.</th>
                <th class="py-2.5 px-2.5 text-right">Exceso &gt;40% UGPP</th>
                <th class="py-2.5 px-2.5 text-right">IBC Real PILA</th>
                <th class="py-2.5 px-2.5 text-right">Deduc. Trab.</th>
                <th class="py-2.5 px-2.5 text-right">Aportes Emp.</th>
                <th class="py-2.5 px-2.5 text-right">Provisiones</th>
                <th class="py-2.5 px-2.5 text-right">Costo Total</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white font-mono tabular-nums">
              <tr
                v-for="r in auditResult.employeeResults"
                :key="r.document"
                class="hover:bg-blue-50/40 transition-colors"
                :class="r.ugppRiskDetected ? 'bg-amber-50/60' : 'even:bg-slate-50/60'"
              >
                <td class="py-2 px-2.5 font-sans">
                  <div class="font-bold text-slate-900">{{ r.employeeName }}</div>
                  <div class="text-[10px] text-slate-500">
                    CC {{ r.document }} • {{ r.exoneratedArt1141 ? 'Exonerado Art. 114-1' : 'Tarifa Plena EPS/ICBF/SENA' }}
                  </div>
                </td>
                <td class="py-2 px-2.5 text-right text-slate-900">{{ formatCOP(r.totalSalaryEarnedCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-slate-800">{{ formatCOP(r.nonSalaryPaymentsArt128COP) }}</td>
                <td class="py-2 px-2.5 text-center">
                  <span
                    class="px-2 py-0.5 rounded font-bold text-[10px] border"
                    :class="
                      r.ugppRiskDetected
                        ? 'bg-red-50 text-red-800 border-red-200'
                        : 'bg-emerald-50 text-emerald-800 border-emerald-200'
                    "
                  >
                    {{ r.nonSalarySharePercent }}%
                  </span>
                </td>
                <td class="py-2 px-2.5 text-right font-bold" :class="r.ugppRiskDetected ? 'text-red-700' : 'text-slate-400'">
                  {{ r.ugppExcessGravadoCOP > 0 ? formatCOP(r.ugppExcessGravadoCOP) : '—' }}
                </td>
                <td class="py-2 px-2.5 text-right text-blue-900 font-bold bg-blue-50/30">{{ formatCOP(r.ibcSeguridadSocialCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-slate-700">-{{ formatCOP(r.totalEmployeeDeductionsCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-slate-800">{{ formatCOP(r.totalEmployerPilaAndParafiscalesCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-slate-800">{{ formatCOP(r.totalMonthlyProvisionsCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-slate-900 font-bold">{{ formatCOP(r.totalEmployerRealMonthlyCostCOP) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Generador Automático del Comprobante Contable PUC (Partida Doble) -->
      <div class="bg-white border border-slate-200 border-t-4 border-t-emerald-700 rounded-xl p-5 shadow-xs space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div>
            <div class="flex flex-wrap items-center gap-2">
              <span class="px-2 py-0.5 rounded bg-emerald-50 text-emerald-800 border border-emerald-200 font-mono text-[10px] font-bold">
                PT-02C • LIBRO DIARIO
              </span>
              <h4 class="text-base font-bold text-slate-900">
                Comprobante Contable de Causación y Provisión de Nómina (PUC Decreto 2650 — 6 Dígitos)
              </h4>
              <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-300 font-mono">
                ✓ PARTIDA DOBLE CUADRADA: {{ formatCOP(auditResult.totalPucDebitCOP) }}
              </span>
            </div>
            <p class="text-xs text-slate-500 mt-0.5">
              Asiento contable listo para copiar y pegar en Siigo Nube, World Office, Alegra, Helisa o Excel con sumas iguales al peso.
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-2 text-xs">
            <button
              @click="copyPucJournalForExcel"
              class="px-3.5 py-2 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white font-semibold transition shadow-2xs"
            >
              <i class="fa-solid fa-copy mr-1"></i>Copiar Asiento PUC (Excel / ERP)
            </button>
            <button
              @click="downloadPucCsv"
              class="px-3.5 py-2 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
            >
              <i class="fa-solid fa-file-csv text-emerald-700 mr-1"></i>Descargar CSV PUC
            </button>
            <button
              @click="savePayrollWorkpaperInMariaDB"
              class="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-900 text-white font-semibold transition"
            >
              <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente Digital
            </button>
          </div>
        </div>

        <div class="overflow-x-auto border border-slate-200 rounded-lg">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-100 border-b-2 border-slate-300 text-slate-700 uppercase text-[11px] font-bold tracking-wider">
                <th class="py-2.5 px-3">Código PUC</th>
                <th class="py-2.5 px-3">Cuenta Contable (Decreto 2650)</th>
                <th class="py-2.5 px-3">Centro / Módulo</th>
                <th class="py-2.5 px-3">Concepto / Soporte Normativo</th>
                <th class="py-2.5 px-3 text-right bg-blue-50/60 text-blue-950">Débito (Cargo 5105/5205)</th>
                <th class="py-2.5 px-3 text-right bg-slate-200/50 text-slate-900">Crédito (Abono 23/25/26)</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white font-mono tabular-nums">
              <tr
                v-for="line in auditResult.pucJournalEntries"
                :key="line.pucCode + line.nature"
                class="even:bg-slate-50/60 hover:bg-blue-50/40 transition-colors"
              >
                <td class="py-2 px-3 font-bold text-blue-800">{{ line.pucCode }}</td>
                <td class="py-2 px-3 font-sans font-semibold text-slate-900">{{ line.accountName }}</td>
                <td class="py-2 px-3 text-[11px] text-slate-600">{{ line.costCenterArea }}</td>
                <td class="py-2 px-3 font-sans text-[11px] text-slate-600">{{ line.accountingNote }}</td>
                <td class="py-2 px-3 text-right font-semibold" :class="line.debitCOP > 0 ? 'text-slate-900 bg-blue-50/20' : 'text-slate-400'">
                  {{ line.debitCOP > 0 ? formatCOP(line.debitCOP) : '—' }}
                </td>
                <td class="py-2 px-3 text-right font-semibold" :class="line.creditCOP > 0 ? 'text-slate-900' : 'text-slate-400'">
                  {{ line.creditCOP > 0 ? formatCOP(line.creditCOP) : '—' }}
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="border-t-2 border-b-4 border-double border-slate-700 bg-slate-100 font-mono tabular-nums font-extrabold text-sm text-slate-900">
                <td colspan="4" class="py-3 px-3 text-right uppercase tracking-wider text-xs text-slate-700">
                  Sumas Iguales Comprobante Contable PUC:
                </td>
                <td class="py-3 px-3 text-right text-slate-950 bg-emerald-50/70">{{ formatCOP(auditResult.totalPucDebitCOP) }}</td>
                <td class="py-3 px-3 text-right text-slate-950 bg-emerald-50/70">{{ formatCOP(auditResult.totalPucCreditCOP) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>

    <!-- Sub-Módulo Complementario: Liquidación Definitiva de Contrato e Indemnización Art. 64 CST -->
    <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-3">
        <div class="flex items-center gap-2">
          <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-300 font-mono text-[10px] font-bold">
            ANEXO CST
          </span>
          <h4 class="text-sm font-bold text-slate-900">
            Liquidación Definitiva por Retiro e Indemnización por Despido sin Justa Causa (Art. 64 CST)
          </h4>
        </div>
        <button
          @click="runIndividualSettlement"
          class="px-3.5 py-1.5 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs shadow-2xs"
        >
          Recalcular Liquidación de Retiro
        </button>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3 text-xs">
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Salario Básico (COP)</label>
          <input
            v-model.number="settlementForm.monthlyBaseSalary"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Variables / Comisiones</label>
          <input
            v-model.number="settlementForm.monthlyVariableCommissions"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Días Totales Antigüedad</label>
          <input
            v-model.number="settlementForm.workedDaysTotal"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Días Año (Cesantías)</label>
          <input
            v-model.number="settlementForm.workedDaysCurrentYear"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Días Semestre (Prima)</label>
          <input
            v-model.number="settlementForm.workedDaysCurrentSemester"
            type="number"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-mono tabular-nums text-right"
          />
        </div>
        <div>
          <label class="block text-slate-700 font-semibold mb-1">Modalidad Contrato</label>
          <select
            v-model="settlementForm.contractType"
            class="w-full bg-white border border-slate-300 rounded-lg px-2.5 py-1.5 text-slate-900 font-medium"
          >
            <option value="INDEFINIDO">Indefinido</option>
            <option value="TERMINO_FIJO">Término Fijo</option>
            <option value="OBRA_LABOR">Obra o Labor</option>
          </select>
        </div>
      </div>

      <div v-if="settlementResult" class="grid grid-cols-2 sm:grid-cols-5 gap-3 text-xs font-mono tabular-nums">
        <div class="bg-slate-50 border border-slate-200 rounded-lg p-3">
          <div class="text-slate-600 font-sans text-[11px] font-medium">Cesantías + Intereses (12%)</div>
          <div class="text-sm font-bold text-slate-900 mt-1">
            {{ formatCOP(settlementResult.severanceCesantiasCOP + settlementResult.severanceInterests12PercentCOP) }}
          </div>
        </div>
        <div class="bg-slate-50 border border-slate-200 rounded-lg p-3">
          <div class="text-slate-600 font-sans text-[11px] font-medium">Prima de Servicios</div>
          <div class="text-sm font-bold text-slate-900 mt-1">{{ formatCOP(settlementResult.serviceBonusPrimaCOP) }}</div>
        </div>
        <div class="bg-slate-50 border border-slate-200 rounded-lg p-3">
          <div class="text-slate-600 font-sans text-[11px] font-medium">Vacaciones (Art. 192 CST)</div>
          <div class="text-sm font-bold text-slate-900 mt-1">{{ formatCOP(settlementResult.vacationsCompensatedCOP) }}</div>
        </div>
        <div class="bg-amber-50/70 border border-amber-200 rounded-lg p-3">
          <div class="text-amber-900 font-sans text-[11px] font-semibold">Indemnización Art. 64 CST</div>
          <div class="text-sm font-bold text-amber-800 mt-1">
            {{ formatCOP(settlementResult.indemnification?.indemnificationAmountCOP || 0) }}
          </div>
        </div>
        <div class="bg-emerald-50 border border-emerald-300 rounded-lg p-3">
          <div class="text-emerald-900 font-sans text-[11px] font-bold">Total Liquidación Retiro</div>
          <div class="text-base font-extrabold text-emerald-800 mt-1">
            {{ formatCOP(settlementResult.grandTotalSettlementToEmployeeCOP) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
