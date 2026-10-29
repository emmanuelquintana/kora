package com.emmanuelquintana.kora.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * Comprueba que un APK descargado sea una actualización legítima de Kora:
 * mismo applicationId, versionCode mayor y el mismo certificado de firma que
 * la app instalada. Nunca intenta forzar una instalación con firma distinta.
 */
object ApkVerifier {

    fun verify(context: Context, apk: File): UpdateFailure? {
        if (!apk.isFile || apk.length() == 0L) return UpdateFailure.FILE_MISSING
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return UpdateFailure.NOT_AN_APK
        if (archive.packageName != context.packageName) return UpdateFailure.NOT_AN_APK

        val installed = installedInfo(pm, context.packageName) ?: return UpdateFailure.UNKNOWN
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            return UpdateFailure.NOT_NEWER
        }

        val archiveCerts = certificateDigests(archive)
        val installedCerts = certificateDigests(installed)
        if (archiveCerts.isEmpty() || installedCerts.isEmpty() || !archiveCerts.containsAll(installedCerts)) {
            return UpdateFailure.SIGNATURE_MISMATCH
        }
        return null
    }

    /** Huellas SHA-256 del certificado con el que está firmada la app instalada. */
    fun installedCertificateSha256(context: Context): Set<String> =
        installedInfo(context.packageManager, context.packageName)?.let(::certificateDigests).orEmpty()

    private val signingFlags: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.PackageInfoFlags.of(signingFlags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, signingFlags)
        }

    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(signingFlags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, signingFlags)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo ?: return emptySet()
            if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        if (signatures.isNullOrEmpty()) return emptySet()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { signature ->
            digest.digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }
}
