# Kora Design Language — "Hoja del día"

> Kora: claridad para cuidar lo importante.

Este documento es la referencia de diseño de Kora. Los valores viven en código
(`app/src/main/java/com/emmanuelquintana/kora/core/designsystem/`) y este archivo
explica **por qué** son así. Si cambias un token, cambia ambos.

---

## 1. Dirección

**Mundo:** el calendario de taco mexicano: un bloque de hojas, una por día, con
el día de la semana arriba, un numeral enorme y el mes debajo. Se arranca una
hoja cada mañana.

**Tesis:** el Inicio de Kora **es la hoja de hoy**. Cada toma de medicamento y
cada lectura se archiva en esa hoja. Kora rechaza el patrón por defecto de la
categoría, que es un tablero de tarjetas métricas idénticas con íconos en
círculos de color y anillos de progreso con degradado.

**Por qué este mundo:**

- Es un objeto cotidiano en México, ligado a la rutina diaria, y esa misma
  rutina es la del tratamiento (tomas por horario, lecturas en ayunas).
- Pide números grandes, condensados y legibles, que es justo lo que necesita
  un valor clínico.
- Es papel y tinta, así que transmite calma y precisión sin la frialdad de un
  hospital.
- Tiene un gesto propio que ningún competidor comparte: la hoja del día y el
  grosor del taco como inventario restante.

**Cómo entra el mundo en una app Material 3.** Solo aporta tipografía, paleta,
densidad y un gesto distintivo. Navegación, controles y patrones de
interacción son Material 3 estándar (NavigationBar, NavigationRail,
ModalBottomSheet, Snackbar). Una persona que conoce Android sabe dónde está
todo antes de notar el estilo.

### Refuerzos tomados de las direcciones descartadas

| De | Disciplina adoptada |
|---|---|
| Horario de aerolínea (timetable) | El estado de una toma es una **marca en una celda fija**: llena = tomada, hueca = pendiente, tachada = omitida, con reloj = pospuesta. No se comunica solo con color. |
| Espectrograma (líneas de emisión) | Un dato faltante es una **línea discontinua** o un hueco, nunca una interpolación. |
| Web japonesa de alta densidad | La pantalla expandida (escritorio de análisis) puede tener **densidad real**: más filas y más valores por pantalla. |
| Feed vertical | En el registro rápido, **un valor ocupa toda la hoja** y el siguiente campo está a un paso. |

---

## 2. Principios de las skills y dónde se aplican

### Impeccable (dirección, jerarquía y auditoría)

| Principio | Dónde |
|---|---|
| Modo *Operate*: escaneable, convenciones nativas por encima de la expresión | Toda la app. M3 gobierna estructura y controles. |
| Navegación según el tamaño: barra inferior en compacto, rail en expandido | `ui/navigation` (Parte 3) |
| Color y profundidad por roles tonales, sin sombras arbitrarias | `KoraColor.kt`: `surfaceTint = paper`, pasos `surfaceContainer*` |
| Tema oscuro diseñado, no invertido | `KoraPalette.Dark` con su propia verificación de contraste |
| Prohibido: eyebrows, cards idénticas como estructura, borde lateral de color > 1dp, anillos y sparklines como relleno | Componentes de la Parte 3 |
| Verificación sobre el render: emulador/dispositivo, claro/oscuro, fuente 1.3× | Checklist de la Parte 3 (`adb shell settings put system font_scale 1.3`) |

### Taste Skill (dirección de arte anti-"AI slop", reglas de pantallas de app)

| Principio | Dónde |
|---|---|
| El color va en los datos, no en el chrome | Chrome en papel y tinta; el petróleo solo en acciones y la serie principal |
| Los KPI son tipografía, no mosaicos con íconos | `KoraNumerals.valueLarge`: valor + unidad + palabra de estado |
| Superficies planas: un paso de tono, sin sombras en reposo | `KoraShapes` + tokens `paper*`; la elevación solo en lo que flota (hojas, menús) |
| Gráficas sin chrome: pocas líneas guía, sin leyenda si hay una sola serie | `ui/charts` (Parte 3) |
| Estados honestos: cero es cero, faltante es "—" | `KoraHealthStatus`, gráficas |
| Palabras del oficio, no sustantivos de plantilla | Copy: "Ayunas", "Pendiente 8:00", "Quedan 6 tabletas" |
| Una cara del producto: marca, tipografía cargada de verdad y un elemento único | Ícono de hoja + Barlow empaquetada + hoja del día |

