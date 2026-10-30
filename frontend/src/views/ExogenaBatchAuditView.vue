<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiRequest, copyRowsAsTsvForExcel, exportRowsToCsv, formatCOP } from '../services/api';

const props = defineProps<{
  activeClient?: any | null;
}>();

const year = ref(2026);
const loading = ref(false);
const errorMsg = ref('');
const feedbackMsg = ref('');
const auditResult = ref<any>(null);

const sampleDirtyExogenaBatch = `800.197.268-9\tPROVEEDOR TECNOLOGICO CON DV ERRONEO SAS\tSERVICIOS\tSI\t8500000\t120000\tMedellin
NIT 890903938\tBANCOLOMBIA S.A. (SIN DV)\tCOMPRAS\tSI\t4200000\t105000\tMedellin
900.456.123-7\tCONSULTORES LEGALES Y TRIBUTARIOS SAS\tHONORARIOS\tSI\t6000000\t350000\tBogota
CC 79.456.123\tJUAN CARLOS PEREZ (MANTENIMIENTO)\tSERVICIOS\tNO\t1800000\t108000\tCali
901.222.333-1\tINMOBILIARIA DEL ORIENTE SAS\tARRENDAMIENTO_INMUEBLE\tSI\t5400000\t189000\tBucaramanga
860.002.964-4\tTRANSPORTES NACIONALES DE CARGA SA\tTRANSPORTE_CARGA\tSI\t3900000\t39000\tBarranquilla
900.888.777-0\tPAPELERIA MENOR CUANTIA SAS\tCOMPRAS\tSI\t95000\t0\tZapatoca
901.999.111-5\tSUMINISTROS INDUSTRIALES SIN RETENCION SAS\tCOMPRAS\tSI\t7500000\t0\tBogota`;

const rawBatchText = ref(sampleDirtyExogenaBatch);

// Buscador complementario DIVIPOLA
const departments = ref<any[]>([]);
const municipalities = ref<any[]>([]);
const selectedDept = ref('');
const searchCity = ref('');

async function runExogenaBatchAudit() {
  loading.value = true;
  errorMsg.value = '';
  try {
    auditResult.value = await apiRequest('/api/v1/accounting/exogena/batch-audit', {
      method: 'POST',
      body: JSON.stringify({
        year: year.value,
        rawTsvText: rawBatchText.value,
      }),
    });
  } catch (e: any) {
    errorMsg.value = e.message;
  } finally {
    loading.value = false;
  }
}

function loadSampleBatch() {
  rawBatchText.value = sampleDirtyExogenaBatch;
  runExogenaBatchAudit();
}

async function copyCleanExogenaForExcel() {
  if (!auditResult.value?.auditedRows) return;
  const headers = [
    'Tipo_Doc_DIAN',
    'Numero_Identificacion_Limpio',
    'DV_Oficial_Modulo11',
    'Estado_DV',
    'Razon_Social_Tercero',
    'Cod_Dpto_DANE',
    'Cod_Mpio_DANE',
    'Municipio_Oficial',
    'Formato_Exogena',
    'Concepto_Exogena',
    'Base_Pagada_COP',
    'Retencion_Practicada_COP',
    'Retencion_Legal_DIAN_COP',
    'Faltante_Riesgo_COP',
    'Estado_Auditoria',
  ];
  const rows = auditResult.value.auditedRows.map((r: any) => [
    r.dianDocumentTypeCode,
    r.cleanIdentificationNumber,
    r.officialCalculatedDv,
    r.dvValidationStatus,
    r.thirdPartyName,
    r.daneDepartmentCode,
    r.daneMunicipalityCode,
    r.standardizedMunicipalityName,
    r.exogenaFormat,
    r.exogenaConceptCode,
    r.basePaidCOP,
    r.withholdingPracticedCOP,
    r.expectedLegalWithholdingCOP,
    r.differenceShortfallCOP,
    r.withholdingAuditStatus,
  ]);
  await copyRowsAsTsvForExcel(headers, rows);
  feedbackMsg.value = 'Matriz limpia con NITs, DV Módulo 11 y códigos DANE copiada al portapapeles lista para pegar en Excel / Prevalidador DIAN.';
}

