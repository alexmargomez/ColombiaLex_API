<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { apiRequest, formatCOP } from '../services/api';

const nitInput = ref('800.197.268-4');
const nitResult = ref<any>(null);
const nitError = ref('');

const uvtForm = reactive({
  year: 2026,
  amount: 27,
  unit: 'UVT',
});
const uvtResult = ref<any>(null);

const withholdingForm = reactive({
  year: 2026,
  conceptCode: 'COMPRAS_GENERALES',
  subtotalBeforeVatCOP: 3500000,
  declarantTaxpayer: true,
  vatRatePercent: 19,
});
const withholdingResult = ref<any>(null);

const ciiuSearch = ref('');
const ciiuList = ref<any[]>([]);

async function checkNit() {
  nitError.value = '';
  try {
    nitResult.value = await apiRequest(`/api/v1/tax/nit/${encodeURIComponent(nitInput.value)}`);
  } catch (e: any) {
    nitError.value = e.message;
  }
}

async function convertUvt() {
  uvtResult.value = await apiRequest(
    `/api/v1/tax/uvt-convert?year=${uvtForm.year}&amount=${uvtForm.amount}&unit=${uvtForm.unit}`
  );
}

async function calcWithholding() {
  withholdingResult.value = await apiRequest('/api/v1/tax/withholding', {
    method: 'POST',
    body: JSON.stringify(withholdingForm),
  });
}

async function loadCiiu() {
  const res = await apiRequest(`/api/v1/tax/ciiu?q=${encodeURIComponent(ciiuSearch.value)}`);
  ciiuList.value = res.items || [];
}

onMounted(() => {
  checkNit();
  convertUvt();
  calcWithholding();
  loadCiiu();
});
</script>

