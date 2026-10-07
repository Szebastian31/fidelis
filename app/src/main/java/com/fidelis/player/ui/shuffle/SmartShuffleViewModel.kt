package com.fidelis.player.ui.shuffle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.data.MusicRepository
import com.fidelis.player.data.model.Track
import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.lastfm.SimilarityEngine
import com.fidelis.player.playback.PlaybackConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartShuffleViewModel @Inject constructor(
    private val lastfm: LastfmRepository,
    private val library: MusicRepository,
    private val playback: PlaybackConnection,
) : ViewModel() {

    data class Row(val track: Track, val confidence: Int, val why: String)

    var loading by mutableStateOf(false); private set
    var rows by mutableStateOf<List<Row>>(emptyList()); private set
    var note by mutableStateOf<String?>(null); private set

    fun generate() {
        if (loading) return
        viewModelScope.launch {
            loading = true; note = null
            val all = library.tracks().first()
            if (all.isEmpty()) { note = "Scan your library first."; loading = false; return@launch }

            val seed = all.random()
            val byId = all.associateBy { it.id }
            val suggestions = runCatching { lastfm.suggest(seed.id, 50) }.getOrDefault(emptyList())

            rows = if (suggestions.isNotEmpty()) {
                buildList {
                    add(Row(seed, 100, "Seed"))
                    suggestions.forEach { s -> byId[s.trackId]?.let { add(Row(it, s.confidence, label(s.why))) } }
                }
            } else {
                note = "Consensus data is thin — using a flow mix. Connect Last.fm and play tracks to enrich it."
                flowOrder(seed, all)
            }
            loading = false
        }
    }

    /** Start the generated queue on the real player. */
    fun play() {
        if (rows.isEmpty()) return
        playback.play(rows.map { it.track }, 0)
    }

    private fun label(why: SimilarityEngine.Why) = when (why) {
        SimilarityEngine.Why.CROWD -> "Crowd"
        SimilarityEngine.Why.AUDIO -> "Audio"
        SimilarityEngine.Why.CONSENSUS -> "Consensus"
    }

    private fun flowOrder(seed: Track, all: List<Track>): List<Row> {
        val remaining = all.filter { it.id != seed.id }.toMutableList().apply { shuffle() }
        val out = ArrayList<Row>().apply { add(Row(seed, 100, "Seed")) }
        var lastArtist = seed.artist
        while (remaining.isNotEmpty()) {
            val idx = remaining.indexOfFirst { it.artist != lastArtist }.coerceAtLeast(0)
            val t = remaining.removeAt(idx)
            out.add(Row(t, 60, "Flow"))
            lastArtist = t.artist
        }
        return out
    }
}
