# Kora

**Claridad para cuidar lo importante.** Kora es una app Android nativa (Kotlin +
Jetpack Compose + Material 3) para el seguimiento personal de glucosa, presión
arterial y medicamentos. Está diseñada primero para el Galaxy Z Fold y funciona
en teléfonos convencionales y tablets.

> Kora es una herramienta de seguimiento, **no un dispositivo de diagnóstico**.
> Las clasificaciones son orientativas y no sustituyen la valoración de un
> profesional de la salud.

| Documento | Contenido |
|---|---|
| [PRODUCT.md](PRODUCT.md) | Usuarios, propósito, restricciones y principios del producto |
| [KORA_DESIGN_SYSTEM.md](KORA_DESIGN_SYSTEM.md) | Dirección visual "Hoja del día", tokens, componentes y movimiento |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Capas, estado, plegables, datos, servicios y matriz de versiones |

**Estado:** Parte 1 de 3. Incluye configuración, arquitectura, CI/CD, tema y una
pantalla de verificación. Core y datos llegan en la Parte 2, y la UI completa
en la Parte 3.

---

## Cómo se compila

No hace falta Android Studio. Todo se compila en **GitHub Actions** (Ubuntu,
JDK 17, Gradle 8.9 instalado por `gradle/actions/setup-gradle@v4`). El
repositorio **no** incluye `gradlew` ni `gradle-wrapper.jar`.

---

## 1. Crear el repositorio y subir los archivos

En GitHub, crea el repositorio **`emmanuelquintana/kora`** vacío, sin README ni
.gitignore. Desde esta carpeta (git ya está inicializado en la rama `main`):

```bash
git add .
```

```bash
git commit -m "Parte 1: configuración, arquitectura y CI/CD"
```

```bash
git remote add origin https://github.com/emmanuelquintana/kora.git
```

```bash
git push -u origin main
```

Sin terminal también se puede: en GitHub, **Add file → Upload files**,
arrastra el contenido de la carpeta (incluida `.github/`) y confirma en `main`.

## 2. Comprobar GitHub Actions

1. Abre la pestaña **Actions** del repositorio. El push a `main` dispara
   **Build and release**.
2. El job **Compilar y validar** instala el SDK 35, ejecuta las pruebas
   unitarias (`testReleaseUnitTest`), lint (`lintRelease`) y genera el APK
   (`assembleRelease`).
3. El resumen del job muestra la versión, el tipo de firma, la huella SHA-256
   del certificado y si Firebase está configurado.

## 3. Obtener el APK

- **Builds de `main`:** en la ejecución terminada, sección **Artifacts**,
  descarga `kora-0.1.0-dev.N`. Es un .zip con el APK y su `.sha256`. Los
  artifacts duran 30 días.
- **Releases oficiales:** crea un tag con la versión y súbelo:

```bash
git tag -a v0.1.0 -m "Kora 0.1.0"
```

```bash
git push origin v0.1.0
```

El workflow publica **Releases → Kora v0.1.0** con `Kora-v0.1.0.apk` y
`Kora-v0.1.0.apk.sha256`. El auto-actualizador de la app (Parte 2) lee
justamente esos Releases.

Para instalar en el Fold, abre el APK desde el teléfono y, si Android lo pide,
permite "Instalar apps desconocidas" para el navegador o el gestor de archivos.

## 4. Firma del APK

### Sin secrets (situación inicial)

El workflow genera una **firma temporal** en cada ejecución. El APK se llama
`Kora-v0.1.0-dev.N-PRUEBA-firma-temporal.apk` y la app muestra el aviso
"Temporal · solo para pruebas".

**Limitaciones de la firma temporal:**

- Cada build tiene un certificado distinto. Android **no** permite instalar un
  build encima de otro: hay que desinstalar primero, y eso **borra los datos
  locales** (en modo conectado, los datos de Firestore se conservan en la nube).
- No sirve para Releases oficiales: un tag `v*` sin keystore **falla a
  propósito**.
- El auto-actualizador no puede actualizar una instalación con firma temporal.
- Google Sign-In no funcionará, porque Firebase necesita la huella de un
  certificado fijo.

### Crear el keystore persistente (una sola vez, sin Android Studio)

Usa un entorno de nube con JDK, como **GitHub Codespaces** (abre cualquier
repositorio → Code → Codespaces → New) o **Google Cloud Shell**. Ambos traen
`keytool`.

1. Genera el keystore. Te pedirá una contraseña; usa una larga y guárdala en tu
   gestor de contraseñas. Con PKCS12, la contraseña de la llave es la misma que
   la del almacén.

```bash
keytool -genkeypair -v -keystore kora-release.jks -storetype PKCS12 -alias kora -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Emmanuel Quintana, O=Kora, C=MX"
```

2. Obtén las huellas SHA-1 y SHA-256 (las necesitarás para Firebase y para
   `KORA_CERT_SHA256`):

