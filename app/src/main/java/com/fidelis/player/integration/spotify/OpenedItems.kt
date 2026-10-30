package com.fidelis.player.integration.spotify

import com.spotify.protocol.types.ListItem

/** Hands a Spotify item to the page that shows it (navigation only carries text). */
object OpenedItems {
    private val items = HashMap<String, ListItem>()
    private var next = 0
    fun put(item: ListItem): String = "i${next++}".also { items[it] = item }
    fun get(key: String): ListItem? = items[key]
}
