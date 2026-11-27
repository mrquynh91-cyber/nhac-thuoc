package com.medlens.ai.ota

object SemVerComparator {
    fun normalize(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V")
    }

    /**
     * Returns > 0 if [remote] is newer than [current], 0 if equal, < 0 if older.
     */
    fun compare(remote: String, current: String): Int {
        val remoteCore = normalize(remote).substringBefore('-')
        val currentCore = normalize(current).substringBefore('-')

        val remoteParts = remoteCore.split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = currentCore.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r.compareTo(c)
        }

        // Stable release > pre-release (e.g. 1.2.0 > 1.2.0-beta.1)
        val remoteIsPre = normalize(remote).contains('-')
        val currentIsPre = normalize(current).contains('-')
        return when {
            !remoteIsPre && currentIsPre -> 1
            remoteIsPre && !currentIsPre -> -1
            else -> 0
        }
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        return compare(remoteTag, currentVersion) > 0
    }
}
