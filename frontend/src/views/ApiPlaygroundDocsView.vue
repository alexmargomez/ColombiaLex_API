<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  apiRequest,
  formatCOP,
  setStoredAccountantSession,
  type AccountantUserSession,
} from '../services/api';

const props = defineProps<{
  currentUser?: AccountantUserSession | null;
  taxParams?: any;
}>();

const emit = defineEmits<{
  (e: 'authenticated', session: AccountantUserSession): void;
  (e: 'enterWorkstation', tabId?: string): void;
}>();

// --- Auth Form State ---
const authMode = ref<'login' | 'register'>('login');
const authLoading = ref(false);
const authError = ref<string | null>(null);

const loginEmail = ref('contador@alcontable.co');
const loginPassword = ref('Demo2026*');

const regFullName = ref('');
const regCardNumber = ref('');
const regFirmName = ref('');
const regEmail = ref('');
const regPassword = ref('');

async function handleLogin() {
  authLoading.value = true;
  authError.value = null;
  try {
    const session = await apiRequest<AccountantUserSession>('/api/v1/accounting/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email: loginEmail.value,
        password: loginPassword.value,
      }),
    });
    setStoredAccountantSession(session);
    emit('authenticated', session);
  } catch (e: any) {
    authError.value = e.message || 'Error al iniciar sesión.';
  } finally {
    authLoading.value = false;
  }
}

async function handleRegister() {
  authLoading.value = true;
  authError.value = null;
  try {
    const session = await apiRequest<AccountantUserSession>('/api/v1/accounting/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        email: regEmail.value,
        fullName: regFullName.value,
        professionalCardNumber: regCardNumber.value,
        firmName: regFirmName.value,
        password: regPassword.value,
      }),
    });
    setStoredAccountantSession(session);
    emit('authenticated', session);
  } catch (e: any) {
    authError.value = e.message || 'Error al registrar la cuenta de contador.';
  } finally {
    authLoading.value = false;
  }
}

async function handleDemoLogin() {
  authLoading.value = true;
  authError.value = null;
  try {
    const session = await apiRequest<AccountantUserSession>('/api/v1/accounting/auth/demo', {
      method: 'POST',
    });
    setStoredAccountantSession(session);
    emit('authenticated', session);
  } catch (e: any) {
    authError.value = e.message || 'Error al iniciar la sesión demostrativa.';
  } finally {
    authLoading.value = false;
  }
}

// --- Free Public Tool 1: Verificador NIT Módulo 11 + DANE DIVIPOLA ---
const quickNitInput = ref('800.197.268-9');
const quickCityInput = ref('Medellin');
const quickNitLoading = ref(false);
const quickNitResult = ref<any>(null);

async function runQuickNitCheck() {
  quickNitLoading.value = true;
  try {
    const tsvLine = `${quickNitInput.value}\tTERCERO CONSULTA PUBLICA\tSERVICIOS\tSI\t1000000\t40000\t${quickCityInput.value}`;
    const res = await apiRequest('/api/v1/accounting/exogena/batch-audit', {
      method: 'POST',
      body: JSON.stringify({
        year: 2026,
        rawTsvText: tsvLine,
      }),
    });
    quickNitResult.value = res.auditedRows?.[0] || null;
  } catch {
    quickNitResult.value = null;
  } finally {
    quickNitLoading.value = false;
  }
}

// --- Free Public Tool 2: Calculadora de Plazos DIAN/UGPP en Días Hábiles (Ley Emiliani) ---
const quickStartDate = ref('2026-10-15');
const quickBusinessDays = ref(15);
const quickDeadlineLoading = ref(false);
const quickDeadlineResult = ref<any>(null);

async function runQuickDeadlineCheck() {
  quickDeadlineLoading.value = true;
  try {
    quickDeadlineResult.value = await apiRequest('/api/v1/tax-procedure/business-days-deadline', {
      method: 'POST',
      body: JSON.stringify({
        startDate: quickStartDate.value,
        businessDays: Number(quickBusinessDays.value),
      }),
    });
  } catch {
    quickDeadlineResult.value = null;
  } finally {
    quickDeadlineLoading.value = false;
  }
}

