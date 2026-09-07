/*
 * Copyright (c) 2026 Auxio Project
 * LrcParser.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.playback.lyrics

/** A [LyricLine] index that refers to no line at all. */
const val NO_LYRIC_LINE = -1

/** A single line of lyrics, alongside the time it should be shown at. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * Parser for LRC files, the de-facto standard for timestamped lyrics.
 *
 * Only "simple" LRC is supported: `[mm:ss.xx]Line`, with several timestamps per line and a global
 * `[offset:]` (in milliseconds) allowed. Metadata tags (`[ti:]`, `[ar:]` and friends) are ignored,
 * as are word-level (enhanced) timestamps.
 */
object LrcParser {
    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    /**
     * Parse the contents of an LRC file.
     *
     * @param text The contents of the LRC file.
     * @return The [LyricLine]s in chronological order, or null if the file had no timestamped lines
     *   at all.
     */
    fun parse(text: String): List<LyricLine>? {
        val offsetMs = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val lines = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val timestamps = TIMESTAMP.findAll(raw).toList()
            if (timestamps.isEmpty()) {
                continue
            }
            // Everything after the last timestamp is the actual lyric. Empty lines are how LRC
            // encodes instrumental breaks, which have nothing to show.
            val body = raw.substring(timestamps.last().range.last + 1).trim()
            if (body.isEmpty()) {
                continue
            }
            for (timestamp in timestamps) {
                val timeMs = parseTimestamp(timestamp) ?: continue
                lines.add(LyricLine((timeMs + offsetMs).coerceAtLeast(0), body))
            }
        }
        if (lines.isEmpty()) {
            return null
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parseTimestamp(timestamp: MatchResult): Long? {
        val minutes = timestamp.groupValues[1].toLongOrNull() ?: return null
        val seconds = timestamp.groupValues[2].toDoubleOrNull() ?: return null
        return (minutes * 60_000 + seconds * 1000).toLong()
    }
}
