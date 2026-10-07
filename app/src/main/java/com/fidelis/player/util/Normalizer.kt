package com.fidelis.player.util

import java.text.Normalizer as JNormalizer

/** Shared text normalization so the scanner and matcher bucket tracks the same way. */
object Normalizer {

    private val NOISE = Regex(
        "\\b(feat\\.?|ft\\.?|featuring|remaster(ed)?|remastered \\d{4}|deluxe|" +
            "mono|stereo|bonus track|radio edit|album version|explicit)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val PARENS = Regex("[\\(\\[].*?[\\)\\]]")
    private val NON_ALNUM = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")

    /** Collapse accents, casing, parentheticals, and marketing noise to a comparable core. */
    fun normalize(s: String): String {
        var t = JNormalizer.normalize(s, JNormalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
        t = PARENS.replace(t, " ")
        t = NOISE.replace(t, " ")
        t = NON_ALNUM.replace(t, " ")
        return SPACES.replace(t, " ").trim()
    }

    /** Cheap bucket key: first artist + title. */
    fun key(artist: String, title: String): String {
        val a = normalize(artist.substringBefore(',').substringBefore('&'))
        return "$a|${normalize(title)}"
    }
}