// --- Free Public Tool 3: Liquidador Rápido de Sanción DIAN Art. 641/640 ---
const quickTaxAmount = ref(14850000);
const quickDueDate = ref('2026-09-15');
const quickPaymentDate = ref('2026-10-29');
const quickApply640 = ref(true);
const quickSanctionLoading = ref(false);
const quickSanctionResult = ref<any>(null);

async function runQuickSanctionCheck() {
  quickSanctionLoading.value = true;
  try {
    quickSanctionResult.value = await apiRequest('/api/v1/accounting/sanctions/liquidate', {
      method: 'POST',
      body: JSON.stringify({
        year: 2026,
        declarationType: 'RETEFUENTE_350',
        sanctionConduct: 'EXTEMPORANEIDAD_641',
        dueDate: quickDueDate.value,
        filingOrPaymentDate: quickPaymentDate.value,
        taxPayableOrDeltaCOP: Number(quickTaxAmount.value),
        grossIncomePeriodCOP: 0,
        netWorthPreviousYearCOP: 0,
        voluntaryLiquidationByTaxpayer: true,
        yearsWithoutSameInfraction: quickApply640.value ? 2 : 0,
        annualUsuryRatePercent: 28.5,
      }),
    });
  } catch {
    quickSanctionResult.value = null;
  } finally {
    quickSanctionLoading.value = false;
  }
}

const workstationModules = [
  {
    code: 'PT-01',
    title: 'Cartera Privada de Clientes & Radar Tributario DIAN',
    badge: 'PRIVADO POR CONTADOR',
    desc: 'Cada contador gestiona su propia cartera confidencial de empresas y personas naturales, con cálculo automático de vencimientos en días hábiles según último dígito del NIT y expediente digital en MariaDB.',
    icon: 'fa-solid fa-briefcase',
  },
  {
    code: 'PT-02',
    title: 'Auditoría de Planilla de Nómina, Tope 40% UGPP & Asiento PUC',
    badge: 'LOTE MULTI-EMPLEADO',
    desc: 'Audita en lote los pagos no salariales (Art. 30 Ley 1393), valida la exoneración Art. 114-1 E.T. y genera el comprobante contable PUC de 6 dígitos cuadrado al peso listo para Siigo, WorldOffice o Alegra.',
    icon: 'fa-solid fa-file-invoice-dollar',
  },
  {
    code: 'PT-03',
    title: 'Pre-Auditoría Masiva de Información Exógena (Formato 1001)',
    badge: 'CRUCE ART. 177 Y 370 E.T.',
    desc: 'Limpia terceros desde Excel, recalcula dígitos de verificación Módulo 11, homologa códigos DANE DIVIPOLA y detecta costos en riesgo de rechazo por retenciones no practicadas.',
    icon: 'fa-solid fa-table-check',
  },
  {
    code: 'PT-04',
    title: 'Planeación Tributaria Ley 2277 (Art. 383/388) vs Régimen SIMPLE',
    badge: 'ESTUDIO COMPARATIVO',
    desc: 'Depura la retención laboral con los límites de 790 UVT y 1.340 UVT de la Ley 2277, y modela lado a lado el flujo de caja neto entre Régimen Ordinario (35%) y Régimen Simple (RST Art. 908).',
    icon: 'fa-solid fa-scale-balanced',
  },
  {
    code: 'PT-05',
    title: 'Liquidador Forense de Sanciones DIAN & Recibo Oficial 490',
    badge: 'ART. 641, 642, 644 Y 640',
    desc: 'Calcula extemporaneidad, corrección e intereses moratorios, aplica gradualidad del 50%/75% (Art. 640), respeta el piso mínimo de 10 UVT (Art. 639) y redondea al múltiplo de $1.000 (Art. 868).',
    icon: 'fa-solid fa-gavel',
  },
];

onMounted(() => {
  runQuickNitCheck();
  runQuickDeadlineCheck();
  runQuickSanctionCheck();
});
</script>

