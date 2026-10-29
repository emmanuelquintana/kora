import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ── Firebase opcional ────────────────────────────────────────────────────────
// Si el workflow (o tú) colocan app/google-services.json, se aplica el plugin y
// la app arranca en modo conectado. Sin el archivo, el build sigue funcionando y
// la app entra en "Modo demostración" (BuildConfig.FIREBASE_CONFIGURED = false).
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// ── Versión ──────────────────────────────────────────────────────────────────
// Fuente local: version.properties. En CI se sobrescribe con KORA_VERSION_NAME
// (p. ej. "1.2.0" desde el tag v1.2.0, o "1.2.0-dev.57" en pushes a main).
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val koraVersionName: String =
    System.getenv("KORA_VERSION_NAME")?.takeIf { it.isNotBlank() }
        ?: versionProps.getProperty("versionName")

val semver = Regex("""^(\d{1,3})\.(\d{1,2})\.(\d{1,2})(-[0-9A-Za-z.-]+)?$""")
val semverMatch = semver.matchEntire(koraVersionName)
    ?: throw GradleException("versionName '$koraVersionName' no es SemVer X.Y.Z[-pre] válido para Kora")

// versionCode monotónico derivado de SemVer:
//   MAJOR*1_000_000 + MINOR*10_000 + PATCH*100 + (99 si es release, 0 si es pre-release)
// Así 1.2.0-dev.N (…00) < 1.2.0 (…99) < 1.2.1-dev.N, y Android acepta la actualización.
val koraVersionCode: Int = semverMatch.groupValues.let { g ->
    val (major, minor, patch) = Triple(g[1].toInt(), g[2].toInt(), g[3].toInt())
    val isPreRelease = g[4].isNotEmpty()
    major * 1_000_000 + minor * 10_000 + patch * 100 + if (isPreRelease) 0 else 99
}

// ── Firma ────────────────────────────────────────────────────────────────────
// El workflow exporta estas variables. Nunca se guardan en el repositorio.
//   KORA_KEYSTORE_FILE / KORA_KEYSTORE_PASSWORD / KORA_KEY_ALIAS / KORA_KEY_PASSWORD
//   KORA_SIGNING_KIND = "persistent" (keystore de GitHub Secrets) | "ephemeral" (temporal de CI)
val keystorePath: String? = System.getenv("KORA_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val signingKind: String = when {
    keystorePath == null -> "unsigned"
    else -> System.getenv("KORA_SIGNING_KIND")?.takeIf { it.isNotBlank() } ?: "ephemeral"
}

android {
    namespace = "com.emmanuelquintana.kora"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emmanuelquintana.kora"
        minSdk = 26
        targetSdk = 35
        versionCode = koraVersionCode
        versionName = koraVersionName

        vectorDrawables { useSupportLibrary = true }

        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "SIGNING_KIND", "\"$signingKind\"")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"emmanuelquintana/kora\"")
        buildConfigField(
            "String",
            "GIT_SHA",
            "\"${System.getenv("GITHUB_SHA")?.take(7) ?: "local"}\"",
        )

        // Solo español en esta etapa (los textos de Kora viven en values/, es-MX):
        // evita empaquetar las traducciones de otros idiomas de las dependencias.
        resourceConfigurations += listOf("es")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("kora") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KORA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KORA_KEY_ALIAS")
                keyPassword = System.getenv("KORA_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("kora")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        // GradleDependency/NewerVersionAvailable: las versiones se eligen por
        // compatibilidad verificada, no por ser las más nuevas.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        xmlReport = true
        htmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // AndroidX base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Compose + Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Plegables y diseño adaptativo
    implementation(libs.androidx.window)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)

    // Firebase (siempre en el classpath; solo se inicializa si hay google-services.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Inicio de sesión con Google vía Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Red y concurrencia
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Pruebas unitarias (JVM, sin emulador)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
