package com.fidelis.player.util

import android.content.Context
import android.os.Build
import com.fidelis.player.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the phone, so it can be copied from Profile instead of fished out with adb.
 * Only the newest one is kept, and it never leaves the phone unless you copy it.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val dir = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(dir, FILE).writeText(report(thread, error)) }
            // Then let Android close the app as it normally would.
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): Crash? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }?.let(::Crash)
    }.getOrNull()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private fun report(thread: Thread, error: Throwable): String = buildString {
        appendLine("Fidelis ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine(SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date()))
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build. VERSION.SDK_INT})")
        appendLine("Thread: ${thread.name}")
        appendLine()
        append(error.stackTraceToString())
    }

    /** The saved report, with what Profile shows at a glance picked out. */
    class Crash(val text: String) {
        private val lines = text.lines()
        val version = lines.getOrNull(0).orEmpty().removePrefix("Fidelis ").substringBefore(" (")
        val time = lines.getOrNull(1).orEmpty()

        /** The root error in a line, like "IllegalArgumentException: Corner size in Px can't be negative". */
        val headline: String = run {
            val line = lines.lastOrNull { it.startsWith("Caused by: ") }?.removePrefix("Caused by: ")
                ?: lines.drop(5).firstOrNull { it.isNotBlank() }.orEmpty()
            val type = line.substringBefore(':').substringAfterLast('.')
            val message = line.substringAfter(':', "").trim()
            if (message.isEmpty()) type else "$type: $message"
        }
    }
}
