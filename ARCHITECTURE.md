# Arquitectura de Kora

## 1. Principios

1. **Seguridad de los datos de salud** antes que cualquier efecto visual.
2. **Un solo estado de aplicación** compartido por los tres layouts (compacto,
   Flex y expandido). Al cambiar de postura cambia la composición, no los datos.
3. **La UI no conoce Firebase.** Habla con interfaces de repositorio. Hay dos
   implementaciones: Firestore (conectada) y Demo (en memoria, ficticia).
4. **Compilación reproducible en la nube.** No hay wrapper binario y no se
   necesita Android Studio.

## 2. Capas y paquetes

```
com.emmanuelquintana.kora
├── KoraApplication.kt          Proceso: contenedor de dependencias y canales de notificación
├── MainActivity.kt             Única Activity: tema, navegación adaptativa, intents
├── core/
│   ├── designsystem/           Tokens y tema (Parte 1) + componentes Kora* (Parte 3)
│   ├── foldable/               FoldablePosture: WindowInfoTracker → postura de la app
│   ├── auth/                   AuthManager: Firebase Auth + Credential Manager
│   ├── database/               Configuración de Firestore (caché offline) y DataStore
│   ├── notifications/          AlarmScheduler, receivers y canales
│   ├── update/                 UpdateChecker, UpdateDownloadWorker, comparación SemVer
│   └── util/                   Formatos es-MX, reloj inyectable, Result
├── data/
│   ├── model/                  Modelos de dominio inmutables
│   ├── mapper/                 Map<String, Any?> ⇄ modelo (sin reflexión, compatible con R8)
│   └── repository/             Interfaces + FirestoreRepository + DemoRepository
├── domain/
│   └── usecase/                Clasificación de presión, rangos de glucosa, stock, horarios
└── ui/
    ├── navigation/             Destinos, NavHost y selección de layout por postura
    ├── components/             Composables compartidos de pantallas
    ├── compact/                CompactScreen (pantalla exterior)
    ├── flex/                   FlexModeScreen (mesa)
    ├── expanded/               ExpandedDualPaneScreen (interior)
    ├── charts/                 GlucoseChart, BloodPressureChart (Canvas) + modelos y tendencias
    ├── screens/                Hoy, Historial, Medicamentos, Perfil/Actualizaciones, Auth, formularios
    └── state/                  ViewModels (sesión, glucosa, presión, medicamentos, auth) y su fábrica
```

**Cambios respecto a la propuesta inicial:**

- **Sin Navigation Compose.** Kora tiene cuatro secciones de primer nivel que
  se muestran a la vez en layouts distintos (barra, riel, dos paneles, Flex).
  Un `NavHost` impone una pila por layout. En su lugar, `KoraShellState`
  (sección, pestaña del historial, periodo y `LazyListState`) vive por encima
  del cambio de postura y se guarda con `rememberSaveable`. Atrás desde otra
  sección regresa a Hoy (`BackHandler`). Las hojas de registro son
  `ModalBottomSheet` cuyo borrador vive en su ViewModel.
- `core/designsystem/` agrupa tokens (`KoraTones`, `KoraColor`, `KoraType`,
  `KoraLayout`, `KoraMotion`) y `KoraTheme`. Los tonos son `Long` ARGB puros
  para poder probar el contraste en la JVM sin Robolectric.

## 3. Flujo de estado

```
Firestore listener / DemoRepository ──► Repository (Flow<T>)
                                           │
                                     UseCases (puros)
                                           │
                                 KoraViewModel (StateFlow<KoraUiState>)
                                           │  collectAsStateWithLifecycle()
                ┌──────────────────────────┼──────────────────────────┐
           CompactScreen             FlexModeScreen         ExpandedDualPaneScreen
```

- **Un ViewModel de sesión** (`KoraViewModel`) con alcance de la Activity
  expone `KoraUiState`: lecturas, medicamentos, estado de sincronización,
  sesión y actualizador. Los tres layouts leen el mismo `StateFlow`, así que
  plegar el teléfono no vuelve a consultar Firestore.
- **Formularios en curso** (sistólica escrita a medias, contexto de glucosa) se
  guardan en `SavedStateHandle` del ViewModel del formulario. Sobreviven a
  rotación, plegado y muerte del proceso.
- **Temporizador de reposo**: guarda el instante de inicio (`elapsedRealtime`)
  en `SavedStateHandle`, no un contador que se reinicia, y el tiempo restante
  se deriva. La notificación de fin se programa con `AlarmManager`, de modo
  que no depende de que la UI siga viva.
- **Selección de sección y scroll**: la sección vive en el ViewModel y el
  scroll en `rememberSaveable(saver = LazyListState.Saver)` con clave por
  sección.
