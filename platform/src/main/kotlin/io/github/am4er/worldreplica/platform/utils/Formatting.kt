package io.github.am4er.worldreplica.platform.utils

import java.util.Locale

fun formatCount(n: Long): String = String.format(Locale.ROOT, "%,d", n)

fun formatBytes(n: Long): String = when {
    n >= 1L shl 30 -> String.format(Locale.ROOT, "%.1f GB", n / (1L shl 30).toDouble())
    n >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", n / (1L shl 20).toDouble())
    n >= 1L shl 10 -> String.format(Locale.ROOT, "%.1f KB", n / (1L shl 10).toDouble())
    else -> "$n bytes"
}

fun formatDuration(nanos: Long): String {
    val seconds = nanos / 1_000_000_000.0
    return if (seconds < 60) String.format(Locale.ROOT, "%.1fs", seconds)
    else "${(seconds / 60).toLong()}m ${(seconds % 60).toLong()}s"
}

fun percent(done: Long, total: Long): String = if (total <= 0) "0%" else "${done * 100 / total}%"
