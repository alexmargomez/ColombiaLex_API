# ⚖️ ColombiaLex API — Motor Legal, Laboral (CST / Ley 2101), Tributario (DIAN Módulo 11 / UVT), Festivos (Ley Emiliani) & DANE DIVIPOLA

**ColombiaLex API** es una plataforma abierta para la comunidad y microservicio normativo construido en **Java 21 LTS + Spring Boot 3.3** bajo **DDD Táctico (*Domain-Driven Design*) y Arquitectura Hexagonal**, acompañado de un panel interactivo **SPA en Vue 3 + TypeScript + Vite** y persistencia en **MariaDB 11**.

Centraliza en una sola API REST de alta precisión financiera (`BigDecimal` con `RoundingMode.HALF_UP`) los cálculos legales, laborales, tributarios y territoriales más utilizados por sistemas de nómina, ERPs, plataformas contables y comercios electrónicos en Colombia.

![Java 21 LTS](https://img.shields.io/badge/Java-21_LTS_(Virtual_Threads)-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3.4-6DB33F?logo=springboot&logoColor=white)
![Vue 3](https://img.shields.io/badge/Vue_3-Composition_API_%2B_TS-4FC08D?logo=vuedotjs&logoColor=white)
![MariaDB 11](https://img.shields.io/badge/MariaDB-11_LTS-003545?logo=mariadb&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-DDD_%2B_Hexagonal-8A2BE2)
![Design Patterns](https://img.shields.io/badge/GoF_Patterns-Strategy_%7C_Spec_%7C_VO-F59E0B)
![JUnit 5](https://img.shields.io/badge/Tests-JUnit_5_Passing-25A162?logo=junit5&logoColor=white)
![ALpanel Ready](https://img.shields.io/badge/ALpanel-3--Port_Standard_Ready-06B6D4)

---

## 🚀 ¿Qué resuelve ColombiaLex API?

1. **⏰ Calculadora de Horas Extra y Recargos con Reducción Gradual de Jornada (Ley 2101 de 2021 + CST Art. 168 y 179):**
   - Selecciona automáticamente el divisor horario mensual exacto según la fecha de causación:
     - Hasta 14 jul 2023: **48 h/semana** (`240 h/mes`)
     - Desde 15 jul 2023: **47 h/semana** (`235 h/mes`)
     - Desde 15 jul 2024: **46 h/semana** (`230 h/mes`)
     - Desde 15 jul 2025: **44 h/semana** (`220 h/mes`)
     - Desde 15 jul 2026: **42 h/semana** (`210 h/mes`)
   - Desglosa al centavo **HED (`+25%`)**, **HEN (`+75%`)**, **Recargo Nocturno (`+35%`)**, **Dominical/Festivo Diurno (`+75%`)**, **Dominical/Festivo Nocturno (`+110%`)**, **Extra Dominical Diurna (`+100%`)** y **Extra Dominical Nocturna (`+150%`)**.
2. **📑 Liquidador Integral de Contrato, Prestaciones Sociales, Seguridad Social e Indemnización (Art. 64 CST):**
   - **Auxilio de Transporte** automático cuando el salario devengado es $\le 2 \text{ SMLMV}$.
   - **Cesantías** ($\frac{\text{Salario Base} \times \text{Días}}{360}$), **Intereses sobre Cesantías** (`12%` anual), **Prima de Servicios** y **Vacaciones Compensadas** (calculadas sobre el salario básico sin auxilio de transporte, Art. 192 CST).
   - **Seguridad Social y Parafiscales (PILA):** Salud (`4%` / `8.5%`), Pensión (`4%` / `12%`), **Fondo de Solidaridad Pensional (`FSP` `1%` a `2%`)**, **ARL por Clase de Riesgo (`I` a `V`)**, Caja de Compensación (`4%`), ICBF (`3%`), SENA (`2%`) y verificación automática de **Exoneración de Aportes Art. 114-1 E.T. ($< 10 \text{ SMLMV}$)**.
   - **Indemnización por Despido sin Justa Causa (Art. 64 CST):** Estrategias diferenciadas para **Contrato Indefinido ($< 10$ SMLMV vs $\ge 10$ SMLMV)**, **Término Fijo** y **Obra o Labor**.
3. **🧾 Motor Tributario DIAN (NIT Módulo 11, Conversor UVT/SMLMV, Retención en la Fuente y CIIU):**
   - Cálculo y validación del **Dígito de Verificación (DV)** usando la secuencia oficial de 15 números primos de la DIAN (`[3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71]`).
   - Conversor histórico `UVT ↔ COP ↔ SMLMV` (`2020–2026`), calculadora de **Retención en la Fuente** por topes mínimos de UVT y buscador de actividades **CIIU Rev. 4 A.C.**
4. **📅 Calendario de Festivos (Ley Emiliani — Ley 51 de 1983) y Calculadora de Días Hábiles:**
   - Calcula matemáticamente el **Domingo de Pascua** (Algoritmo de Meeus/Jones/Butcher) y los **18 festivos nacionales** de cualquier año (`1984–2100`), incluyendo traslados al lunes y suma/conteo de **días hábiles** para términos legales o PQRSD.
5. **🗺️ Catálogo Geográfico Oficial DANE / DIVIPOLA:**
   - Consulta de Departamentos y Municipios de Colombia con código DANE de 2 y 5 dígitos, categoría Ley 617, subregión y coordenadas Lat/Lng.

---

## 🏛️ Arquitectura y Patrones de Diseño (GoF & DDD)

- **DDD Táctico (*Bounded Contexts*):** `labor`, `tax`, `calendar` y `geo`.
- **Patrones de Diseño Aplicados:**
  - **`Value Object Pattern` (`MoneyCOP`, `NitVO`):** Inmutabilidad, validación en construcción y aritmética exacta con `BigDecimal`.
  - **`Strategy Pattern` (`Law2101WorkingHoursStrategy`, `SeveranceIndemnificationStrategy`):** Selección dinámica de reglas según vigencia de ley o modalidad contractual.
  - **`Specification Pattern` (`LaborSpecifications`):** Reglas booleanas declarativas para elegibilidad de auxilio de transporte, tramos de FSP y exoneración Art. 114-1 E.T.

---

## 🐳 Despliegue Rápido (Docker Compose & ALpanel)

```bash
cp .env.example .env
docker compose up -d --build
```

- **Frontend SPA Interactivo:** `http://localhost:5176`
- **Backend API REST (Java 21):** `http://localhost:8085/api/v1/health`
- **Swagger UI (OpenAPI 3.1):** `http://localhost:8085/api/v1/swagger-ui.html`