### Emil Kowalski (pulido, componentes y movimiento)

| Principio | Dónde |
|---|---|
| ¿Debe animarse? Lo que se repite decenas de veces al día no lleva coreografía | Marcar toma, navegar: solo retroalimentación de presión |
| Ease-out fuerte para entradas; nunca ease-in en UI | `KoraEasing.Out` = `cubic-bezier(0.23, 1, 0.32, 1)` |
| Duraciones < 300ms en UI; salidas más rápidas que entradas | `KoraDurations`: 120 / 180 / 240 / 320 (hoja) / 160 (salida) |
| Presión a escala 0.97; nada aparece desde escala 0 | `KoraMotionScheme.pressedScale`; entradas desde 0.95 + opacidad |
| Resortes para lo interrumpible | `KoraMotionScheme.responsive()` (sin rebote) |
| Movimiento reducido = menos movimiento, no cero comprensión | Con "Quitar animaciones" se mantiene el fundido de color/texto y se elimina el desplazamiento |

Adaptación a Compose: las curvas CSS se traducen a `CubicBezierEasing`; las
transiciones interrumpibles usan `animate*AsState` / `Animatable`, y las
entradas usan `AnimatedVisibility` / `AnimatedContent` con estos specs. No se
usa ninguna tecnología web.

---

## 3. Color

Estrategia **contenida**: neutros cálidos de papel, tinta, un acento petróleo y
una paleta clínica que solo aparece cuando significa algo.

### Roles base

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `paper` | `#FBFAF7` | `#121514` | Fondo / superficie |
| `paperLow` | `#F4F2ED` | `#181C1A` | Agrupaciones, avisos |
| `paperContainer` | `#EDEAE3` | `#1D2120` | Hoja inferior, paneles |
| `paperHigh` | `#E6E2DA` | `#252A28` | Campo activo, selección |
| `paperHighest` | `#DFDBD2` | `#2E3431` | Máximo contraste de superficie |
| `ink` | `#1A1C1B` | `#E6E3DC` | Texto principal |
| `inkMuted` | `#4F5452` | `#B8BDB9` | Texto secundario (≥ 4.5:1, nunca gris débil) |
| `rule` | `#D8D4CB` | `#3A403D` | Divisores tipo hoja impresa (1dp) |
| `outline` | `#7A7F7C` | `#8A908C` | Bordes de campos (≥ 3:1) |
| `primary` (petróleo) | `#0E6B6B` | `#7FD3CC` | Acción principal, serie principal |
| `error` (rojo de calendario) | `#B42318` | `#FF9A8E` | Alertas y validación |

**Sin Dynamic Color.** Un rojo de alerta tiene que significar lo mismo en
cualquier teléfono, y la identidad de Kora no depende del fondo de pantalla.

### Estados clínicos

Cada estado tiene **color + palabra + forma**. El color nunca va solo.

| Estado | Palabra en UI | Forma | Acento claro | Acento oscuro |
|---|---|---|---|---|
| Normal / en rango | "Normal", "En rango" | ✓ marca | `#2F6B3B` | `#8FD19A` |
| Elevada | "Elevada" | ↑ una flecha | `#8A5A00` | `#F2C26B` |
| Hipertensión nivel 1 | "Hipertensión 1" | ↑↑ dos | `#A4410E` | `#FFAB80` |
| Hipertensión nivel 2 / alta | "Hipertensión 2", "Alta" | ↑↑↑ tres | `#B42318` | `#FF9A8E` |
| Muy alta, requiere atención | "Muy alta: repite la medición" | ⚠ triángulo relleno | fondo `#7F1209` + texto blanco | fondo `#A3180D` |
| Baja (glucosa) | "Baja" | ↓ flecha | `#1F4E8C` | `#A9C7F5` |
| Sin datos | "Sin lecturas" | ◌ círculo punteado | `#5F6461` | `#A9AEAA` |

