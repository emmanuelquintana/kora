package com.emmanuelquintana.kora.core.update

/**
 * Versión semántica (https://semver.org) con precedencia de pre-release.
 * Acepta un prefijo "v" ("v1.2.0") e ignora metadatos de build ("+sha").
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemVer> {

    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: SemVer): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        // Una versión sin pre-release tiene mayor precedencia: 1.0.0 > 1.0.0-dev.5
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return compareValues(other.preRelease.size.coerceAtMost(1), preRelease.size.coerceAtMost(1))
        }
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val a = preRelease[i]
            val b = other.preRelease[i]
            val aNum = a.toLongOrNull()
            val bNum = b.toLongOrNull()
            val result = when {
                aNum != null && bNum != null -> aNum.compareTo(bNum)
                aNum != null -> -1 // los identificadores numéricos van antes que los alfanuméricos
                bNum != null -> 1
                else -> a.compareTo(b)
            }
            if (result != 0) return result
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (isPreRelease) "-" + preRelease.joinToString(".") else ""

    companion object {
        private val PATTERN = Regex(
            """^[vV]?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z.-]+)?$""",
        )

        fun parse(raw: String?): SemVer? {
            val match = PATTERN.matchEntire(raw?.trim() ?: return null) ?: return null
            val (major, minor, patch, pre) = match.destructured
            val preParts = if (pre.isEmpty()) emptyList() else pre.split('.')
            // SemVer prohíbe ceros a la izquierda en identificadores numéricos.
            if (preParts.any { it.length > 1 && it.startsWith('0') && it.all(Char::isDigit) }) return null
            return SemVer(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
                preParts,
            )
        }
    }
}
