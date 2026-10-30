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
    <!-- Banner Superior de Configuración de Planilla y Acciones en Lote -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-users-gear text-emerald-400"></i>
            Auditor Masivo de Nómina en Lote, Tope 40% UGPP (Ley 1393 Art. 30) &amp; Comprobante Contable PUC
          </h3>
          <p class="text-xs text-slate-400 mt-0.5">
            Audita toda la planilla de la empresa a la vez, detecta excesos no salariales gravados por la UGPP y genera el Asiento Contable en cuentas PUC de 6 dígitos cuadrado al peso.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <select v-model.number="year" @change="runPayrollBatchAudit" class="bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono">
            <option :value="2026">Vigencia 2026</option>
            <option :value="2025">Vigencia 2025</option>
            <option :value="2024">Vigencia 2024</option>
          </select>

          <label class="flex items-center gap-2 bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-slate-200 cursor-pointer">
            <input v-model="applyArt114" @change="runPayrollBatchAudit" type="checkbox" class="accent-emerald-500" />
            <span>Exoneración Art. 114-1 E.T. (&lt; 10 SMLMV)</span>
          </label>

          <button @click="showPasteBox = !showPasteBox" class="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-amber-300 font-semibold transition">
            <i class="fa-solid fa-paste mr-1"></i>Pegar Nómina de Excel
          </button>

          <button @click="addEmployeeRow" class="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white font-semibold transition">
            <i class="fa-solid fa-user-plus mr-1"></i>+ Empleado
          </button>

          <button @click="runPayrollBatchAudit" class="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold transition">
            <i class="fa-solid fa-bolt mr-1"></i>Auditar Planilla y Generar PUC
          </button>
        </div>
      </div>

      <!-- Caja de Pegado desde Excel -->
      <div v-if="showPasteBox" class="bg-slate-950 border border-amber-500/40 rounded-xl p-4 space-y-2 text-xs">
        <div class="text-amber-300 font-semibold">
          Pega aquí las filas desde Excel (Columnas: Documento | Nombre | Área [ADMIN/VENTAS] | Salario Básico | Horas Extra | Comisiones | Pagos No Salariales | Riesgo ARL):
        </div>
        <textarea
          v-model="rawPastePayroll"
          rows="4"
          placeholder="79456120&#9;Carlos Ruiz&#9;ADMINISTRACION&#9;3200000&#9;0&#9;0&#9;3800000&#9;I"
          class="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-white font-mono text-xs"
        ></textarea>
        <div class="flex justify-end gap-2">
          <button @click="parsePastedPayrollFromExcel" class="px-4 py-2 rounded-lg bg-amber-500 text-slate-950 font-bold">
            Procesar Lote Pegado
          </button>
        </div>
      </div>

      <div v-if="feedbackMsg" class="bg-emerald-950/40 border border-emerald-500/40 text-emerald-300 text-xs rounded-xl p-3 flex items-center justify-between">
        <span><i class="fa-solid fa-circle-check mr-1.5"></i>{{ feedbackMsg }}</span>
        <button @click="feedbackMsg = ''" class="text-slate-400 hover:text-white">✕</button>
      </div>
      <div v-if="errorMsg" class="bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs rounded-xl p-3">
        {{ errorMsg }}
      </div>

      <!-- Grilla Editable de Planilla de Empleados -->
      <div class="overflow-x-auto">
        <table class="w-full text-left border-collapse text-xs">
          <thead>
            <tr class="border-b border-slate-800 text-slate-400 uppercase text-[10px]">
              <th class="py-2 px-2">Cédula</th>
              <th class="py-2 px-2">Empleado</th>
              <th class="py-2 px-2">Área PUC</th>
              <th class="py-2 px-2">Salario Básico</th>
              <th class="py-2 px-2">H. Extra / Recargos</th>
              <th class="py-2 px-2">Comisiones</th>
              <th class="py-2 px-2 text-amber-300">Pagos No Salariales (Art. 128)</th>
              <th class="py-2 px-2">ARL</th>
              <th class="py-2 px-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-800/60">
            <tr v-for="(emp, idx) in employees" :key="idx">
              <td class="py-1.5 px-2">
                <input v-model="emp.document" class="w-24 bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white font-mono" />
              </td>
              <td class="py-1.5 px-2">
                <input v-model="emp.employeeName" class="w-56 bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white" />
              </td>
              <td class="py-1.5 px-2">
                <select v-model="emp.area" class="bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white">
                  <option value="ADMINISTRACION">5105 Admin</option>
                  <option value="VENTAS">5205 Ventas</option>
                </select>
              </td>
              <td class="py-1.5 px-2">
                <input v-model.number="emp.baseSalaryCOP" type="number" step="100000" class="w-28 bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white font-mono" />
              </td>
              <td class="py-1.5 px-2">
                <input v-model.number="emp.overtimeAndSurchargesCOP" type="number" step="50000" class="w-24 bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white font-mono" />
              </td>
              <td class="py-1.5 px-2">
                <input v-model.number="emp.commissionsCOP" type="number" step="50000" class="w-24 bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white font-mono" />
              </td>
              <td class="py-1.5 px-2">
                <input v-model.number="emp.nonSalaryPaymentsArt128COP" type="number" step="100000" class="w-28 bg-slate-950 border border-amber-500/40 rounded px-2 py-1 text-amber-300 font-mono" />
              </td>
              <td class="py-1.5 px-2">
                <select v-model="emp.arlRiskClass" class="bg-slate-950 border border-slate-700 rounded px-2 py-1 text-white font-mono">
                  <option value="I">I</option>
                  <option value="II">II</option>
                  <option value="III">III</option>
                  <option value="IV">IV</option>
                  <option value="V">V</option>
                </select>
              </td>
              <td class="py-1.5 px-2 text-right">
                <button @click="removeEmployeeRow(idx)" class="text-slate-500 hover:text-rose-400">✕</button>
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
        <div class="bg-slate-900 border border-rose-500/40 rounded-2xl p-4">
          <div class="text-xs text-rose-300 font-semibold">Riesgo Fiscalización UGPP (Ley 1393)</div>
          <div class="text-2xl font-black text-rose-400 font-mono mt-1">
            {{ auditResult.employeesWithUgppRiskCount }} / {{ auditResult.employeesProcessed }} emp
          </div>
          <div class="text-[11px] text-rose-300/80 font-mono mt-1">
            Exceso &gt;40% gravado: {{ formatCOP(auditResult.totalUgppExcessGravadoCOP) }}
          </div>
        </div>

        <div class="bg-slate-900 border border-amber-500/40 rounded-2xl p-4">
          <div class="text-xs text-amber-300 font-semibold">Aporte PILA Omitido si Ignora UGPP</div>
          <div class="text-2xl font-black text-amber-400 font-mono mt-1">
            {{ formatCOP(auditResult.totalMonthlyOmittedPilaRiskCOP) }}/mes
          </div>
          <div class="text-[11px] text-slate-400 font-mono mt-1">
            Contingencia anual: {{ formatCOP(auditResult.totalAnnualOmittedPilaRiskCOP) }}
          </div>
        </div>

        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <div class="text-xs text-slate-400">Neto a Dispersar en Bancos (Cuenta 250505)</div>
          <div class="text-2xl font-black text-white font-mono mt-1">
            {{ formatCOP(auditResult.totalNetPayDisbursementCOP) }}
          </div>
          <div class="text-[11px] text-emerald-400 mt-1">Descontando Salud 4%, Pensión 4% y FSP</div>
        </div>

        <div class="bg-slate-900 border border-emerald-500/40 rounded-2xl p-4">
          <div class="text-xs text-emerald-300 font-semibold">Costo Total Real Empresa (Mes)</div>
          <div class="text-2xl font-black text-emerald-400 font-mono mt-1">
            {{ formatCOP(auditResult.totalEmployerRealCostCOP) }}
          </div>
          <div class="text-[11px] text-slate-400 mt-1">Incluye PILA + Parafiscales + Provisiones</div>
        </div>
      </div>

      <!-- Matriz de Auditoría UGPP Empleado por Empleado -->
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-3">
        <h4 class="text-sm font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-magnifying-glass-dollar text-rose-400"></i>
          Hoja de Trabajo de Auditoría UGPP (Tope 40% No Salarial — Art. 30 Ley 1393 de 2010) y Costo Patronal
        </h4>

        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="border-b border-slate-800 text-slate-400 uppercase text-[10px]">
                <th class="py-2 px-2.5">Empleado</th>
                <th class="py-2 px-2.5 text-right">Total Salarial</th>
                <th class="py-2 px-2.5 text-right">No Salarial</th>
                <th class="py-2 px-2.5 text-center">% No Sal.</th>
                <th class="py-2 px-2.5 text-right">Exceso &gt;40% UGPP</th>
                <th class="py-2 px-2.5 text-right">IBC Real PILA</th>
                <th class="py-2 px-2.5 text-right">Deduc. Emp.</th>
                <th class="py-2 px-2.5 text-right">Aportes Emp.</th>
                <th class="py-2 px-2.5 text-right">Provisiones</th>
                <th class="py-2 px-2.5 text-right">Costo Total</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/70 font-mono">
              <tr
                v-for="r in auditResult.employeeResults"
                :key="r.document"
                :class="r.ugppRiskDetected ? 'bg-rose-950/20' : ''"
              >
                <td class="py-2 px-2.5 font-sans">
                  <div class="font-bold text-white">{{ r.employeeName }}</div>
                  <div class="text-[10px] text-slate-400">
                    CC {{ r.document }} • {{ r.exoneratedArt1141 ? 'Exonerado Art. 114-1' : 'Tarifa Plena EPS/ICBF/SENA' }}
                  </div>
                </td>
                <td class="py-2 px-2.5 text-right text-white">{{ formatCOP(r.totalSalaryEarnedCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-amber-300">{{ formatCOP(r.nonSalaryPaymentsArt128COP) }}</td>
                <td class="py-2 px-2.5 text-center">
                  <span
                    class="px-2 py-0.5 rounded font-bold text-[10px]"
                    :class="
                      r.ugppRiskDetected
                        ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40'
                        : 'bg-emerald-500/15 text-emerald-300'
                    "
                  >
                    {{ r.nonSalarySharePercent }}%
                  </span>
                </td>
                <td class="py-2 px-2.5 text-right font-bold" :class="r.ugppRiskDetected ? 'text-rose-400' : 'text-slate-500'">
                  {{ formatCOP(r.ugppExcessGravadoCOP) }}
                </td>
                <td class="py-2 px-2.5 text-right text-cyan-300 font-bold">{{ formatCOP(r.ibcSeguridadSocialCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-rose-300">-{{ formatCOP(r.totalEmployeeDeductionsCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-amber-300">{{ formatCOP(r.totalEmployerPilaAndParafiscalesCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-indigo-300">{{ formatCOP(r.totalMonthlyProvisionsCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-emerald-400 font-bold">{{ formatCOP(r.totalEmployerRealMonthlyCostCOP) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Generador Automático del Comprobante Contable PUC (Partida Doble) -->
      <div class="bg-slate-900 border border-emerald-500/30 rounded-2xl p-5 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="flex items-center gap-2">
              <h4 class="text-base font-bold text-white">
                <i class="fa-solid fa-book-journal-whills text-emerald-400 mr-1.5"></i>
                Comprobante Contable de Causación y Provisión de Nómina (PUC Colombiano — 6 Dígitos)
              </h4>
              <span
                class="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/15 text-emerald-300 border border-emerald-500/30"
              >
                ✅ PARTIDA DOBLE CUADRADA: {{ formatCOP(auditResult.totalPucDebitCOP) }}
              </span>
            </div>
            <p class="text-xs text-slate-400 mt-0.5">
              Listo para copiar y pegar en Siigo, World Office, Alegra, Helisa o Excel con sumas iguales al peso exacto.
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-2 text-xs">
            <button
              @click="copyPucJournalForExcel"
              class="px-3.5 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold transition"
            >
              <i class="fa-solid fa-copy mr-1"></i>Copiar Asiento PUC para Excel / ERP
            </button>
            <button
              @click="downloadPucCsv"
              class="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-cyan-300 font-semibold transition"
            >
              <i class="fa-solid fa-file-csv mr-1"></i>Descargar CSV PUC
            </button>
            <button
              @click="savePayrollWorkpaperInMariaDB"
              class="px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold transition"
            >
              <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente Cliente (MariaDB)
            </button>
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="border-b border-slate-800 text-slate-400 uppercase text-[10px]">
                <th class="py-2 px-3">Código PUC</th>
                <th class="py-2 px-3">Cuenta Contable (Decreto 2650)</th>
                <th class="py-2 px-3">Centro / Módulo</th>
                <th class="py-2 px-3">Concepto / Norma</th>
                <th class="py-2 px-3 text-right">Débito (Gasto 5105)</th>
                <th class="py-2 px-3 text-right">Crédito (Pasivo 23/25/26)</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/60 font-mono">
              <tr v-for="line in auditResult.pucJournalEntries" :key="line.pucCode + line.nature" class="hover:bg-slate-950/60">
                <td class="py-2 px-3 font-bold text-amber-300">{{ line.pucCode }}</td>
                <td class="py-2 px-3 font-sans font-semibold text-white">{{ line.accountName }}</td>
                <td class="py-2 px-3 text-[11px] text-slate-400">{{ line.costCenterArea }}</td>
                <td class="py-2 px-3 font-sans text-[11px] text-slate-400">{{ line.accountingNote }}</td>
                <td class="py-2 px-3 text-right font-bold" :class="line.debitCOP > 0 ? 'text-emerald-400' : 'text-slate-600'">
                  {{ line.debitCOP > 0 ? formatCOP(line.debitCOP) : '—' }}
                </td>
                <td class="py-2 px-3 text-right font-bold" :class="line.creditCOP > 0 ? 'text-cyan-300' : 'text-slate-600'">
                  {{ line.creditCOP > 0 ? formatCOP(line.creditCOP) : '—' }}
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="border-t-2 border-emerald-500/40 bg-slate-950 font-mono font-black text-sm">
                <td colspan="4" class="py-3 px-3 text-right text-white">SUMAS IGUALES COMPROBANTE CONTABLE PUC:</td>
                <td class="py-3 px-3 text-right text-emerald-400">{{ formatCOP(auditResult.totalPucDebitCOP) }}</td>
                <td class="py-3 px-3 text-right text-cyan-300">{{ formatCOP(auditResult.totalPucCreditCOP) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>

    <!-- Sub-Módulo Complementario: Liquidación Definitiva de Contrato e Indemnización Art. 64 CST -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h4 class="text-sm font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-scale-balanced text-indigo-400"></i>
            Simulador de Liquidación Definitiva por Retiro e Indemnización por Despido sin Justa Causa (Art. 64 CST)
          </h4>
        </div>
        <button @click="runIndividualSettlement" class="px-3.5 py-1.5 rounded-xl bg-indigo-500 hover:bg-indigo-400 text-white font-bold text-xs">
          Recalcular Liquidación Retiro
        </button>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3 text-xs">
        <div>
          <label class="block text-slate-400 mb-1">Salario Básico (COP)</label>
          <input v-model.number="settlementForm.monthlyBaseSalary" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Variables / Comisiones</label>
          <input v-model.number="settlementForm.monthlyVariableCommissions" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Días Totales Antigüedad</label>
          <input v-model.number="settlementForm.workedDaysTotal" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Días Año (Cesantías)</label>
          <input v-model.number="settlementForm.workedDaysCurrentYear" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Días Semestre (Prima)</label>
          <input v-model.number="settlementForm.workedDaysCurrentSemester" type="number" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white font-mono" />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Contrato</label>
          <select v-model="settlementForm.contractType" class="w-full bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-1.5 text-white">
            <option value="INDEFINIDO">Indefinido</option>
            <option value="TERMINO_FIJO">Término Fijo</option>
            <option value="OBRA_LABOR">Obra o Labor</option>
          </select>
        </div>
      </div>

      <div v-if="settlementResult" class="grid grid-cols-2 sm:grid-cols-5 gap-3 text-xs font-mono">
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
          <div class="text-slate-400 font-sans text-[11px]">Cesantías + Intereses (12%)</div>
          <div class="text-sm font-bold text-white mt-1">
            {{ formatCOP(settlementResult.severanceCesantiasCOP + settlementResult.severanceInterests12PercentCOP) }}
          </div>
        </div>
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
          <div class="text-slate-400 font-sans text-[11px]">Prima de Servicios</div>
          <div class="text-sm font-bold text-white mt-1">{{ formatCOP(settlementResult.serviceBonusPrimaCOP) }}</div>
        </div>
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
          <div class="text-slate-400 font-sans text-[11px]">Vacaciones (Art. 192 CST)</div>
          <div class="text-sm font-bold text-white mt-1">{{ formatCOP(settlementResult.vacationsCompensatedCOP) }}</div>
        </div>
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
          <div class="text-slate-400 font-sans text-[11px]">Indemnización Art. 64 CST</div>
          <div class="text-sm font-bold text-amber-400 mt-1">
            {{ formatCOP(settlementResult.indemnification?.indemnificationAmountCOP || 0) }}
          </div>
        </div>
        <div class="bg-slate-950 border border-emerald-500/40 rounded-xl p-3">
          <div class="text-emerald-300 font-sans text-[11px] font-semibold">Total Liquidación Retiro</div>
          <div class="text-base font-black text-emerald-400 mt-1">
            {{ formatCOP(settlementResult.grandTotalSettlementToEmployeeCOP) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