<template>
  <div class="space-y-6">
    <!-- SECCIÓN 1: HERO INSTITUCIONAL + TARJETA DE ACCESO / REGISTRO DE CONTADOR -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
      <!-- Presentación del Sistema y Separación Público vs Privado -->
      <div class="lg:col-span-7 bg-[#0F172A] text-white rounded-xl p-6 lg:p-7 border border-slate-800 shadow-xs flex flex-col justify-between">
        <div>
          <div class="flex flex-wrap items-center gap-2 mb-4">
            <span class="px-2.5 py-1 rounded bg-blue-600/20 border border-blue-500/40 text-blue-300 font-mono text-[11px] font-bold">
              ESTACIÓN CONTABLE &amp; TRIBUTARIA COLOMBIA 2026
            </span>
            <span class="px-2.5 py-1 rounded bg-slate-800 border border-slate-700 text-slate-300 font-mono text-[11px]">
              UVT 2026: {{ formatCOP(props.taxParams?.uvtCOP || 52294) }} · Sanción Mín. 10 UVT: $523.000
            </span>
          </div>

          <h2 class="text-2xl lg:text-3xl font-bold tracking-tight text-white leading-tight">
            Papeles de Trabajo Auditables, Pre-Auditoría UGPP/Exógena y Cartera Privada por Contador
          </h2>
          <p class="text-sm text-slate-300 mt-3 leading-relaxed">
            <strong>ALcontable</strong> combina herramientas de consulta tributaria rápida en línea con un
            <strong>espacio de trabajo privado y aislado por Contador Público</strong> para almacenar la información confidencial
            de sus clientes, su calendario de obligaciones DIAN y sus papeles de trabajo en MariaDB.
          </p>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3.5 mt-5">
            <div class="bg-slate-900/90 border border-slate-800 rounded-lg p-4">
              <div class="flex items-center gap-2 text-xs font-bold text-blue-400 uppercase tracking-wider">
                <i class="fa-solid fa-bolt"></i>
                Herramientas Libres (En esta Portada)
              </div>
              <ul class="mt-2.5 space-y-1.5 text-xs text-slate-300">
                <li class="flex items-start gap-2">
                  <i class="fa-solid fa-check text-emerald-400 mt-0.5"></i>
                  <span>Verificador de NIT + DV Módulo 11 DIAN y código DANE DIVIPOLA.</span>
                </li>
                <li class="flex items-start gap-2">
                  <i class="fa-solid fa-check text-emerald-400 mt-0.5"></i>
                  <span>Calculadora de términos DIAN/UGPP en días hábiles (Ley Emiliani).</span>
                </li>
                <li class="flex items-start gap-2">
                  <i class="fa-solid fa-check text-emerald-400 mt-0.5"></i>
                  <span>Simulador rápido de Sanción Art. 641/640 y Recibo 490 en memoria.</span>
                </li>
              </ul>
            </div>

            <div class="bg-slate-900/90 border border-blue-900/60 rounded-lg p-4">
              <div class="flex items-center gap-2 text-xs font-bold text-emerald-400 uppercase tracking-wider">
                <i class="fa-solid fa-user-shield"></i>
                Cuenta Privada de cada Contador
              </div>
              <ul class="mt-2.5 space-y-1.5 text-xs text-slate-300">
                <li class="flex items-start gap-2">
                  <i class="fa-solid fa-lock text-blue-400 mt-0.5"></i>
                  <span>Cartera privada de clientes (NITs, regímenes y obligaciones aisladas por usuario).</span>
                </li>
                <li class="flex items-start gap-2">
                  <i class="fa-solid fa-lock text-blue-400 mt-0.5"></i>
                  <span>Auditoría en lote de Nómina UGPP + Comprobante PUC y Pre-Auditoría Exógena 1001.</span>
                </li>
                <li class="flex items-start gap-2">
                  <i class="fa-solid fa-lock text-blue-400 mt-0.5"></i>
                  <span>Archivo histórico de Papeles de Trabajo vinculados al expediente de cada cliente.</span>
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div class="mt-6 pt-4 border-t border-slate-800 flex flex-wrap items-center justify-between gap-3 text-xs text-slate-400">
          <span>
            <i class="fa-solid fa-shield-halved text-blue-400 mr-1.5"></i>
            Aislamiento Multi-Contador (`accountant_user_id`) · Cifrado de credenciales y redondeo legal Art. 868 E.T.
          </span>
          <span class="font-mono text-[11px] text-slate-300">Normativa DIAN / UGPP / CTCP</span>
        </div>
      </div>

      <!-- Tarjeta de Inicio de Sesión / Registro / Sesión Activa -->
      <div class="lg:col-span-5 bg-white border border-slate-200 border-t-4 border-t-blue-700 rounded-xl p-6 shadow-xs flex flex-col justify-between">
        <!-- Si ya hay un contador con sesión activa -->
        <div v-if="props.currentUser" class="space-y-4">
          <div class="flex items-center justify-between">
            <span class="px-2.5 py-1 rounded bg-emerald-50 text-emerald-800 border border-emerald-200 text-[11px] font-bold">
              <i class="fa-solid fa-circle-check mr-1"></i>SESIÓN DE CONTADOR ACTIVA
            </span>
            <span class="font-mono text-xs text-slate-500">{{ props.currentUser.professionalCardNumber }}</span>
          </div>

          <div class="p-4 rounded-lg bg-slate-50 border border-slate-200">
            <div class="text-base font-bold text-slate-900">{{ props.currentUser.fullName }}</div>
            <div class="text-xs font-semibold text-blue-700 mt-0.5">{{ props.currentUser.firmName }}</div>
            <div class="text-xs text-slate-500 font-mono mt-1">{{ props.currentUser.email }}</div>
          </div>

          <p class="text-xs text-slate-600 leading-relaxed">
            Su espacio de trabajo contable está activo. Todos los clientes, obligaciones del calendario tributario y papeles de trabajo que guarde están asociados exclusivamente a su cuenta profesional.
          </p>

          <div class="grid grid-cols-1 gap-2.5 pt-2">
            <button
              @click="emit('enterWorkstation', 'portfolio')"
              class="w-full py-2.5 px-4 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs transition shadow-2xs cursor-pointer"
            >
              <i class="fa-solid fa-briefcase mr-1.5"></i>Abrir Mi Cartera de Clientes &amp; Papeles de Trabajo
            </button>
            <div class="grid grid-cols-2 gap-2">
              <button
                @click="emit('enterWorkstation', 'payroll-ugpp')"
                class="py-2 px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 font-semibold text-xs transition cursor-pointer"
              >
                PT-02 Nómina &amp; PUC
              </button>
              <button
                @click="emit('enterWorkstation', 'exogena-batch')"
                class="py-2 px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 font-semibold text-xs transition cursor-pointer"
              >
                PT-03 Exógena 1001
              </button>
            </div>
          </div>
        </div>

        <!-- Formulario de Login / Registro para Contadores -->
        <div v-else class="space-y-4">
          <div class="flex items-center justify-between border-b border-slate-200 pb-3">
            <div>
              <h3 class="text-base font-bold text-slate-900">Portal Privado del Contador</h3>
              <p class="text-xs text-slate-500">Acceda a su cartera de clientes o cree su cuenta profesional</p>
            </div>
            <div class="flex rounded-lg bg-slate-100 p-0.5 border border-slate-200">
              <button
                type="button"
                @click="authMode = 'login'; authError = null"
                class="px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer"
                :class="authMode === 'login' ? 'bg-white text-blue-700 shadow-2xs' : 'text-slate-600 hover:text-slate-900'"
              >
                Ingresar
              </button>
              <button
                type="button"
                @click="authMode = 'register'; authError = null"
                class="px-3 py-1 rounded-md text-xs font-semibold transition cursor-pointer"
                :class="authMode === 'register' ? 'bg-white text-blue-700 shadow-2xs' : 'text-slate-600 hover:text-slate-900'"
              >
                Registrarse
              </button>
            </div>
          </div>

          <div
            v-if="authError"
            class="p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-800 font-medium"
          >
            <i class="fa-solid fa-triangle-exclamation mr-1.5"></i>{{ authError }}
          </div>

          <!-- MODO LOGIN -->
          <form v-if="authMode === 'login'" @submit.prevent="handleLogin" class="space-y-3">
            <div>
              <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                Correo Electrónico Profesional
              </label>
              <input
                v-model="loginEmail"
                type="email"
                required
                placeholder="contador@sufirma.co"
                class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-blue-700 focus:outline-none"
              />
            </div>
            <div>
              <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                Contraseña
              </label>
              <input
                v-model="loginPassword"
                type="password"
                required
                placeholder="••••••••"
                class="w-full rounded-lg border border-slate-300 px-3 py-2 text-xs text-slate-900 focus:border-blue-700 focus:outline-none"
              />
            </div>
            <button
              type="submit"
              :disabled="authLoading"
              class="w-full py-2.5 px-4 rounded-lg bg-blue-700 hover:bg-blue-800 text-white font-semibold text-xs transition shadow-2xs cursor-pointer disabled:opacity-50"
            >
              <i class="fa-solid fa-right-to-bracket mr-1.5"></i>
              {{ authLoading ? 'Verificando credenciales...' : 'Ingresar a mi Estación Contable' }}
            </button>
          </form>

          <!-- MODO REGISTRO -->
          <form v-else @submit.prevent="handleRegister" class="space-y-2.5">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div>
                <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                  Nombre del Contador / CPA
                </label>
                <input
                  v-model="regFullName"
                  type="text"
                  required
                  placeholder="Ej. Dr. Juan Camilo Restrepo"
                  class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 focus:border-blue-700 focus:outline-none"
                />
              </div>
              <div>
                <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                  Tarjeta Profesional JCC
                </label>
                <input
                  v-model="regCardNumber"
                  type="text"
                  placeholder="Ej. TP-204918-T"
                  class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 font-mono focus:border-blue-700 focus:outline-none"
                />
              </div>
            </div>
            <div>
              <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                Firma Contable o Consultorio Tributario
              </label>
              <input
                v-model="regFirmName"
                type="text"
                placeholder="Ej. Restrepo Auditores & Consultores S.A.S."
                class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 focus:border-blue-700 focus:outline-none"
              />
            </div>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <div>
                <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                  Correo Profesional
                </label>
                <input
                  v-model="regEmail"
                  type="email"
                  required
                  placeholder="socio@firma.co"
                  class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 focus:border-blue-700 focus:outline-none"
                />
              </div>
              <div>
                <label class="block text-[11px] font-bold text-slate-700 uppercase mb-1">
                  Contraseña (mín. 6 caracteres)
                </label>
                <input
                  v-model="regPassword"
                  type="password"
                  required
                  minlength="6"
                  placeholder="••••••••"
                  class="w-full rounded-lg border border-slate-300 px-3 py-1.5 text-xs text-slate-900 focus:border-blue-700 focus:outline-none"
                />
              </div>
            </div>
            <button
              type="submit"
              :disabled="authLoading"
              class="w-full py-2.5 px-4 rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white font-semibold text-xs transition shadow-2xs cursor-pointer disabled:opacity-50"
            >
              <i class="fa-solid fa-user-plus mr-1.5"></i>
              {{ authLoading ? 'Creando cuenta aislada...' : 'Crear Cuenta Privada de Contador (Cartera Limpia)' }}
            </button>
          </form>

          <!-- Botón de Acceso Rápido a la Firma Demo -->
          <div class="pt-3 border-t border-slate-200">
            <button
              type="button"
              @click="handleDemoLogin"
              :disabled="authLoading"
              class="w-full py-2 px-3 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 border border-slate-300 font-semibold text-xs transition flex items-center justify-center gap-2 cursor-pointer"
            >
              <i class="fa-solid fa-flask text-blue-700"></i>
              <span>Explorar Firma Contable Demo (5 Clientes y Papeles de Trabajo Pre-Cargados)</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- SECCIÓN 2: 3 HERRAMIENTAS CONTABLES ABIERTAS (SIN REGISTRO, EN MEMORIA) -->
    <div class="bg-white border border-slate-200 rounded-xl p-6 shadow-xs space-y-5">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 pb-4">
        <div>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-emerald-50 text-emerald-800 border border-emerald-200 font-mono text-[10px] font-bold">
              ACCESO LIBRE SIN REGISTRO
            </span>
            <h3 class="text-base font-bold text-slate-900">
              Herramientas Contables y Tributarias Rápidas (Consultas Instantáneas en Memoria)
            </h3>
          </div>
          <p class="text-xs text-slate-500 mt-1">
            Utilice estas 3 utilidades de verificación tributaria sin necesidad de iniciar sesión. Para guardar expedientes por cliente o auditar lotes completos, ingrese a su cuenta de contador.
          </p>
        </div>
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-5">
        <!-- Herramienta Pública 1: Verificador NIT Módulo 11 + DANE DIVIPOLA -->
        <div class="border border-slate-200 rounded-xl p-4 bg-slate-50/60 flex flex-col justify-between space-y-3">
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold text-slate-900">
                1. Verificador NIT Módulo 11 &amp; DANE
              </span>
              <span class="font-mono text-[10px] px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-semibold">
                RUT / EXÓGENA
              </span>
            </div>
            <p class="text-[11px] text-slate-600">
              Valida si el Dígito de Verificación (DV) de un tercero es correcto según el algoritmo Módulo 11 de la DIAN y homologa su código de municipio DANE.
            </p>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">NIT con o sin DV</label>
                <input
                  v-model="quickNitInput"
                  type="text"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-mono text-slate-900"
                />
              </div>
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">Ciudad / Municipio</label>
                <input
                  v-model="quickCityInput"
                  type="text"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs text-slate-900"
                />
              </div>
            </div>
            <button
              type="button"
              @click="runQuickNitCheck"
              :disabled="quickNitLoading"
              class="w-full py-1.5 px-3 rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold transition cursor-pointer"
            >
              Calcular DV Módulo 11 y Código DANE
            </button>
          </div>

          <div v-if="quickNitResult" class="p-3 rounded-lg bg-white border border-slate-200 space-y-1.5 text-xs">
            <div class="flex items-center justify-between">
              <span class="text-slate-500">NIT Limpio + DV Oficial:</span>
              <span class="font-mono font-bold text-slate-900">
                {{ quickNitResult.formattedNitWithDv }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-500">Estado DV ingresado:</span>
              <span
                class="px-2 py-0.5 rounded font-mono text-[10px] font-bold"
                :class="
                  quickNitResult.dvValidationStatus === 'DV_OK'
                    ? 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                    : 'bg-amber-50 text-amber-800 border border-amber-200'
                "
              >
                {{
                  quickNitResult.dvValidationStatus === 'DV_OK'
                    ? 'DV CORRECTO'
                    : `DV OFICIAL: ${quickNitResult.officialCalculatedDv}`
                }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-500">Código DANE DIVIPOLA:</span>
              <span class="font-mono font-semibold text-blue-700">
                {{ quickNitResult.daneMunicipalityCode }} · {{ quickNitResult.standardizedMunicipalityName }}
              </span>
            </div>
          </div>
        </div>

        <!-- Herramienta Pública 2: Calculadora de Términos en Días Hábiles (Ley Emiliani) -->
        <div class="border border-slate-200 rounded-xl p-4 bg-slate-50/60 flex flex-col justify-between space-y-3">
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold text-slate-900">
                2. Cómputo de Plazos en Días Hábiles
              </span>
              <span class="font-mono text-[10px] px-2 py-0.5 rounded bg-blue-50 text-blue-800 border border-blue-200 font-semibold">
                LEY EMILIANI
              </span>
            </div>
            <p class="text-[11px] text-slate-600">
              Calcula la fecha exacta de vencimiento de requerimientos DIAN o UGPP excluyendo sábados, domingos, Semana Santa y festivos móviles de Colombia.
            </p>
            <div class="grid grid-cols-2 gap-2">
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">Fecha Notificación</label>
                <input
                  v-model="quickStartDate"
                  type="date"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-mono text-slate-900"
                />
              </div>
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">Días Hábiles Plazo</label>
                <input
                  v-model.number="quickBusinessDays"
                  type="number"
                  min="1"
                  max="120"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-xs font-mono text-slate-900"
                />
              </div>
            </div>
            <button
              type="button"
              @click="runQuickDeadlineCheck"
              :disabled="quickDeadlineLoading"
              class="w-full py-1.5 px-3 rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold transition cursor-pointer"
            >
              Calcular Fecha Exacta de Vencimiento
            </button>
          </div>

          <div v-if="quickDeadlineResult" class="p-3 rounded-lg bg-white border border-slate-200 space-y-1.5 text-xs">
            <div class="flex items-center justify-between">
              <span class="text-slate-500">Notificación Inicial:</span>
              <span class="font-mono text-slate-800">{{ quickDeadlineResult.startDate }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-500">Término Legal:</span>
              <span class="font-mono text-slate-800">{{ quickDeadlineResult.businessDaysAdded }} días hábiles</span>
            </div>
            <div class="flex items-center justify-between pt-1 border-t border-slate-100">
              <span class="font-semibold text-slate-700">Vencimiento Hábil:</span>
              <span class="font-mono font-bold text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                {{ quickDeadlineResult.expirationDate }}
              </span>
            </div>
          </div>
        </div>

        <!-- Herramienta Pública 3: Liquidador Rápido de Sanción Art. 641 / Art. 640 -->
        <div class="border border-slate-200 rounded-xl p-4 bg-slate-50/60 flex flex-col justify-between space-y-3">
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <span class="text-xs font-bold text-slate-900">
                3. Simulador Rápido Sanción Art. 641
              </span>
              <span class="font-mono text-[10px] px-2 py-0.5 rounded bg-emerald-50 text-emerald-800 border border-emerald-200 font-semibold">
                FORMULARIO 490
              </span>
            </div>
            <p class="text-[11px] text-slate-600">
              Pre-liquida extemporaneidad antes de emplazamiento (5% por mes o fracción), gradualidad Art. 640 y piso de Sanción Mínima 10 UVT ($523.000).
            </p>
            <div class="grid grid-cols-3 gap-2">
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">Impuesto ($)</label>
                <input
                  v-model.number="quickTaxAmount"
                  type="number"
                  step="100000"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-xs font-mono text-slate-900"
                />
              </div>
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">Vencimiento</label>
                <input
                  v-model="quickDueDate"
                  type="date"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-[11px] font-mono text-slate-900"
                />
              </div>
              <div>
                <label class="block text-[10px] font-bold text-slate-600 uppercase mb-1">Fecha Pago</label>
                <input
                  v-model="quickPaymentDate"
                  type="date"
                  class="w-full rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-[11px] font-mono text-slate-900"
                />
              </div>
            </div>
            <button
              type="button"
              @click="runQuickSanctionCheck"
              :disabled="quickSanctionLoading"
              class="w-full py-1.5 px-3 rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold transition cursor-pointer"
            >
              Pre-Liquidar Sanción + Mora (Art. 641/640)
            </button>
          </div>

          <div v-if="quickSanctionResult" class="p-3 rounded-lg bg-white border border-slate-200 space-y-1.5 text-xs">
            <div class="flex items-center justify-between">
              <span class="text-slate-500">Sanción Final (Renglón 981):</span>
              <span class="font-mono font-bold text-amber-800 tabular-nums">
                {{ formatCOP(quickSanctionResult.form490SanctionRowCOP) }}
              </span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-slate-500">Interés Mora (Renglón 982):</span>
              <span class="font-mono font-semibold text-slate-800 tabular-nums">
                {{ formatCOP(quickSanctionResult.form490InterestRowCOP) }}
              </span>
            </div>
            <div class="flex items-center justify-between pt-1 border-t border-slate-100">
              <span class="font-semibold text-slate-700">Total Recibo 490 (Renglón 990):</span>
              <span class="font-mono font-bold text-slate-900 tabular-nums">
                {{ formatCOP(quickSanctionResult.form490TotalPaymentCOP) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- SECCIÓN 3: ARQUITECTURA DE LOS 5 PAPELES DE TRABAJO DEL SISTEMA -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <div>
          <h3 class="text-base font-bold text-slate-900">
            ¿Cómo funciona la Estación Privada de Papeles de Trabajo de ALcontable?
          </h3>
          <p class="text-xs text-slate-500">
            Al registrarse e iniciar sesión, cada Contador Público accede a los 5 motores especializados conectados a su propia cartera privada de clientes:
          </p>
        </div>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        <div
          v-for="mod in workstationModules"
          :key="mod.code"
          class="bg-white border border-slate-200 rounded-xl p-4 shadow-xs flex flex-col justify-between space-y-3"
        >
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="font-mono text-xs font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded border border-blue-200">
                {{ mod.code }}
              </span>
              <i :class="[mod.icon, 'text-slate-400 text-sm']"></i>
            </div>
            <h4 class="text-xs font-bold text-slate-900 leading-snug">{{ mod.title }}</h4>
            <p class="text-[11px] text-slate-600 leading-relaxed">{{ mod.desc }}</p>
          </div>
          <div class="pt-2 border-t border-slate-100">
            <span class="text-[10px] font-mono font-bold text-slate-500 uppercase">{{ mod.badge }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

