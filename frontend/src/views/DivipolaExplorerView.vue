<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { apiRequest } from '../services/api';

const departments = ref<any[]>([]);
const municipalities = ref<any[]>([]);
const selectedDept = ref('');
const searchQuery = ref('');

async function loadDepartments() {
  departments.value = await apiRequest('/api/v1/geo/departments');
}

async function searchMunicipalities() {
  const params = new URLSearchParams();
  if (selectedDept.value) params.set('departmentCode', selectedDept.value);
  if (searchQuery.value.trim()) params.set('q', searchQuery.value.trim());
  const res = await apiRequest(`/api/v1/geo/municipalities?${params.toString()}`);
  municipalities.value = res.items || [];
}

onMounted(async () => {
  await loadDepartments();
  await searchMunicipalities();
});
</script>

<template>
  <div class="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h3 class="text-base font-bold text-white flex items-center gap-2">
          <i class="fa-solid fa-map-location-dot text-cyan-400"></i>
          Catálogo Territorial Oficial DANE / DIVIPOLA (Departamentos y Municipios)
        </h3>
        <p class="text-xs text-slate-400">
          Códigos DANE de 2 y 5 dígitos, clasificación municipal (Ley 617) y coordenadas geográficas.
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2 text-xs">
        <select
          v-model="selectedDept"
          @change="searchMunicipalities"
          class="bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
        >
          <option value="">Todos los Departamentos</option>
          <option v-for="d in departments" :key="d.departmentCode" :value="d.departmentCode">
            [{{ d.departmentCode }}] {{ d.departmentName }}
          </option>
        </select>

        <input
          v-model="searchQuery"
          @input="searchMunicipalities"
          type="text"
          placeholder="Buscar por nombre o código DANE (ej. 68895, Medellín)..."
          class="w-72 bg-slate-950 border border-slate-700 rounded-xl px-3 py-2 text-white"
        />
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 text-xs">
      <div
        v-for="m in municipalities"
        :key="m.daneCode"
        class="bg-slate-950 border border-slate-800 rounded-xl p-3.5 flex items-start justify-between gap-2"
      >
        <div>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-300 font-mono font-bold">
              DANE {{ m.daneCode }}
            </span>
            <span
              v-if="m.isCapital"
              class="px-2 py-0.5 rounded bg-amber-500/15 text-amber-300 text-[10px] font-bold"
            >
              Capital
            </span>
          </div>
          <div class="text-sm font-bold text-white mt-1.5">{{ m.municipalityName }}</div>
          <div class="text-[11px] text-slate-400">{{ m.departmentName }} • {{ m.region }}</div>
        </div>
        <div class="text-right font-mono text-[10px] text-slate-400">
          <div class="text-cyan-300 font-bold">Cat. {{ m.category }}</div>
          <div class="mt-1">{{ m.latitude }}, {{ m.longitude }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
