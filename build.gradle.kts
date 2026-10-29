// Plugins declarados una sola vez; cada módulo decide cuáles aplica.
// google-services se aplica en :app solo si existe app/google-services.json,
// de modo que el proyecto compila sin Firebase configurado.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