### Series de gráficas

| Serie | Claro | Oscuro | Símbolo |
|---|---|---|---|
| Glucosa / sistólica | `#0E6B6B` | `#7FD3CC` | círculo lleno |
| Diastólica | `#7A4E2D` | `#E0B08A` | cuadrado |
| Pulso (panel propio) | `#5A6130` | `#B9C58A` | rombo |

### Verificación

`KoraTonesContrastTest` (JVM, corre en CI) exige ≥ 4.5:1 en texto y ≥ 3:1 en
series y bordes sobre todas las superficies, en claro y oscuro. Los 128 pares
pasan; el más ajustado es el texto ámbar de "Elevada" sobre `paperHigh`, con
4.59:1.

---

## 4. Tipografía

- **Barlow** (Regular, Medium, SemiBold) para texto: grotesca de proporciones
  amplias y muy legible en tamaños grandes.
- **Barlow Condensed** (Medium, SemiBold) para el numeral del día y **todo
  valor clínico**, siempre con cifras tabulares (`tnum`).
- Licencia SIL OFL 1.1 (`licenses/Barlow-OFL.txt`). Van empaquetadas en
  `res/font`, sin depender de Google Play Services para descargarlas.
- Todo en `sp`, así que la app respeta el tamaño de fuente del sistema.

| Rol | Tamaño / interlineado | Uso |
|---|---|---|
| `numerals.dayNumeral` | 112 / 104 Condensed SemiBold | Numeral de la hoja del día |
| `numerals.valueLarge` | 56 / 58 | Última presión y glucosa |
| `numerals.valueMedium` | 34 / 38 | Historial, paneles |
| `numerals.valueSmall` | 22 / 26 | Tablas, stock, versión |
| `numerals.unit` | 15 / 20 Medium | mg/dL, mmHg, lpm |
| `display*` | 64 · 52 · 40 Condensed | Cifras destacadas en pantalla expandida |
| `headline*` | 32 · 28 · 24 SemiBold | Títulos de pantalla |
| `title*` | 22 · 18 · 16 SemiBold | Secciones |
| `bodyLarge` | **17** / 25 | Texto base (más grande que el 16 de M3) |
| `bodyMedium` / `bodySmall` | 16 / 23 · 14 / 20 | Secundario |
| `label*` | 16 · 14 · 13 | Botones, chips |

Reglas: siempre en minúsculas de oración, nunca versalitas espaciadas; nada
menor a 13sp; la unidad siempre separada del valor y en un tamaño menor.

---

## 5. Espacio, forma y superficie

- **Espaciado** (`KoraSpacing`, base 4dp): 4 · 8 · 12 · 16 · 24 · 32 · 48, más
  `section` = 40dp entre bloques. El margen lateral es 16dp en compacto y 24dp
  en expandido. El objetivo táctil mínimo es 48dp.
- **Esquinas** (`KoraShapes`): 4 · 8 · 12 · 16 · 24. Los controles van en
  8–12 y las hojas inferiores en 24.
- **Superficies:** planas. La profundidad se marca con un paso de tono
  (`paper` → `paperLow` → `paperContainer`). Solo lo que flota (hoja inferior,
  menú, diálogo) lleva elevación.
- **Agrupación editorial antes que tarjetas.** Las listas son filas con
  divisor `rule` de 1dp, como una hoja impresa. Una tarjeta se usa solo cuando
  agrupa algo que se toca como unidad, y nunca hay tarjetas anidadas.

## 6. Iconografía

Se usan Material Symbols *Outlined* (`material-icons-extended`) con un único
trazo y peso. Un ícono acompaña a una palabra y nunca la sustituye en acciones
clínicas. No hay íconos dentro de círculos de color ni emoji.

