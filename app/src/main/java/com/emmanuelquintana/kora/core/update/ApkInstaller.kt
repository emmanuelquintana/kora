package com.emmanuelquintana.kora.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface InstallLaunch {
    /** Android exige permitir "Instalar apps desconocidas" para Kora. */
    data class NeedsPermission(val settingsIntent: Intent) : InstallLaunch

    /** Intent listo para entregar el APK al instalador del sistema. */
    data class Ready(val intent: Intent) : InstallLaunch

    data object FileMissing : InstallLaunch
}

/** Entrega el APK verificado al instalador del sistema mediante FileProvider. */
object ApkInstaller {
    const val APK_MIME = "application/vnd.android.package-archive"

    fun prepare(context: Context, apk: File): InstallLaunch {
        if (!apk.isFile) return InstallLaunch.FileMissing
        if (!context.packageManager.canRequestPackageInstalls()) {
            return InstallLaunch.NeedsPermission(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
            )
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return InstallLaunch.Ready(intent)
    }
}
