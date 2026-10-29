package com.emmanuelquintana.kora.core.update

import org.json.JSONException
import org.json.JSONObject

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String,
)

data class GitHubRelease(
    val tagName: String,
    val title: String?,
    val notes: String,
    val htmlUrl: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset>,
)

/** Actualización lista para descargar. */
data class UpdateInfo(
    val version: SemVer,
    val tagName: String,
    val notes: String,
    val htmlUrl: String?,
    val apk: ReleaseAsset,
    /** Archivo `<apk>.sha256` publicado por el workflow; puede faltar en Releases antiguos. */
    val checksum: ReleaseAsset?,
)

sealed interface ReleaseSelection {
    data class Available(val info: UpdateInfo) : ReleaseSelection
    data class UpToDate(val latest: SemVer) : ReleaseSelection
    data object InvalidVersion : ReleaseSelection
    data object NoApk : ReleaseSelection
}

object GitHubReleaseParser {

    /** Interpreta la respuesta de `GET /repos/{owner}/{repo}/releases/latest`; null si no es válida. */
    fun parse(json: String): GitHubRelease? = try {
        val root = JSONObject(json)
        val assetsJson = root.optJSONArray("assets")
        val assets = buildList {
            if (assetsJson != null) {
                for (i in 0 until assetsJson.length()) {
                    val asset = assetsJson.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.isNotBlank() && url.startsWith("https://")) {
                        add(ReleaseAsset(name, url, asset.optLong("size", -1L), asset.optString("content_type")))
                    }
                }
            }
        }
        val tag = root.optString("tag_name")
        if (tag.isBlank()) {
            null
        } else {
            GitHubRelease(
                tagName = tag,
                title = root.optString("name").takeIf { it.isNotBlank() && it != "null" },
                notes = root.optString("body").takeIf { it != "null" }.orEmpty(),
                htmlUrl = root.optString("html_url").takeIf { it.startsWith("https://") },
                draft = root.optBoolean("draft", false),
                prerelease = root.optBoolean("prerelease", false),
                assets = assets,
            )
        }
    } catch (_: JSONException) {
        null
    }
}

object ReleaseSelector {
    private const val APK_MIME = "application/vnd.android.package-archive"

    /**
     * Elige el APK oficial del Release. Prioriza el nombre exacto que genera el
     * workflow (`Kora-vX.Y.Z.apk`); si no existe, acepta un único APK que no sea
     * de prueba. Con varios candidatos ambiguos no adivina.
     */
    fun select(release: GitHubRelease, current: SemVer): ReleaseSelection {
        val version = SemVer.parse(release.tagName) ?: return ReleaseSelection.InvalidVersion
        if (release.draft) return ReleaseSelection.InvalidVersion
        if (version <= current) return ReleaseSelection.UpToDate(version)

        val apks = release.assets.filter {
            (it.name.endsWith(".apk", ignoreCase = true) || it.contentType == APK_MIME) &&
                !it.name.contains("prueba", ignoreCase = true)
        }
        val apk = apks.firstOrNull { it.name == "Kora-v$version.apk" }
            ?: apks.singleOrNull()
            ?: return ReleaseSelection.NoApk
        val checksum = release.assets.firstOrNull { it.name == "${apk.name}.sha256" }
        return ReleaseSelection.Available(
            UpdateInfo(version, release.tagName, release.notes, release.htmlUrl, apk, checksum),
        )
    }

    /** Extrae el hash de un archivo `sha256sum` ("<hex>  <archivo>"). */
    fun parseChecksum(content: String): String? {
        val token = content.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return null
        return token.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
    }
}
