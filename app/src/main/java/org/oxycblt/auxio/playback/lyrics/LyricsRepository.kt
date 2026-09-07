/*
 * Copyright (c) 2026 Auxio Project
 * LyricsRepository.kt is part of Auxio.
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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.oxycblt.musikr.Song
import timber.log.Timber as L

/**
 * Finds and reads the lyrics that sit alongside a [Song] on disk, i.e the same directory and file
 * name, only with a `.lrc` extension.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@Singleton
class LyricsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * Load the lyrics of the given [Song].
     *
     * @param song The [Song] to load lyrics for.
     * @return The [LyricLine]s of the song, or null if no lyrics could be found or read.
     */
    suspend fun load(song: Song): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            try {
                val uri = siblingLrc(song.uri) ?: return@withContext null
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    L.d("No lyrics file could be opened at $uri")
                    return@withContext null
                }
                val lyrics = LrcParser.parse(decode(bytes))
                L.d("Loaded ${lyrics?.size ?: 0} lyric lines from $uri")
                lyrics
            } catch (e: Exception) {
                L.d("Unable to read lyrics for ${song.name}: $e")
                null
            }
        }

    /**
     * Resolve the `.lrc` file that sits next to the audio file the given [Uri] points to.
     *
     * Songs are handed out as SAF tree document URIs of the form
     * `content://<authority>/tree/<tree>/document/<volume>:<directory>/<file>`. Swapping the file
     * name for it's `.lrc` equivalent yields a sibling that the same tree grant already covers.
     *
     * ponytail: only works for music loaded over SAF (the default). Songs loaded from the system
     * MediaStore database have opaque URIs with no way to reach their siblings, so they simply
     * report no lyrics. Support them by querying `MediaStore.Files` if that ever matters.
     */
    private fun siblingLrc(songUri: Uri): Uri? =
        try {
            val documentId = DocumentsContract.getDocumentId(songUri)
            val directory = documentId.substringBeforeLast('/', "")
            val stem = documentId.substringAfterLast('/').substringBeforeLast('.', "")
            if (directory.isEmpty() || stem.isEmpty()) {
                null
            } else {
                DocumentsContract.buildDocumentUriUsingTree(
                    songUri,
                    "$directory/$stem.$LRC_EXTENSION",
                )
            }
        } catch (e: Exception) {
            // Not a document URI at all (i.e MediaStore), no sibling can be derived.
            null
        }

    private companion object {
        const val LRC_EXTENSION = "lrc"
        const val BOM = "\uFEFF"
        const val REPLACEMENT = "\uFFFD"

        /**
         * Decode lyrics bytes, falling back to GB18030 when UTF-8 decoding produces replacement
         * characters.
         *
         * ponytail: only these two encodings are probed, which covers basically every real-world
         * lyric file. Add more here if a library turns up that needs them.
         */
        fun decode(bytes: ByteArray): String {
            val utf8 = String(bytes, Charsets.UTF_8).removePrefix(BOM)
            return if (REPLACEMENT in utf8) String(bytes, GB18030) else utf8
        }

        val GB18030: Charset = Charset.forName("GB18030")
    }
}