## 7. Movimiento

| Momento | Tratamiento | Spec |
|---|---|---|
| Presionar cualquier control | Escala 0.97 | `responsive()` |
| Abrir registro rápido | Hoja inferior | `SHEET` 320ms, `KoraEasing.Sheet` |
| Cerrar registro | Salida rápida | `EXIT` 160ms |
| Lectura guardada | La fila nueva entra en la hoja del día (0.95 → 1 + opacidad) y vibra con `CONFIRM` | `enter()` 240ms |
| Toma confirmada | La marca hueca se llena (sin desplazamiento) | `fade()` 180ms |
| Deshacer toma | Snackbar con "Deshacer" | M3 Snackbar |
| Cambio de estado de salud | Fundido del color y cambio de palabra | `fade()` |
| Stock actualizado | El grosor del taco se ajusta | `move()` |
| Cambiar periodo de gráfica | `AnimatedContent` con fundido | `fade()` |
| Cambio de postura del Fold | Panel compartido con `move()`; contenido sin re-entrada | `move()` |

Hápticos con moderación: `CONFIRM` al guardar, `REJECT` en validación fallida.
Nunca en scroll ni en navegación.

## 8. Componentes (Parte 3)

| Componente | Anatomía |
|---|---|
| `KoraHealthStatus` | Línea de estado de la hoja: palabra, lectura que la origina y hora. Si faltan datos dice "Sin lecturas de glucosa hoy". |
| `KoraVitalCard` | Valor grande condensado + unidad + estado (palabra + forma) + contexto/hora. Fila editorial, no tarjeta flotante. |
| `KoraMedicationCard` | Nombre, dosis, horario y marca de celda fija; `KoraStockIndicator` a la derecha. |
| `KoraQuickEntryButton` | Botón tonal de 56dp de alto con ícono y verbo ("Registrar presión"). |
| `KoraNumericInput` | Valor a `valueLarge`, teclado numérico, validación inmediata con rango y unidad visibles. |
| `KoraChartCard` | Título, selector 7/30/90 (SegmentedButton), gráfica Canvas y alternativa textual. |
| `KoraMedicationTimeline` | Horas a la izquierda en cifras tabulares y tomas en filas con marca. |
| `KoraSectionHeader` | Título en `titleMedium` + acción de texto opcional. Sin eyebrow. |
| `KoraEmptyState` | Una frase que dice qué falta y un botón para registrarlo. |
| `KoraLoadingState` | Esqueleto de la hoja con el mismo layout (sin spinner a pantalla completa). |
| `KoraAlertBanner` | Contenedor del estado, ícono, mensaje prudente y acción. |
| `KoraStockIndicator` | Grosor del taco: barras horizontales apiladas según los días restantes, más "Quedan N". |

## 9. Posturas

| Postura | Composición |
|---|---|
| **Compacta** (exterior) | Hoja del día arriba (≈30%): estado, pendientes y últimas lecturas. Acciones de registro en la zona del pulgar sobre la NavigationBar. |
| **Flex** (mesa, bisagra horizontal semiabierta) | Arriba: temporizador de reposo de 5 min, última presión y "Lista para medir". Abajo: sistólica, diastólica, pulso, brazo y botón Guardar de 56dp. Nada a menos de 16dp de la bisagra. |
| **Expandida** (interior) | NavigationRail. 45% agenda (tomas, stock, últimas lecturas e historial) y 55% escritorio de análisis (gráficas, filtros y lectura descriptiva). Respeta la separación física de la bisagra. |

## 10. Accesibilidad

- 48dp mínimos y 8dp entre objetivos.
- Contraste verificado por prueba automática.
- Fuente del sistema hasta 2×: los layouts se apilan en vez de recortar.
- TalkBack: cada valor se lee completo ("Presión 128 sobre 82 milímetros de
  mercurio, elevada, hoy a las 8:10").
- Las gráficas tienen una alternativa textual con la lista de valores
  visualizados.
- El estado nunca se comunica solo con color.
- Claro y oscuro diseñados y probados.
