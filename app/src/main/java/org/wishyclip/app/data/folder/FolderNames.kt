package org.wishyclip.app.data.folder

/** Turns project names into safe, unique folder names. Pure Kotlin, covered by JVM tests. */
object FolderNames {
    private val BAD = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")
    const val MAX_LENGTH = 80

    /** A name every common file system and storage provider accepts. Never empty. */
    fun sanitize(projectName: String): String {
        var s = BAD.replace(projectName, "_").replace(Regex("\\s+"), " ").trim()
        s = s.trimStart('.').trimEnd('.', ' ')
        if (s.length > MAX_LENGTH) s = s.take(MAX_LENGTH).trimEnd('.', ' ')
        return s.ifEmpty { "Untitled" }
    }

    /** [base], or "base (2)", "base (3)"... the first one not in [taken] (case-insensitive). */
    fun unique(base: String, taken: Collection<String>): String {
        val lower = taken.map { it.lowercase() }.toHashSet()
        if (base.lowercase() !in lower) return base
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = base.take(MAX_LENGTH - suffix.length).trimEnd('.', ' ') + suffix
            if (candidate.lowercase() !in lower) return candidate
            n++
        }
    }
}
