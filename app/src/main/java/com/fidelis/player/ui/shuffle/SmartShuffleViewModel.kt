package com.fidelis.player.ui.shuffle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fidelis.player.data.db.MusicDao
import com.fidelis.player.data.db.TrackEntity
import com.fidelis.player.integration.lastfm.LastfmRepository
import com.fidelis.player.integration.lastfm.SimilarityEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartShuffleViewModel @Inject constructor(
    private val lastfm: LastfmRepository,
    private val dao: MusicDao,
) : ViewModel() {

    data class Row(val track: TrackEntity, val confidence: Int, val why: String)

    var loading by mutableStateOf(false); private set
    var rows by mutableStateOf<List<Row>>(emptyList()); private set
    var note by mutableStateOf<String?>(null); private set

    fun generate() {
        if (loading) return
        viewModelScope.launch {
            loading = true; note = null
            val library = dao.allTracks()
            if (library.isEmpty()) { note = "Scan your library first."; loading = false; return@launch }

            val seed = library.random()
            val byId = library.associateBy { it.id }
            val suggestions = runCatching { lastfm.suggest(seed.id, 50) }.getOrDefault(emptyList())

            rows = if (suggestions.isNotEmpty()) {
                buildList {
                    add(Row(seed, 100, "Seed"))
                    suggestions.forEach { s -> byId[s.trackId]?.let { add(Row(it, s.confidence, label(s.why))) } }
                }
            } else {
                note = "Consensus data is thin — using a flow mix. Connect Last.fm and play tracks to enrich it."
                flowOrder(seed, library)
            }
            loading = false
        }
    }

    fun orderedIds(): List<Long> = rows.map { it.track.id }

    private fun label(why: SimilarityEngine.Why) = when (why) {
        SimilarityEngine.Why.CROWD -> "Crowd"
        SimilarityEngine.Why.AUDIO -> "Audio"
        SimilarityEngine.Why.CONSENSUS -> "Consensus"
    }

    /** Fallback: start at the seed, then always prefer a track by a different artist. */
    private fun flowOrder(seed: TrackEntity, library: List<TrackEntity>): List<Row> {
        val remaining = library.filter { it.id != seed.id }.toMutableList().apply { shuffle() }
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
