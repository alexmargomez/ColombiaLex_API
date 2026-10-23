<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { apiRequest, formatCOP } from '../services/api';

const loading = ref(false);
const errorMsg = ref('');
const result = ref<any>(null);

const form = reactive({
  year: 2026,
  monthlyBaseSalary: 2200000,
  monthlyVariableCommissions: 0,
  workedDaysTotal: 540,
  workedDaysCurrentYear: 180,
  workedDaysCurrentSemester: 180,
  pendingVacationDaysAccounted: 360,
  contractType: 'INDEFINIDO',
  dismissalWithoutJustCause: true,
  remainingDaysFixedTerm: 90,
  arlRiskClass: 'I',
  applyArt114Exoneration: true,
});

async function calculateSettlement() {
  loading.value = true;
  errorMsg.value = '';
  try {
    result.value = await apiRequest('/api/v1/labor/settlement', {
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
  calculateSettlement();
});
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Formulario de Liquidación -->
    <div class="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-scale-balanced text-emerald-400"></i>
          Liquidador de Prestaciones Sociales, Seguridad Social &amp; Art. 64 CST
        </h3>
        <p class="text-xs text-slate-400 mt-1">
          Calcula Cesantías, Intereses (12%), Prima, Vacaciones, Aportes PILA e Indemnización por
          despido sin justa causa.
        </p>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
        <div>
          <label class="block text-slate-300 font-semibold mb-1">Año Vigencia Legal</label>
          <select
            v-model.number="form.year"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
          >
            <option :value="2026">2026 (SMLMV $1.550.000)</option>
            <option :value="2025">2025 (SMLMV $1.423.500)</option>
            <option :value="2024">2024 (SMLMV $1.300.000)</option>
            <option :value="2023">2023 (SMLMV $1.160.000)</option>
          </select>
        </div>

        <div>
          <label class="block text-slate-300 font-semibold mb-1">Modalidad Contrato</label>
          <select
            v-model="form.contractType"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
          >
            <option value="INDEFINIDO">Término Indefinido</option>
            <option value="TERMINO_FIJO">Término Fijo</option>
            <option value="OBRA_LABOR">Obra o Labor</option>
          </select>
        </div>

        <div>
          <label class="block text-slate-300 font-semibold mb-1">Salario Básico Mensual (COP)</label>
          <input
            v-model.number="form.monthlyBaseSalary"
            type="number"
            min="100000"
            step="50000"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Promedio Variables / Comisiones</label>
          <input
            v-model.number="form.monthlyVariableCommissions"
            type="number"
            min="0"
            step="20000"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Días Totales Contrato (Histórico)</label>
          <input
            v-model.number="form.workedDaysTotal"
            type="number"
            min="1"
            max="15000"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Días Causados Año (Cesantías)</label>
          <input
            v-model.number="form.workedDaysCurrentYear"
            type="number"
            min="1"
            max="360"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Días Semestre (Prima Servicios)</label>
          <input
            v-model.number="form.workedDaysCurrentSemester"
            type="number"
            min="1"
            max="180"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Días Base Vacaciones Pendientes</label>
          <input
            v-model.number="form.pendingVacationDaysAccounted"
            type="number"
            min="1"
            max="3600"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div>
          <label class="block text-slate-400 mb-1">Clase de Riesgo ARL</label>
          <select
            v-model="form.arlRiskClass"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
          >
            <option value="I">Riesgo I (0.522% - Oficina/Software)</option>
            <option value="II">Riesgo II (1.044% - Comercial)</option>
            <option value="III">Riesgo III (2.436% - Manufactura)</option>
            <option value="IV">Riesgo IV (4.350% - Transporte)</option>
            <option value="V">Riesgo V (6.960% - Construcción/Pesado)</option>
          </select>
        </div>

        <div v-if="form.contractType !== 'INDEFINIDO'">
          <label class="block text-slate-400 mb-1">Días Faltantes Plazo Fijo / Obra</label>
          <input
            v-model.number="form.remainingDaysFixedTerm"
            type="number"
            min="1"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>

        <div class="sm:col-span-2 flex items-center gap-4 pt-1">
          <label class="flex items-center gap-2 cursor-pointer text-slate-300">
            <input v-model="form.dismissalWithoutJustCause" type="checkbox" class="accent-amber-500" />
            <span>Incluir Indemnización por Despido sin Justa Causa (Art. 64 CST)</span>
          </label>
        </div>
      </div>

      <button
        @click="calculateSettlement"
        :disabled="loading"
        class="w-full py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition"
      >
        <i class="fa-solid fa-file-invoice-dollar mr-1.5"></i>
        {{ loading ? 'Liquidando...' : 'Ejecutar Liquidación Legal Completa' }}
      </button>
    </div>

    <!-- Reporte de Liquidación -->
    <div v-if="result" class="lg:col-span-7 space-y-4">
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <span
              class="px-2.5 py-0.5 rounded-full text-[11px] font-bold border"
              :class="
                result.eligibleForTransportAllowance
                  ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30'
                  : 'bg-slate-800 text-slate-300 border-slate-700'
              "
            >
              {{
                result.eligibleForTransportAllowance
                  ? `Aplica Aux. Transporte (${formatCOP(result.transportAllowanceAppliedCOP)})`
                  : 'Sin Aux. Transporte (> 2 SMLMV)'
              }}
            </span>
            <h4 class="text-sm font-bold text-white mt-2">
              Resumen de Prestaciones Sociales e Indemnización ({{ result.referenceYear }})
            </h4>
          </div>
          <div class="text-right">
            <div class="text-[11px] text-slate-400">Total Liquidación al Trabajador</div>
            <div class="text-xl font-black text-emerald-400 font-mono">
              {{ formatCOP(result.grandTotalSettlementToEmployeeCOP) }}
            </div>
          </div>
        </div>

        <!-- Fórmulas Paso a Paso -->
        <div class="space-y-2">
          <div
            v-for="step in result.formulaBreakdown"
            :key="step.code"
            class="bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-xs"
          >
            <div>
              <div class="font-bold text-white">{{ step.title }}</div>
              <div class="text-[11px] text-slate-400 font-mono mt-0.5">
                {{ step.formulaApplied }} • <span class="text-indigo-400">{{ step.legalReference }}</span>
              </div>
            </div>
            <div class="text-sm font-bold text-emerald-400 font-mono">
              {{ formatCOP(step.amountCOP) }}
            </div>
          </div>
        </div>

        <!-- Seguridad Social Mensual -->
        <div class="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-3 text-xs">
          <div class="flex items-center justify-between">
            <h5 class="font-bold text-indigo-300">
              <i class="fa-solid fa-shield-heart mr-1.5"></i>Aportes Mensuales a Seguridad Social y Parafiscales (IBC:
              {{ formatCOP(result.monthlySocialSecurity.ibcCOP) }})
            </h5>
            <span
              v-if="result.monthlySocialSecurity.exoneratedArt114ET"
              class="px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-300 text-[10px] font-bold"
            >
              Exonerado Art. 114-1 E.T. (&lt; 10 SMLMV)
            </span>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="space-y-1">
              <div class="text-slate-400 font-semibold">Deducciones Trabajador:</div>
              <div class="flex justify-between">
                <span>Salud (4%):</span>
                <span class="font-mono text-white">{{ formatCOP(result.monthlySocialSecurity.employeeHealth4PercentCOP) }}</span>
              </div>
              <div class="flex justify-between">
                <span>Pensión (4%):</span>
                <span class="font-mono text-white">{{ formatCOP(result.monthlySocialSecurity.employeePension4PercentCOP) }}</span>
              </div>
              <div class="flex justify-between">
                <span>Fondo Solidaridad ({{ result.monthlySocialSecurity.solidarityFundRate }}%):</span>
                <span class="font-mono text-white">{{ formatCOP(result.monthlySocialSecurity.employeeSolidarityFundCOP) }}</span>
              </div>
              <div class="flex justify-between border-t border-slate-800 pt-1 font-bold text-rose-400">
                <span>Total Deducción Nómina:</span>
                <span class="font-mono">{{ formatCOP(result.monthlySocialSecurity.totalEmployeeDeductionsCOP) }}</span>
              </div>
            </div>

            <div class="space-y-1">
              <div class="text-slate-400 font-semibold">Aportes Empleador:</div>
              <div class="flex justify-between">
                <span>Pensión (12%) + Salud:</span>
                <span class="font-mono text-white">
                  {{ formatCOP(result.monthlySocialSecurity.employerPension12PercentCOP + result.monthlySocialSecurity.employerHealthCOP) }}
                </span>
              </div>
              <div class="flex justify-between">
                <span>ARL Riesgo {{ result.monthlySocialSecurity.arlRiskClass }} ({{ result.monthlySocialSecurity.arlRate }}%):</span>
                <span class="font-mono text-white">{{ formatCOP(result.monthlySocialSecurity.employerArlCOP) }}</span>
              </div>
              <div class="flex justify-between">
                <span>Caja (4%) + ICBF + SENA:</span>
                <span class="font-mono text-white">
                  {{
                    formatCOP(
                      result.monthlySocialSecurity.employerCompensationFund4PercentCOP +
                        result.monthlySocialSecurity.employerIcbf3PercentCOP +
                        result.monthlySocialSecurity.employerSena2PercentCOP
                    )
                  }}
                </span>
              </div>
              <div class="flex justify-between border-t border-slate-800 pt-1 font-bold text-amber-400">
                <span>Total Carga Patronal:</span>
                <span class="font-mono">{{ formatCOP(result.monthlySocialSecurity.totalEmployerMonthlyContributionsCOP) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
