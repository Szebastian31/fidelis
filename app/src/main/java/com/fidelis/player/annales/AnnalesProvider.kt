package com.fidelis.player.annales

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.time.LocalDate

/**
 * Shares one day's listening with Annales (see SHARING.md in the Annales repo):
 * content://com.fidelis.player.annales/day/2026-11-02 → music.track, music.artist, music.minutes.
 * Read-only, and only answers Annales itself.
 */
class AnnalesProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val out = MatrixCursor(arrayOf("key", "value"))
        if (callingPackage != ANNALES) return out
        val segs = uri.pathSegments
        if (segs.size != 2 || segs[0] != "day") return out
        val date = runCatching { LocalDate.parse(segs[1]) }.getOrNull() ?: return out
        val day = ListeningLog.get(context ?: return out).day(date) ?: return out
        day.plays.maxByOrNull { it.value }?.key?.let { (artist, title) ->
            out.addRow(arrayOf("music.track", title))
            out.addRow(arrayOf("music.artist", artist))
        }
        val minutes = day.seconds / 60
        if (minutes > 0) out.addRow(arrayOf("music.minutes", "$minutes"))
        return out
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

    private companion object { const val ANNALES = "com.annales.journal" }
}
