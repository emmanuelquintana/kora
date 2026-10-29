# ── Kora: reglas de R8 para el build release ─────────────────────────────────

# Conserva números de línea en stack traces sin exponer nombres de archivo fuente.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Credential Manager: la implementación de Play Services se carga por reflexión.
# Regla documentada por androidx.credentials.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Firestore: Kora mapea documentos manualmente (Map<String, Any?>) en data/mapper,
# por lo que no se requieren reglas de reflexión para modelos. Si en el futuro se
# usa toObject(), añade -keep para esas clases aquí.

# OkHttp: advertencias de plataformas opcionales (Conscrypt/BouncyCastle/OpenJSSE).
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