function downloadCleanExogenaCsv() {
  if (!auditResult.value?.auditedRows) return;
  const headers = [
    'Tipo_Doc_DIAN',
    'Numero_Identificacion_Limpio',
    'DV_Oficial_Modulo11',
    'Estado_DV',
    'Razon_Social_Tercero',
    'Cod_Dpto_DANE',
    'Cod_Mpio_DANE',
    'Municipio_Oficial',
    'Formato_Exogena',
    'Concepto_Exogena',
    'Base_Pagada_COP',
    'Retencion_Practicada_COP',
    'Retencion_Legal_DIAN_COP',
    'Faltante_Riesgo_COP',
    'Estado_Auditoria',
  ];
  const rows = auditResult.value.auditedRows.map((r: any) => [
    r.dianDocumentTypeCode,
    r.cleanIdentificationNumber,
    r.officialCalculatedDv,
    r.dvValidationStatus,
    r.thirdPartyName,
    r.daneDepartmentCode,
    r.daneMunicipalityCode,
    r.standardizedMunicipalityName,
    r.exogenaFormat,
    r.exogenaConceptCode,
    r.basePaidCOP,
    r.withholdingPracticedCOP,
    r.expectedLegalWithholdingCOP,
    r.differenceShortfallCOP,
    r.withholdingAuditStatus,
  ]);
  exportRowsToCsv('exogena_1001_auditada_alcontable.csv', headers, rows);
}

async function saveExogenaWorkpaperInMariaDB() {
  if (!auditResult.value) return;
  try {
    const clientName = props.activeClient?.businessName || 'Estudio Contable';
    await apiRequest('/api/v1/accounting/portfolio/workpapers', {
      method: 'POST',
      body: JSON.stringify({
        clientId: props.activeClient?.id || null,
        workpaperType: 'EXOGENA_PREAUDITORIA',
        title: `Pre-Auditoría Exógena (${auditResult.value.totalRowsProcessed} terceros) — ${clientName}`,
        taxPeriod: `${year.value}-ANUAL`,
        executiveSummary: `Corregidos ${auditResult.value.dvErrorsCorrectedCount} NITs con DV erróneo en contabilidad. Detectadas ${auditResult.value.underWithholdingAlertsCount} alertas de sub-retención por ${formatCOP(auditResult.value.totalShortfallRiskArt370COP)}.`,
        detectedSavingsOrRiskCop: auditResult.value.totalShortfallRiskArt370COP,
        payloadJson: JSON.stringify(auditResult.value),
      }),
    });
    feedbackMsg.value = 'Pre-auditoría de Exógena y Retenciones guardada en MariaDB en el expediente del cliente.';
  } catch (e: any) {
    errorMsg.value = e.message;
  }
}

async function loadDivipolaCatalog() {
  departments.value = await apiRequest('/api/v1/geo/departments');
  await filterMunicipalities();
}

async function filterMunicipalities() {
  const params = new URLSearchParams();
  if (selectedDept.value) params.set('departmentCode', selectedDept.value);
  if (searchCity.value.trim()) params.set('q', searchCity.value.trim());
  const data = await apiRequest(`/api/v1/geo/municipalities?${params.toString()}`);
  municipalities.value = data.items || [];
}

onMounted(() => {
  runExogenaBatchAudit();
  loadDivipolaCatalog();
});
</script>

