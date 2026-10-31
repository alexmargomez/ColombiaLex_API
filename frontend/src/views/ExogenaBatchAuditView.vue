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
    <!-- Papel de Trabajo PT-03A: Pegado Masivo desde Excel para Exógena y Auditoría de Retenciones -->
    <div class="bg-white border border-slate-200 border-t-4 border-t-blue-700 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
        <div>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
              PT-03A
            </span>
            <h3 class="text-base font-bold text-slate-900">
              Pre-Auditor Masivo de Exógena DIAN (Formato 1001/1007), Limpiador de NITs/DV &amp; Cruce de Retenciones (Pre-Form. 350)
            </h3>
          </div>
          <p class="text-xs text-slate-500 mt-0.5">
            Pega terceros desde el balance de prueba en Excel: limpia caracteres inválidos, corrige el DV Módulo 11, homologa código DANE y detecta sub-retenciones Art. 370 E.T.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-2 text-xs">
          <button
            @click="loadSampleBatch"
            class="px-3 py-2 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
          >
            <i class="fa-solid fa-table-cells text-blue-700 mr-1"></i>Cargar Lote de Prueba
          </button>
          <button
            @click="runExogenaBatchAudit"
            class="px-4 py-2 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold transition shadow-2xs"
          >
            <i class="fa-solid fa-magnifying-glass-chart mr-1.5"></i>Auditar Lote Exógena
          </button>
        </div>
      </div>

      <div class="space-y-1.5 text-xs">
        <div class="text-slate-700 font-semibold">
          Estructura de Columnas desde Excel (Tabulado TSV, `;` o `,`):
          <code class="text-blue-800 bg-blue-50 border border-blue-200 px-1.5 py-0.5 rounded font-mono ml-1 text-[11px]">
            NIT/Cédula | Razón Social | Concepto (COMPRAS / SERVICIOS / HONORARIOS / ARRENDAMIENTO_INMUEBLE / TRANSPORTE_CARGA) | ¿Declarante? (SI/NO) | Base Pagada | Retención Practicada | Ciudad
          </code>
        </div>
        <textarea
          v-model="rawBatchText"
          rows="6"
          class="w-full bg-slate-50 border border-slate-300 rounded-lg p-3 text-slate-900 font-mono text-xs leading-relaxed focus:bg-white focus:outline-none focus:border-blue-600"
        ></textarea>
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
    </div>

    <!-- Resultados del Lote de Exógena y Retenciones -->
    <div v-if="auditResult" class="space-y-6">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="bg-white border border-slate-200 border-l-4 border-l-amber-600 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-amber-800 uppercase tracking-wider">NITs con DV Erróneo en Libros</div>
          <div class="text-2xl font-extrabold text-amber-700 font-mono tabular-nums mt-1">
            {{ auditResult.dvErrorsCorrectedCount }} / {{ auditResult.totalRowsProcessed }}
          </div>
          <div class="text-[11px] text-slate-600 mt-1">Corregidos automáticamente por Módulo 11 DIAN</div>
        </div>

        <div class="bg-white border border-slate-200 border-l-4 border-l-red-600 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-red-700 uppercase tracking-wider">Sub-Retenciones (Art. 370 E.T.)</div>
          <div class="text-2xl font-extrabold text-red-700 font-mono tabular-nums mt-1">
            {{ auditResult.underWithholdingAlertsCount }} terceros
          </div>
          <div class="text-[11px] text-red-700 font-mono tabular-nums font-medium mt-1">
            Faltante Solidario: {{ formatCOP(auditResult.totalShortfallRiskArt370COP) }}
          </div>
        </div>

        <div class="bg-white border border-slate-200 border-l-4 border-l-blue-700 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-slate-500 uppercase tracking-wider">Rete. Contable vs Legal DIAN</div>
          <div class="text-base font-extrabold text-slate-900 font-mono tabular-nums mt-1">
            {{ formatCOP(auditResult.totalWithholdingPracticedCOP) }}
            <span class="text-slate-400 text-xs font-normal">vs</span>
            <span class="text-blue-800"> {{ formatCOP(auditResult.totalExpectedLegalWithholdingCOP) }}</span>
          </div>
          <div class="text-[11px] text-slate-600 font-mono tabular-nums mt-1">
            Base auditada: {{ formatCOP(auditResult.totalBaseAuditedCOP) }}
          </div>
        </div>

        <div class="bg-white border border-slate-200 border-l-4 border-l-emerald-600 rounded-xl p-4 shadow-xs">
          <div class="text-xs font-semibold text-emerald-800 uppercase tracking-wider">Renglón Formulario 350 (Art. 868)</div>
          <div class="text-2xl font-extrabold text-emerald-700 font-mono tabular-nums mt-1">
            {{ formatCOP(auditResult.form350RoundedTotalWithholdingCOP) }}
          </div>
          <div class="text-[11px] text-slate-600 mt-1">Redondeado al múltiplo de $1.000 más cercano</div>
        </div>
      </div>

      <!-- Tabla de Auditoría Fila por Fila -->
      <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono text-[10px] font-bold">
              PT-03B
            </span>
            <h4 class="text-sm font-bold text-slate-900">
              Matriz Depurada para Prevalidador Exógena DIAN (Formato 1001) y Conciliación de Retención en la Fuente
            </h4>
          </div>

          <div class="flex flex-wrap items-center gap-2 text-xs">
            <button
              @click="copyCleanExogenaForExcel"
              class="px-3.5 py-2 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white font-semibold transition shadow-2xs"
            >
              <i class="fa-solid fa-copy mr-1"></i>Copiar Tabla Limpia (Excel / Prevalidador)
            </button>
            <button
              @click="downloadCleanExogenaCsv"
              class="px-3.5 py-2 rounded-lg bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 font-semibold transition"
            >
              <i class="fa-solid fa-file-csv text-emerald-700 mr-1"></i>Descargar CSV Exógena
            </button>
            <button
              @click="saveExogenaWorkpaperInMariaDB"
              class="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-900 text-white font-semibold transition"
            >
              <i class="fa-solid fa-floppy-disk mr-1"></i>Guardar en Expediente (MariaDB)
            </button>
          </div>
        </div>

        <div class="overflow-x-auto border border-slate-200 rounded-lg">
          <table class="w-full text-left border-collapse text-xs">
            <thead>
              <tr class="bg-slate-100 border-b-2 border-slate-300 text-slate-700 uppercase text-[11px] font-bold tracking-wider">
                <th class="py-2.5 px-2.5">NIT / ID Limpio + DV</th>
                <th class="py-2.5 px-2.5">Validación DV</th>
                <th class="py-2.5 px-2.5">Tercero &amp; Municipio DANE</th>
                <th class="py-2.5 px-2.5">Formato / Concepto</th>
                <th class="py-2.5 px-2.5 text-right">Base Pagada</th>
                <th class="py-2.5 px-2.5 text-right">Rete. Contable</th>
                <th class="py-2.5 px-2.5 text-right">Rete. Legal DIAN</th>
                <th class="py-2.5 px-2.5">Dictamen de Auditoría</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white font-mono tabular-nums">
              <tr
                v-for="row in auditResult.auditedRows"
                :key="row.rowNumber"
                class="hover:bg-blue-50/40 transition-colors"
                :class="
                  row.withholdingAuditStatus === 'RETUVO_DE_MENOS_RIESGO_DIAN'
                    ? 'bg-red-50/40'
                    : row.dvValidationStatus === 'DV_CORREGIDO_ERROR_CONTABLE'
                      ? 'bg-amber-50/50'
                      : 'even:bg-slate-50/60'
                "
              >
                <td class="py-2 px-2.5 whitespace-nowrap">
                  <div class="font-bold text-slate-900">
                    <span class="text-slate-500 text-[10px] mr-1">[{{ row.dianDocumentTypeCode }}]</span>
                    {{ row.formattedNitWithDv }}
                  </div>
                  <div class="text-[10px] text-slate-500">Orig: {{ row.rawNitInput }}</div>
                </td>
                <td class="py-2 px-2.5 whitespace-nowrap">
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold border"
                    :class="
                      row.dvValidationStatus === 'DV_CORREGIDO_ERROR_CONTABLE'
                        ? 'bg-red-50 text-red-800 border-red-200'
                        : row.dvValidationStatus === 'DV_CALCULADO_NUEVO'
                          ? 'bg-amber-50 text-amber-800 border-amber-200'
                          : 'bg-emerald-50 text-emerald-800 border-emerald-200'
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
                  <div class="font-bold text-slate-900">{{ row.thirdPartyName }}</div>
                  <div class="text-[10px] text-blue-800 font-mono font-semibold">
                    DANE {{ row.daneMunicipalityCode }} (Dpto {{ row.daneDepartmentCode }}) • {{ row.standardizedMunicipalityName }}
                  </div>
                </td>
                <td class="py-2 px-2.5 whitespace-nowrap">
                  <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-bold text-[10px]">
                    F-{{ row.exogenaFormat }} / {{ row.exogenaConceptCode }}
                  </span>
                  <div class="text-[10px] text-slate-600 mt-0.5">{{ row.conceptCode }} ({{ row.legalRatePercent }}%)</div>
                </td>
                <td class="py-2 px-2.5 text-right text-slate-900 font-semibold whitespace-nowrap">{{ formatCOP(row.basePaidCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-slate-800 whitespace-nowrap">{{ formatCOP(row.withholdingPracticedCOP) }}</td>
                <td class="py-2 px-2.5 text-right text-blue-900 font-bold bg-blue-50/20 whitespace-nowrap">
                  {{ formatCOP(row.expectedLegalWithholdingCOP) }}
                </td>
                <td class="py-2 px-2.5 font-sans">
                  <span
                    class="px-2 py-0.5 rounded text-[10px] font-bold border"
                    :class="
                      row.withholdingAuditStatus === 'RETUVO_DE_MENOS_RIESGO_DIAN'
                        ? 'bg-red-50 text-red-800 border-red-200'
                        : row.withholdingAuditStatus === 'RETUVO_DE_MAS'
                          ? 'bg-amber-50 text-amber-800 border-amber-200'
                          : 'bg-emerald-50 text-emerald-800 border-emerald-200'
                    "
                  >
                    {{ row.withholdingAuditStatus }}
                  </span>
                  <div class="text-[10px] text-slate-600 mt-0.5">{{ row.auditObservation }}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Catálogo DANE / DIVIPOLA de Referencia Rápida -->
    <div class="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
        <div class="flex items-center gap-2">
          <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-300 font-mono text-[10px] font-bold">
            CATÁLOGO DANE
          </span>
          <h4 class="text-sm font-bold text-slate-900">
            Directorio Oficial de Códigos DANE / DIVIPOLA para Medios Magnéticos e ICA Municipal
          </h4>
        </div>
        <div class="flex flex-wrap items-center gap-2 text-xs">
          <select
            v-model="selectedDept"
            @change="filterMunicipalities"
            class="bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-slate-900 focus:outline-none focus:border-blue-600"
          >
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
            class="w-64 bg-white border border-slate-300 rounded-lg px-3 py-1.5 text-slate-900 focus:outline-none focus:border-blue-600"
          />
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5 text-xs">
        <div
          v-for="m in municipalities.slice(0, 12)"
          :key="m.daneCode"
          class="bg-slate-50 border border-slate-200 rounded-lg p-3 flex items-center justify-between"
        >
          <div>
            <div class="font-bold text-slate-900">{{ m.municipalityName }}</div>
            <div class="text-[10px] text-slate-500">{{ m.departmentName }}</div>
          </div>
          <span class="px-2 py-1 rounded bg-blue-50 text-blue-800 border border-blue-200 font-mono font-bold">
            {{ m.daneCode }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