- **Idempotencia**: cada toma registrada usa como ID de documento
  `"{medicationId}_{yyyyMMdd}_{HHmm}"`. Si se toca dos veces, se escribe el
  mismo documento y no se duplica. Las lecturas generan su ID en el cliente
  antes de escribir.

### `android:configChanges`

La Activity declara `screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden|navigation`.
Compose lee `LocalConfiguration` y las métricas de ventana de forma reactiva,
así que plegar o rotar recompone sin recrear la Activity ni producir un
parpadeo. **El estado no depende de esto**: todo lo anterior también
sobrevive a la recreación, que sigue ocurriendo con `uiMode` (tema) y
`density`, para recargar los recursos correctos.

## 4. Plegables

`core/foldable/FoldablePosture.kt` (Parte 2) combina dos fuentes:

1. `WindowInfoTracker.windowLayoutInfo(activity)` aporta los `FoldingFeature`
   con `state` (`FLAT`/`HALF_OPENED`), `orientation`, `bounds` e
   `isSeparating`.
2. `currentWindowAdaptiveInfo()` (Material 3 Adaptive 1.0) aporta la
   `WindowSizeClass` y el `Posture` (`isTabletop`, `hingeList`).

El resultado es `KoraPosture`:

| Postura | Condición |
|---|---|
| `Tabletop(hinge)` | `FoldingFeature` horizontal y `HALF_OPENED` |
| `Book(hinge)` | `FoldingFeature` vertical y `HALF_OPENED` |
| `Expanded(hinge?)` | Ancho `EXPANDED`; incluye la bisagra vertical plana si existe, para respetar su posición |
| `Compact` / `Medium` | El resto |

**Nunca se infiere "plegable" solo por el ancho.** Si el dispositivo no reporta
bisagra, se usa únicamente la `WindowSizeClass`.

`currentWindowAdaptiveInfoV2()` no existe en Adaptive 1.0.0, que es la versión
estable compatible con esta matriz. Cuando se actualice la matriz, se
evaluará migrar.

## 5. Datos

- **Rutas:** `users/{uid}/glucose`, `blood_pressure`, `medications` y
  `medication_logs`. Las reglas (Parte 2) limitan cada documento a
  `request.auth.uid == uid` y validan tipos y rangos.
- **Offline:** caché persistente de Firestore con estados de sincronización
  derivados de `metadata.hasPendingWrites` / `isFromCache`.
- **Cierre de sesión o cambio de cuenta:** se detienen los listeners, se
  ejecuta `FirebaseFirestore.clearPersistence()` tras `terminate()`, se
  cancelan las alarmas y se borra DataStore. En un dispositivo compartido no
  quedan datos clínicos del usuario anterior.
- **Respaldos:** `allowBackup=false` y `data_extraction_rules.xml` excluyen
  todo de la copia en la nube y de la transferencia entre dispositivos.
- **Logs:** nunca se registran valores clínicos. Los errores se reportan por
  tipo (`NetworkError`, `PermissionDenied`) y no con el contenido.

### Modo demostración (sin Firebase)

- Si `app/google-services.json` no existe, `app/build.gradle.kts` no aplica el
  plugin `google-services` y define `BuildConfig.FIREBASE_CONFIGURED = false`.
- La app usa `DemoRepository`, en memoria, con datos ficticios y etiquetados.
  Muestra un aviso permanente de "Modo demostración" y nunca muestra estados
  de "sincronizado".
- `default_web_client_id` (necesario para Google Sign-In) lo genera el plugin
  `google-services`. Sin Firebase ese recurso no existe, por lo que
  `AuthManager` lo resuelve con `Resources.getIdentifier` y desactiva Google
  Sign-In si falta, en lugar de fallar al compilar.

## 6. Inyección de dependencias

**Contenedor manual** (`AppContainer` en `KoraApplication`) en lugar de Hilt:
son pocas dependencias, se evita `kapt`/`ksp` y su acoplamiento con las
versiones de Kotlin y AGP, y es más fácil razonar sobre el modo demo frente al
conectado. Los ViewModels reciben dependencias mediante
`viewModelFactory { initializer { … } }`.

## 7. Servicios del sistema

| Servicio | Diseño (Parte 2) |
|---|---|
| Alarmas | `AlarmManager.setExactAndAllowWhileIdle` si `canScheduleExactAlarms()`; si no, `setAndAllowWhileIdle`, y la UI dice explícitamente que los recordatorios pueden retrasarse. `requestCode` estable = hash del par (medicationId, hora). |
| Reprogramación | `BootReceiver` para `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` y `MY_PACKAGE_REPLACED`. |
| Notificaciones | Canal "Recordatorios de medicamentos" (alta importancia) y canal "Actualizaciones". Acciones "Tomada" y "Posponer 15 min" con `PendingIntent.FLAG_IMMUTABLE`. El ID de notificación es estable para no duplicar. |
| Actualizador | `UpdateChecker` (OkHttp + `org.json`) consulta `releases/latest` y la descarga corre en `UpdateDownloadWorker` (WorkManager, servicio `dataSync`). Guarda en `filesDir/updates/`, verifica el SHA-256 publicado y el certificado de firma con `PackageManager.getPackageArchiveInfo`, e instala con `FileProvider` + `ACTION_VIEW`. |

