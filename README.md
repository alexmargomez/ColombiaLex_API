# ⚖️ ALcontable — Suite de Herramientas Contables, Tributarias (DIAN), Laborales (CST / Ley 2101 / UGPP), Festivos (Ley Emiliani) & DANE DIVIPOLA

**ALcontable** es una plataforma abierta para la comunidad y estación de trabajo contable/tributaria construida en **Java 21 LTS + Spring Boot 3.3** bajo **DDD Táctico (*Domain-Driven Design*) y Arquitectura Hexagonal**, acompañada de un panel interactivo **SPA en Vue 3 + TypeScript + Vite** y persistencia en **MariaDB 11**.

Centraliza en una sola plataforma y API REST de alta precisión financiera (`BigDecimal` con `RoundingMode.HALF_UP`) los cálculos contables, laborales, tributarios y territoriales más utilizados por contadores públicos, estudios contables, sistemas de nómina y ERPs en Colombia.

![Java 21 LTS](https://img.shields.io/badge/Java-21_LTS_(Virtual_Threads)-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue_3-Composition_API_%2B_TS-4FC08D?logo=vuedotjs&logoColor=white)
![MariaDB 11](https://img.shields.io/badge/MariaDB-11_LTS-003545?logo=mariadb&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-DDD_%2B_Hexagonal-8A2BE2)
![Design Patterns](https://img.shields.io/badge/GoF_Patterns-Strategy_%7C_Spec_%7C_VO-F59E0B)
![JUnit 5](https://img.shields.io/badge/Tests-JUnit_5_Passing-25A162?logo=junit5&logoColor=white)
![ALpanel Ready](https://img.shields.io/badge/ALpanel-3--Port_Standard_Ready-06B6D4)

---

## 🚀 ¿Qué resuelve la Estación de Trabajo ALcontable?

1. **🗂️ Módulo 1: Cartera de Clientes Contables, Radar de Vencimientos DIAN y Expediente Digital (Persistencia en MariaDB):**
   - Gestión de empresas y personas naturales por **NIT + DV** y régimen tributario (`ORDINARIO_PJ`, `SIMPLE_RST`, `PERSONA_NATURAL`, `ESAL`).
   - Generación automática de **Calendario Tributario DIAN** según últimos dígitos del NIT y **Radar de Obligaciones** (`PRESENTADO`, `PROXIMO_A_VENCER`, `VENCIDO_CON_SANCION`) con conexión de 1 clic hacia el Liquidador Forense de Sanciones.
   - **Expediente Digital de Papeles de Trabajo (`alcontable_saved_workpapers`)** persistido en MariaDB para conservar cada corrida de nómina, pre-auditoría de exógena, planeación o liquidación de sanciones.
2. **👥 Módulo 2: Auditor Masivo de Nómina Mensual en Lote, Tope 40% UGPP (Ley 1393 Art. 30) y Comprobante Contable PUC:**
   - Procesamiento simultáneo en lote de plantillas de empleados (`Copy/Paste` desde Excel o CSV).
   - **Auditoría Preventiva UGPP (Ley 1393 de 2010 Art. 30):** Detecta automáticamente pagos no constitutivos de salario que superan el **40% del total de la remuneración** y recalcula el **IBC de Seguridad Social** sumando el exceso.
   - Liquidación automática de **PILA** (Salud, Pensión, FSP escalonado, ARL I–V, Parafiscales con **Exoneración Art. 114-1 E.T.** y Provisiones de Prestaciones Sociales).
   - **Generador de Comprobante de Contabilización PUC a 6 dígitos (`5105xx` / `2370xx` / `2380xx` / `2505xx` / `2610xx`)** cuadrado al centavo ($\sum \text{Débitos} = \sum \text{Créditos}$) listo para importar en Siigo / World Office / Alegra.
3. **🔍 Módulo 3: Pre-Auditor Masivo de Exógena DIAN (Formatos 1001 y 1007), Limpiador NIT/DV y Homologador DANE:**
   - Limpieza en lote de NITs sucios con guiones, puntos o espacios y verificación matemática contra **Módulo 11 DIAN** (detectando discrepancias entre el DV del ERP y el DV real).
   - Homologación automática de nombres de ciudades con tildes o errores de digitación contra el catálogo oficial **DANE DIVIPOLA** (código de departamento de 2 dígitos y municipio de 5 dígitos).
   - **Auditoría de Insuficiencia de Retención en la Fuente (Art. 177 y 370 E.T.):** Cruza pagos acumulados por tercero y concepto (`HONORARIOS`, `COMISIONES`, `SERVICIOS_GENERALES`, `COMPRAS_GENERALES`, `ARRENDAMIENTOS_MUEBLES`, `ARRENDAMIENTOS_INMUEBLES`), alerta bases superiores al tope en UVT sin retención practicada y proyecta los renglones del **Formulario 350** redondeados al múltiplo de mil.
4. **📊 Módulo 4: Estudio de Planeación Tributaria Ley 2277 (Art. 383/388 E.T.) y Comparador Régimen Ordinario vs SIMPLE (RST):**
   - **Depurador de Retención en la Fuente Rentas de Trabajo (Art. 383 y 388 E.T.):** Deducciones (vivienda, medicina prepagada, dependientes 10% + 72 UVT Ley 2277), renta exenta 25% limitada a **790 UVT anuales** y límite global del **40% o 1.340 UVT anuales**.
   - **Comparador Estratégico Lado a Lado:** Simula con cifras reales del cliente el impuesto neto bajo **Régimen Ordinario (35%)** frente al **Régimen Simple de Tributación — RST (Art. 908 E.T. Grupos 1 al 4)** considerando el impacto del impuesto sobre ingresos brutos, exoneración de aportes y descuentos de pensión y facturación electrónica.
5. **⚖️ Módulo 5: Liquidador Forense de Sanciones DIAN (Art. 641/642/644 + Gradualidad Art. 640) y Tasador Ley 2101:**
   - Cálculo exacto de **Sanción por Extemporaneidad (Art. 641 y 642 E.T.)** y **Sanción por Corrección (Art. 644 E.T.)** por mes o fracción de mes, o sobre ingresos brutos / patrimonio cuando no hay impuesto a cargo.
   - Aplicación automática del **Régimen de Gradualidad (Art. 640 E.T. — reducción al 50% o 75%)**, **Sanción Mínima de 10 UVT (Art. 639 E.T.)**, redondeo al múltiplo de mil (**Art. 868 E.T.**), **Intereses Moratorios (Art. 634/635 E.T.)** y borrador de casillas del **Recibo Oficial de Pago Formulario 490 (Casillas 41, 42, 43 y 48)**.
   - Incluye **Tasador de Horas Extra y Recargos con Reducción Gradual de Jornada (Ley 2101 de 2021: 240h → 235h → 230h → 220h → 210h)**.

---

## 🏛️ Arquitectura y Patrones de Diseño (GoF & DDD)

- **DDD Táctico (*Bounded Contexts* en `com.alcontable`):** `portfolio`, `payroll`, `exogena`, `taxplanning`, `sanctions` y `shared`.
- **Patrones de Diseño Aplicados:**
  - **`Value Object Pattern` (`MoneyCOP`, `NitVO`):** Inmutabilidad, validación en construcción y aritmética exacta con `BigDecimal`.
  - **`Strategy Pattern` (`Law2101WorkingHoursStrategy`, `SeveranceIndemnificationStrategy`):** Selección dinámica de reglas según vigencia de ley o modalidad contractual.
  - **`Specification Pattern` (`LaborSpecifications`):** Reglas booleanas declarativas para elegibilidad de auxilio de transporte, tramos de FSP, tope 40% UGPP y exoneración Art. 114-1 E.T.

---

## 🐳 Despliegue Rápido (Docker Compose & ALpanel)

```bash
cp .env.example .env
docker compose up -d --build
```

- **Estación de Trabajo Web (Vue 3 SPA):** `http://localhost:5176`
- **API Interna (Web & Integración Móvil — Java 21):** `http://localhost:8085/api/v1/health`
