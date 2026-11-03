package com.fidelis.player.ui.mood

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.integration.spotify.SpotifyRemote
import com.fidelis.player.integration.spotify.SpotifyRepository
import com.fidelis.player.mood.Mix
import com.fidelis.player.mood.MixStore
import com.fidelis.player.mood.MoodRepository
import com.fidelis.player.mood.Vibe
import com.fidelis.player.mood.toQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MoodViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repo: SpotifyRepository,
    private val moodRepo: MoodRepository,
    private val store: MixStore,
    private val remote: SpotifyRemote,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("mood_dial", Context.MODE_PRIVATE)
    val connected = repo.connected
    val progress = moodRepo.progress
    val mixes = store.mixes

    /** Where the dot is. It starts where you left it, or where the time of day suggests. */
    var vibe by mutableStateOf(Vibe.parse(prefs.getString("last", null)) ?: suggested()); private set
    /** How much of the mix is new to you: 0 just mine, 1 a few new, 2 mostly new. */
    var share by mutableIntStateOf(prefs.getInt("share", 1).coerceIn(0, 2)); private set
    var building by mutableStateOf(false); private set
    var note by mutableStateOf<String?>(null); private set
    /** Set when a mix is ready; the screen opens it, then calls [consumeOpen]. */
    var openMix by mutableStateOf<String?>(null); private set

    val shares = listOf("Just mine", "A few new", "Mostly new")
    private val amounts = listOf(0f, 0.3f, 0.6f)

    fun move(x: Float, y: Float) { vibe = Vibe(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f)); note = null }

    /** Remembers the spot once you let go. */
    fun settle() { prefs.edit().putString("last", vibe.encode()).apply() }

    fun pickShare(i: Int) { share = i; prefs.edit().putInt("share", i).apply() }

    private fun suggested(): Vibe = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..10 -> Vibe(0.45f, 0.35f)
        in 11..16 -> Vibe(0.3f, -0.35f)
        in 17..21 -> Vibe(0.4f, 0.55f)
        else -> Vibe(-0.45f, -0.5f)
    }

    fun make() {
        if (building) return
        val v = vibe
        val i = share
        viewModelScope.launch {
            building = true; note = null
            settle()
            val tracks = runCatching { moodRepo.vibe(v, amounts[i]) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) {
                note = "Nothing in your listening fits this spot yet. Move the dot a little, or add new songs."
            } else {
                val sub = v.nearest.blurb + when (i) { 0 -> " · your songs"; 1 -> " · with a few new ones"; else -> " · mostly new to you" }
                val mix = store.add(v.name + if (i == 2) " · new" else "", sub, tracks)
                prefs.edit().putString("mix:${mix.id}", v.encode()).apply()
                openMix = mix.id
            }
            building = false
        }
    }

    /** The dial spot a saved mix was made at, for its colour. Null for mixes from before the dial. */
    fun vibeOf(m: Mix): Vibe? = Vibe.parse(prefs.getString("mix:${m.id}", null))

    fun consumeOpen() { openMix = null }

    fun playMix(m: Mix) { if (m.tracks.isNotEmpty()) remote.playQueue(m.tracks.map { it.toQueue() }, 0) }

    fun authUrl(): String = repo.authUrl()
}