<template>
  <div class="space-y-6">
    <!-- Panel Superior: Pegado Masivo desde Excel para Exógena y Auditoría de Retenciones -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 class="text-base font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-file-shield text-cyan-400"></i>
            Pre-Auditor Masivo de Exógena DIAN (Formato 1001/1007), Limpiador de NITs/DV en Lote &amp; Validador de Retenciones (Pre-350)
          </h3>
          <p class="text-xs text-slate-400 mt-0.5">
            Pega decenas o cientos de terceros desde tu balance de prueba en Excel: limpia basura, corrige el DV Módulo 11, asigna código DANE y detecta retenciones mal practicadas.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <button
            @click="loadSampleBatch"
            class="px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-amber-300 font-semibold"
          >
            <i class="fa-solid fa-wand-magic-sparkles mr-1"></i>Cargar Lote Real con Errores
          </button>
          <button
            @click="runExogenaBatchAudit"
            class="px-4 py-2 rounded-xl bg-cyan-500 hover:bg-cyan-400 text-slate-950 font-bold"
          >
            <i class="fa-solid fa-bolt mr-1"></i>Ejecutar Auditoría en Lote
          </button>
        </div>
      </div>

      <div class="space-y-1.5 text-xs">
        <div class="text-slate-300 font-semibold">
          Formato de Pegado desde Excel (Tabulado TSV, `;` o `,`):
          <code class="text-amber-300 font-mono ml-1">
            NIT/Cédula | Razón Social | Concepto (COMPRAS / SERVICIOS / HONORARIOS / ARRENDAMIENTO_INMUEBLE / TRANSPORTE_CARGA) | ¿Declarante? (SI/NO) | Base Pagada | Retención Practicada | Ciudad
          </code>
        </div>
        <textarea
          v-model="rawBatchText"
          rows="6"
          class="w-full bg-slate-950 border border-slate-700 rounded-xl p-3 text-white font-mono text-xs leading-relaxed"
        ></textarea>
      </div>

      <div v-if="feedbackMsg" class="bg-emerald-950/40 border border-emerald-500/40 text-emerald-300 text-xs rounded-xl p-3 flex items-center justify-between">
        <span><i class="fa-solid fa-circle-check mr-1.5"></i>{{ feedbackMsg }}</span>
        <button @click="feedbackMsg = ''" class="text-slate-400 hover:text-white">✕</button>
      </div>
      <div v-if="errorMsg" class="bg-rose-950/40 border border-rose-500/40 text-rose-300 text-xs rounded-xl p-3">
        {{ errorMsg }}
      </div>
    </div>

    <!-- Resultados del Lote de Exógena y Retenciones -->
    <div v-if="auditResult" class="space-y-6">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-slate-900 border border-amber-500/40 rounded-2xl p-4">
          <div class="text-xs text-amber-300 font-semibold">NITs con DV Erróneo en Contabilidad</div>
          <div class="text-2xl font-black text-amber-400 font-mono mt-1">
            {{ auditResult.dvErrorsCorrectedCount }} / {{ auditResult.totalRowsProcessed }}
          </div>
          <div class="text-[11px] text-slate-400 mt-1">Corregidos automáticamente por Módulo 11</div>
        </div>

        <div class="bg-slate-900 border border-rose-500/40 rounded-2xl p-4">
          <div class="text-xs text-rose-300 font-semibold">Alertas por Retener de Menos (Art. 370 E.T.)</div>
          <div class="text-2xl font-black text-rose-400 font-mono mt-1">
            {{ auditResult.underWithholdingAlertsCount }} terceros
          </div>
          <div class="text-[11px] text-rose-300/80 font-mono mt-1">
            Faltante: {{ formatCOP(auditResult.totalShortfallRiskArt370COP) }}
          </div>
        </div>

        <div class="bg-slate-900 border border-slate-800 rounded-2xl p-4">
          <div class="text-xs text-slate-400">Retención Practicada vs Legal Esperada</div>
          <div class="text-lg font-black text-white font-mono mt-1">
            {{ formatCOP(auditResult.totalWithholdingPracticedCOP) }}
            <span class="text-slate-500 text-xs">vs</span>
            <span class="text-cyan-300"> {{ formatCOP(auditResult.totalExpectedLegalWithholdingCOP) }}</span>
          </div>
          <div class="text-[11px] text-slate-400 mt-1">Base auditada: {{ formatCOP(auditResult.totalBaseAuditedCOP) }}</div>
        </div>

        <div class="bg-slate-900 border border-emerald-500/40 rounded-2xl p-4">
          <div class="text-xs text-emerald-300 font-semibold">Total Renglón Formulario 350 (Art. 868)</div>
          <div class="text-2xl font-black text-emerald-400 font-mono mt-1">
            {{ formatCOP(auditResult.form350RoundedTotalWithholdingCOP) }}
          </div>
          <div class="text-[11px] text-slate-400 mt-1">Redondeado al múltiplo de $1.000 más cercano</div>
        </div>
      </div>

      <!-- Tabla de Auditoría Fila por Fila -->
      <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h4 class="text-sm font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-table-list text-cyan-400"></i>
            Matriz Depurada para Prevalidador Exógena DIAN y Conciliación de Retención en la Fuente
          </h4>

          <div class="flex flex-wrap items-center gap-2 text-xs">
            <button
              @click="copyCleanExogenaForExcel"
              class="px-3.5 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold transition"
            >
              <i class="fa-solid fa-copy mr-1"></i>Copiar Tabla Limpia para Excel / Prevalidador
            </button>
            <button
              @click="downloadCleanExogenaCsv"
              class="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-cyan-300 font-semibold transition"
            >
              <i class="fa-solid fa-file-csv mr-1"></i>Descargar CSV Exógena
            </button>
            <button
              @click="saveExogenaWorkpaperInMariaDB"
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
                <th class="py-2 px-2.5">NIT / ID Limpio + DV</th>
                <th class="py-2 px-2.5">Auditoría DV</th>
                <th class="py-2 px-2.5">Tercero &amp; Municipio DANE</th>
                <th class="py-2 px-2.5">Formato / Concepto</th>
                <th class="py-2 px-2.5 text-right">Base Pagada</th>
                <th class="py-2 px-2.5 text-right">Rete. Contable</th>
                <th class="py-2 px-2.5 text-right">Rete. Legal DIAN</th>
                <th class="py-2 px-2.5">Dictamen de Auditoría</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800/70 font-mono">
              <tr
                v-for="row in auditResult.auditedRows"
                :key="row.rowNumber"
                :class="
                  row.withholdingAuditStatus === 'RETUVO_DE_MENOS_RIESGO_DIAN'
                    ? 'bg-rose-950/20'
                    : row.dvValidationStatus === 'DV_CORREGIDO_ERROR_CONTABLE'
                      ? 'bg-amber-950/15'
                      : ''
                "
              >
                <td class="py-2 px-2.5">
                  <div class="font-bold text-white">
                    <span class="text-slate-400 text-[10px] mr-1">[{{ row.dianDocumentTypeCode }}]</span>
                    {{ row.formattedNitWithDv }}
                  </div>
                  <div class="text-[10px] text-slate-500">Orig: {{ row.rawNitInput }}</div>
                </td>
                <td class="py-2 px-2.5">
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold"
                    :class="
                      row.dvValidationStatus === 'DV_CORREGIDO_ERROR_CONTABLE'
                        ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40'
                        : row.dvValidationStatus === 'DV_CALCULADO_NUEVO'
                          ? 'bg-amber-500/20 text-amber-300'
                          : 'bg-emerald-500/15 text-emerald-300'
                    "
                  >
                    {{
                      row.dvValidationStatus === 'DV_CORREGIDO_ERROR_CONTABLE'
                        ? `⚠️ Era ${row.providedDv} → Real ${row.officialCalculatedDv}`
                        : row.dvValidationStatus === 'DV_CALCULADO_NUEVO'
                          ? `Calculado DV ${row.officialCalculatedDv}`
                          : `DV ${row.officialCalculatedDv} OK`
                    }}
                  </span>
                </td>
                <td class="py-2 px-2.5 font-sans">
                  <div class="font-bold text-white">{{ row.thirdPartyName }}</div>
                  <div class="text-[10px] text-cyan-300 font-mono">
                    DANE {{ row.daneMunicipalityCode }} (Dpto {{ row.daneDepartmentCode }}) • {{ row.standardizedMunicipalityName }}
                  </div>
                </td>
                <td class="py-2 px-2.5">
                  <span class="px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-300 font-bold text-[10px]">
                    F-{{ row.exogenaFormat }} / {{ row.exogenaConceptCode }}
                  </span>
                  <div class="text-[10px] text-slate-400 mt-0.5">{{ row.conceptCode }} ({{ row.legalRatePercent }}%)</div>
                </td>
                <td class="py-2 px-2.5 text-right text-white">{{ formatCOP(row.basePaidCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-amber-300">{{ formatCOP(row.withholdingPracticedCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-emerald-400 font-bold">{{ formatCOP(row.expectedLegalWithholdingCOP) }}</td>
                <td class="py-2 px-2.5 font-sans">
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold"
                    :class="
                      row.withholdingAuditStatus === 'RETUVO_DE_MENOS_RIESGO_DIAN'
                        ? 'bg-rose-500/20 text-rose-300 border border-rose-500/40'
                        : row.withholdingAuditStatus === 'RETUVO_DE_MAS'
                          ? 'bg-amber-500/20 text-amber-300'
                          : 'bg-emerald-500/15 text-emerald-300'
                    "
                  >
                    {{ row.withholdingAuditStatus }}
                  </span>
                  <div class="text-[10px] text-slate-400 mt-0.5">{{ row.auditObservation }}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Catálogo DANE / DIVIPOLA de Referencia Rápida -->
    <div class="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h4 class="text-sm font-bold text-white flex items-center gap-2">
            <i class="fa-solid fa-map-location-dot text-cyan-400"></i>
            Buscador de Códigos Oficiales DANE / DIVIPOLA para Medios Magnéticos e ICA
          </h4>
        </div>
        <div class="flex flex-wrap items-center gap-2 text-xs">
          <select v-model="selectedDept" @change="filterMunicipalities" class="bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-white">
            <option value="">Todos los Departamentos</option>
            <option v-for="d in departments" :key="d.departmentCode" :value="d.departmentCode">
              [{{ d.departmentCode }}] {{ d.departmentName }}
            </option>
          </select>
          <input
            v-model="searchCity"
            @input="filterMunicipalities"
            type="text"
            placeholder="Buscar municipio o código DANE..."
            class="w-64 bg-slate-950 border border-slate-700 rounded-xl px-3 py-1.5 text-white"
          />
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5 text-xs">
        <div
          v-for="m in municipalities.slice(0, 12)"
          :key="m.daneCode"
          class="bg-slate-950 border border-slate-800 rounded-xl p-3 flex items-center justify-between"
        >
          <div>
            <div class="font-bold text-white">{{ m.municipalityName }}</div>
            <div class="text-[10px] text-slate-400">{{ m.departmentName }}</div>
          </div>
          <span class="px-2 py-1 rounded bg-cyan-500/15 text-cyan-300 font-mono font-bold">
            {{ m.daneCode }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