```bash
keytool -list -v -keystore kora-release.jks -alias kora
```

3. Conviértelo a Base64 en una sola línea:

```bash
base64 -w0 kora-release.jks > kora-release.jks.b64
```

4. Configura los secrets. Con GitHub CLI, desde el mismo entorno:

```bash
gh secret set KEYSTORE_BASE64 --repo emmanuelquintana/kora < kora-release.jks.b64
```

```bash
gh secret set KEYSTORE_PASSWORD --repo emmanuelquintana/kora
```

```bash
gh secret set KEY_ALIAS --repo emmanuelquintana/kora --body kora
```

```bash
gh secret set KEY_PASSWORD --repo emmanuelquintana/kora
```

Los comandos sin `--body` te piden el valor de forma interactiva, así que no
queda en el historial. También puedes hacerlo en GitHub: **Settings → Secrets
and variables → Actions → New repository secret**.

5. Opcional, pero muy recomendado: registra la huella pública para que CI
   rechace cualquier build firmado con otra llave. No es un secreto, va como
   **variable**:

```bash
gh variable set KORA_CERT_SHA256 --repo emmanuelquintana/kora --body "AA:BB:...:FF"
```

6. **Respaldo:** descarga `kora-release.jks` a un lugar seguro y fuera de línea
   (gestor de contraseñas con adjuntos o una memoria cifrada). Después
   **bórralo del entorno de nube**:

```bash
rm kora-release.jks kora-release.jks.b64
```

> Si pierdes el keystore o su contraseña, **ninguna instalación existente
> podrá actualizarse**: habrá que desinstalar y empezar de nuevo. Nunca lo
> subas al repositorio (`.gitignore` ya excluye `*.jks` y `*.b64`).

Una vez configurados los secrets, los builds de `main` y los tags se firman con
la misma identidad y se instalan unos sobre otros conservando los datos.

## 5. Firebase (opcional, se puede hacer después)

1. En [console.firebase.google.com](https://console.firebase.google.com), crea
   un proyecto y añade una app Android con el paquete
   **`com.emmanuelquintana.kora`**.
2. En **Configuración del proyecto → Tus apps → Huellas digitales**, añade el
   **SHA-1 y el SHA-256** del keystore persistente (paso 4.2). Sin ellos,
   Google Sign-In falla.
3. En **Authentication → Método de acceso**, habilita **Correo/contraseña** y
   **Google**.
4. Crea **Cloud Firestore** en modo producción, en la región `nam5` o la más
   cercana. Las reglas se entregan en la Parte 2 (`firestore.rules`).
5. Descarga `google-services.json` **después** de añadir las huellas (así
   incluye el cliente OAuth web) y guárdalo como secret:

```bash
gh secret set GOOGLE_SERVICES_JSON --repo emmanuelquintana/kora < google-services.json
```

El workflow lo escribe en `app/google-services.json` solo durante el build,
verifica que contenga el paquete correcto y lo borra al terminar.

## Qué funciona sin Firebase

| Funciona | No funciona (hasta configurar Firebase) |
|---|---|
| Compilación, pruebas, lint, firma y Releases | Inicio de sesión (correo y Google) |
| Tema claro y oscuro, tipografía, ícono y splash | Sincronización con Firestore |
| Detección de tamaño de ventana y postura del Fold | Datos persistentes en la nube |
| (Partes 2–3) Toda la UI con **datos ficticios** en "Modo demostración", sin simular sincronización | |

## Secrets y variables

| Nombre | Tipo | Obligatorio | Para qué |
|---|---|---|---|
| `KEYSTORE_BASE64` | Secret | Para tags | Keystore persistente en Base64 |
| `KEYSTORE_PASSWORD` | Secret | Para tags | Contraseña del keystore |
| `KEY_ALIAS` | Secret | Para tags | Alias de la llave (`kora`) |
| `KEY_PASSWORD` | Secret | Para tags | Contraseña de la llave (igual a la del almacén en PKCS12) |
| `GOOGLE_SERVICES_JSON` | Secret | No | Configuración de Firebase (JSON o Base64) |
| `KORA_CERT_SHA256` | Variable | No (recomendado) | Huella esperada del certificado de firma |

## Versionado

- `version.properties` define la versión base (`0.1.0`).
- Los pushes a `main` producen `0.1.0-dev.<número de ejecución>`.
- Los tags `vX.Y.Z` producen `X.Y.Z`. Usa SemVer estricto, con MINOR y PATCH ≤ 99.
- `versionCode` se deriva automáticamente y siempre crece (ver ARCHITECTURE.md § 9).

## Licencias de terceros

- Barlow / Barlow Condensed: SIL Open Font License 1.1 ([licenses/Barlow-OFL.txt](licenses/Barlow-OFL.txt)).