### Decisiones de implementación (Parte 2)

- **Escrituras sin esperar al servidor.** Con persistencia offline, una `Task`
  de Firestore no se completa hasta que el servidor confirma, lo que sin
  conexión podría tardar horas. Los repositorios lanzan la escritura y
  regresan en cuanto queda aplicada localmente. Los rechazos posteriores
  (reglas, sesión) llegan por `KoraRepository.writeErrors` y la UI los
  muestra.
- **Tomas idempotentes con stock coherente.** `recordDose` usa el ID
  determinista de la toma y un `WriteBatch`: escribe el registro y
  `FieldValue.increment(±unidades)` en el medicamento, operación que también
  funciona sin conexión. Si la toma ya estaba "Tomada", repetir no hace nada;
  cambiarla a "Omitida" o deshacerla devuelve exactamente lo descontado
  (`unitsApplied`). Un `Mutex` evita carreras entre toques rápidos.
- **Registro local de alarmas** (`AlarmRegistry`, SharedPreferences privadas y
  excluidas de respaldos). Guarda nombre, dosis, horas y días de cada
  medicamento para reprogramar tras reiniciar sin red ni sesión, y para
  cancelar las alarmas de medicamentos eliminados. Se borra al cerrar sesión.
- **Notificaciones privadas** en la pantalla de bloqueo
  (`VISIBILITY_PRIVATE` + versión pública genérica): el nombre del
  medicamento no se ve con el teléfono bloqueado.
- **Actualizador:** el APK solo se declara listo si coinciden el tamaño, el
  SHA-256 publicado (cuando existe), el `applicationId`, un `versionCode`
  mayor y el certificado de firma de la app instalada.

## 8. Matriz de versiones

Verificada contra Google Maven y Maven Central el 2026-10-29. **No se mezclan
versiones fuera de esta matriz sin revisar la compatibilidad.**

| Componente | Versión | Restricción |
|---|---|---|
| Gradle | 8.9 | Mínimo para AGP 8.7 |
| AGP | 8.7.3 | compileSdk máximo 35; build-tools 34.0.0 |
| JDK | 17 (Temurin) | Requerido por AGP 8.x |
| Kotlin + Compose compiler plugin | 2.0.21 | El plugin de Compose va con la versión de Kotlin |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | minSdk 26: íconos adaptativos y `java.time` sin desugaring |
| Compose BOM | 2024.12.01 | UI 1.7.6, Material3 1.3.1 |
| Material3 Adaptive | 1.0.0 | Sin `currentWindowAdaptiveInfoV2` |
| androidx.window | 1.3.0 | |
| core-ktx | 1.15.0 | Requiere compileSdk 35 |
| activity-compose / lifecycle | 1.9.3 / 2.8.7 | |
| work-runtime-ktx | 2.10.0 | |
| Firebase BOM | 33.7.0 | auth + firestore (KTX integrado) |
| credentials / googleid | 1.3.0 / 1.1.1 | |
| OkHttp | 4.12.0 | |
| Coroutines | 1.9.0 | |
| org.json (solo pruebas JVM) | 20240303 | El de `android.jar` es un stub en pruebas unitarias |

**Para actualizar:** primero Gradle y AGP juntos según la tabla oficial de
compatibilidad; después compileSdk; después el BOM de Compose, revisando el
`minCompileSdk` de cada AAR. Cada salto debe pasar por CI.

## 9. CI/CD

Archivo: `.github/workflows/build-and-release.yml`.

```
push main / manual ─► build: SDK 35 ─► Gradle 8.9 ─► versión X.Y.Z-dev.N
                      ─► (opcional) google-services.json desde Secret
                      ─► firma persistente (Secrets) o temporal (keytool)
                      ─► testReleaseUnitTest + lintRelease + assembleRelease
                      ─► apksigner verify (+ comparación con KORA_CERT_SHA256)
                      ─► Artifact: APK + .sha256 + reportes

tag vX.Y.Z ─► build (exige firma persistente) ─► release (contents: write)
                                               ─► GitHub Release + APK + .sha256
```

`versionCode` = `MAJOR·1 000 000 + MINOR·10 000 + PATCH·100 + (99 en release | 0 en pre-release)`.
Es monótono, por lo que cualquier build posterior se instala como
actualización.