<template>
  <div class="space-y-6">
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 1. Validador de NIT DIAN Módulo 11 -->
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-fingerprint text-indigo-400"></i>
            Validador y Calculador de NIT DIAN (Algoritmo Módulo 11)
          </h3>
          <p class="text-xs text-slate-400 mt-1">
            Calcula el Dígito de Verificación (DV) aplicando los 15 números primos oficiales de la DIAN.
          </p>
        </div>

        <div class="flex gap-2">
          <input
            v-model="nitInput"
            @keyup.enter="checkNit"
            type="text"
            placeholder="Ej: 800197268 o 890.903.938-8"
            class="flex-1 bg-slate-950 border border-slate-700 rounded-xl px-3.5 py-2 text-white font-mono text-sm"
          />
          <button
            @click="checkNit"
            class="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs transition"
          >
            Validar NIT
          </button>
        </div>

        <div v-if="nitError" class="text-xs text-rose-400 bg-rose-950/30 border border-rose-500/30 rounded-xl p-3">
          {{ nitError }}
        </div>

        <div v-if="nitResult" class="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-3 text-xs">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <div class="text-slate-400 text-[11px]">NIT Formateado Oficial</div>
              <div class="text-xl font-black text-white font-mono">
                {{ nitResult.formattedNitWithDv }}
              </div>
              <div class="text-[11px] text-indigo-400 mt-0.5">{{ nitResult.taxpayerPersonType }}</div>
            </div>
            <div class="text-right">
              <span
                class="px-2.5 py-1 rounded-full text-[11px] font-bold"
                :class="
                  nitResult.validProvidedCheckDigit
                    ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                    : 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                "
              >
                DV Oficial: {{ nitResult.calculatedCheckDigit }}
              </span>
            </div>
          </div>

          <div class="text-[11px] text-slate-400 font-mono bg-slate-900 rounded-lg p-2.5">
            {{ nitResult.formulaExplanation }}
          </div>

          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="w in nitResult.stepByStepWeights"
              :key="w.positionFromRight"
              class="px-2 py-1 rounded bg-slate-900 border border-slate-800 font-mono text-[10px] text-slate-300"
            >
              {{ w.digit }}×{{ w.primeWeight }}={{ w.product }}
            </span>
          </div>
        </div>
      </div>

      <!-- 2. Conversor UVT <-> COP <-> SMLMV -->
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-coins text-amber-400"></i>
            Conversor Tributario Histórico (UVT ↔ COP ↔ SMLMV)
          </h3>
          <p class="text-xs text-slate-400 mt-1">
            Convierte al instante entre Unidades de Valor Tributario, Pesos y Salarios Mínimos.
          </p>
        </div>

        <div class="grid grid-cols-3 gap-2 text-xs">
          <div>
            <label class="block text-slate-400 mb-1">Año Fiscal</label>
            <select
              v-model.number="uvtForm.year"
              @change="convertUvt"
              class="w-full bg-slate-950 border border-slate-700 rounded-xl px-2.5 py-2 text-white"
            >
              <option :value="2026">2026</option>
              <option :value="2025">2025</option>
              <option :value="2024">2024</option>
              <option :value="2023">2023</option>
              <option :value="2022">2022</option>
            </select>
          </div>
          <div>
            <label class="block text-slate-400 mb-1">Cantidad</label>
            <input
              v-model.number="uvtForm.amount"
              @input="convertUvt"
              type="number"
              min="0"
              class="w-full bg-slate-950 border border-slate-700 rounded-xl px-2.5 py-2 text-white font-mono"
            />
          </div>
          <div>
            <label class="block text-slate-400 mb-1">Unidad Origen</label>
            <select
              v-model="uvtForm.unit"
              @change="convertUvt"
              class="w-full bg-slate-950 border border-slate-700 rounded-xl px-2.5 py-2 text-white"
            >
              <option value="UVT">UVT</option>
              <option value="COP">Pesos (COP)</option>
              <option value="SMLMV">SMLMV</option>
            </select>
          </div>
        </div>

        <div v-if="uvtResult" class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs pt-1">
          <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
            <div class="text-slate-400 text-[11px]">Equivalente en Pesos</div>
            <div class="text-base font-black text-emerald-400 font-mono mt-1">
              {{ formatCOP(uvtResult.equivalentCOP) }}
            </div>
          </div>
          <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
            <div class="text-slate-400 text-[11px]">Equivalente en UVT</div>
            <div class="text-base font-black text-amber-400 font-mono mt-1">
              {{ uvtResult.equivalentUVT }} UVT
            </div>
            <div class="text-[10px] text-slate-500">1 UVT = {{ formatCOP(uvtResult.uvtValueCOP) }}</div>
          </div>
          <div class="bg-slate-950 border border-slate-800 rounded-xl p-3">
            <div class="text-slate-400 text-[11px]">Equivalente en SMLMV</div>
            <div class="text-base font-black text-indigo-300 font-mono mt-1">
              {{ uvtResult.equivalentSMLMV }} SM
            </div>
            <div class="text-[10px] text-slate-500">1 SM = {{ formatCOP(uvtResult.smlmvValueCOP) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. Calculadora de Retención en la Fuente e IVA -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-percent text-emerald-400"></i>
            Calculadora de Retención en la Fuente e IVA por Topes UVT (Estatuto Tributario)
          </h3>
          <p class="text-xs text-slate-400">
            Evalúa automáticamente si la operación supera la base mínima en UVT y liquida Retefuente + IVA.
          </p>
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-5 gap-3 text-xs">
        <div class="sm:col-span-2">
          <label class="block text-slate-400 mb-1">Concepto de Retención DIAN</label>
          <select
            v-model="withholdingForm.conceptCode"
            @change="calcWithholding"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
          >
            <option value="COMPRAS_GENERALES">Compras Generales (27 UVT - 2.5% / 3.5%)</option>
            <option value="SERVICIOS_GENERALES">Servicios en General (4 UVT - 4% / 6%)</option>
            <option value="HONORARIOS_COMISIONES">Honorarios Profesionales (0 UVT - 11% / 10%)</option>
            <option value="ARRENDAMIENTO_INMUEBLES">Arrendamiento Inmuebles (27 UVT - 3.5%)</option>
            <option value="TRANSPORTE_CARGA">Transporte de Carga (4 UVT - 1%)</option>
            <option value="LICENCIAMIENTO_SOFTWARE">Software / Servicios TI (0 UVT - 3.5%)</option>
          </select>
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Subtotal Antes de IVA (COP)</label>
          <input
            v-model.number="withholdingForm.subtotalBeforeVatCOP"
            @input="calcWithholding"
            type="number"
            step="100000"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white font-mono"
          />
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Tarifa IVA (%)</label>
          <select
            v-model.number="withholdingForm.vatRatePercent"
            @change="calcWithholding"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
          >
            <option :value="19">19% (General)</option>
            <option :value="5">5% (Reducido)</option>
            <option :value="0">0% (Exento/Excluido)</option>
          </select>
        </div>
        <div>
          <label class="block text-slate-400 mb-1">Calidad del Proveedor</label>
          <select
            v-model="withholdingForm.declarantTaxpayer"
            @change="calcWithholding"
            class="w-full bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
          >
            <option :value="true">Declarante de Renta</option>
            <option :value="false">No Declarante</option>
          </select>
        </div>
      </div>

      <div
        v-if="withholdingResult"
        class="bg-slate-950 border border-slate-800 rounded-xl p-4 flex flex-wrap items-center justify-between gap-4 text-xs"
      >
        <div class="space-y-1">
          <div class="font-semibold text-white">{{ withholdingResult.explanation }}</div>
          <div class="text-slate-400 font-mono">
            Base Mínima: {{ withholdingResult.minimumBaseUVT }} UVT ({{ formatCOP(withholdingResult.minimumBaseCOP) }}) • IVA:
            +{{ formatCOP(withholdingResult.vatAmountCOP) }} • Retefuente ({{ withholdingResult.withholdingRatePercent }}%):
            <span class="text-rose-400 font-bold">-{{ formatCOP(withholdingResult.withholdingAmountCOP) }}</span>
          </div>
        </div>
        <div class="text-right">
          <div class="text-[11px] text-slate-400">Neto a Girar al Proveedor</div>
          <div class="text-xl font-black text-emerald-400 font-mono">
            {{ formatCOP(withholdingResult.netPayableToProviderCOP) }}
          </div>
        </div>
      </div>
    </div>

    <!-- 4. Buscador CIIU -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-base font-bold text-white">
            <i class="fa-solid fa-briefcase text-cyan-400 mr-2"></i>Catálogo de Actividades Económicas CIIU Rev. 4 A.C.
          </h3>
        </div>
        <input
          v-model="ciiuSearch"
          @input="loadCiiu"
          type="text"
          placeholder="Buscar código (ej. 6201, 8610) o actividad..."
          class="w-72 bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-xs text-white"
        />
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-2.5 text-xs">
        <div
          v-for="item in ciiuList"
          :key="item.code"
          class="bg-slate-950 border border-slate-800 rounded-xl p-3 flex items-start justify-between gap-3"
        >
          <div>
            <div class="flex items-center gap-2">
              <span class="px-2 py-0.5 rounded bg-cyan-500/15 text-cyan-300 font-mono font-bold">
                CIIU {{ item.code }}
              </span>
              <span class="text-[11px] text-slate-400">{{ item.section }}</span>
            </div>
            <div class="text-slate-200 mt-1">{{ item.description }}</div>
          </div>
          <div class="text-right shrink-0 font-mono text-[11px] text-amber-300">
            ICA Ref: {{ item.referenceIcaPerThousand }}‰
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
