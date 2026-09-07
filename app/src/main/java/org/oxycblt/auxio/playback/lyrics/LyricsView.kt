/*
 * Copyright (c) 2026 Auxio Project
 * LyricsView.kt is part of Auxio.
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
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.NestedScrollView
import kotlin.math.abs
import org.oxycblt.auxio.R
import org.oxycblt.auxio.util.inflater

/**
 * A scrolling column of lyrics that highlights and centers the line currently being sung.
 *
 * Every line is a plain [TextView]. Lyrics are at most a few hundred lines long, so there is no
 * need for view recycling here.
 */
class LyricsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    NestedScrollView(context, attrs) {
    private val container: LinearLayout
    private var activeLine = NO_LYRIC_LINE
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var clickConsumed = false

    init {
        val gutter = resources.getDimensionPixelSize(R.dimen.spacing_medium)
        container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                updatePaddingRelative(start = gutter, end = gutter)
            }
        addView(
            container,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        // The playback panel lives in a bottom sheet. Opting out of nested scrolling means
        // dragging here collapses the sheet, just like it does over the cover pager.
        isNestedScrollingEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    /** Show the given [LyricLine]s. */
    fun setLyrics(lines: List<LyricLine>) {
        container.removeAllViews()
        val inflater = context.inflater
        for (line in lines) {
            val textView = inflater.inflate(R.layout.item_lyrics_line, container, false) as TextView
            textView.text = line.text
            container.addView(textView)
        }
        scrollTo(0, 0)
        // The new children carry no highlight of their own, so re-apply the active line to them
        // rather than waiting for the next update to land.
        container.getChildAt(activeLine)?.isActivated = true
        if (activeLine != NO_LYRIC_LINE) {
            scrollToLine(activeLine, smooth = false)
        }
    }

    /**
     * Highlight the line that should be showing now, scrolling it into view if needed.
     *
     * @param line The index of the line to highlight, or [NO_LYRIC_LINE] to highlight nothing.
     */
    fun setActiveLine(line: Int) {
        if (line == activeLine) {
            return
        }
        container.getChildAt(activeLine)?.isActivated = false
        activeLine = line
        container.getChildAt(line)?.isActivated = true
        scrollToLine(line, smooth = true)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                clickConsumed = false
            }
            // NestedScrollView handles its own gestures and never runs performClick, so a tap
            // would never reach an OnClickListener without forwarding it by hand.
            MotionEvent.ACTION_UP ->
                if (!clickConsumed && abs(ev.y - downY) < touchSlop) {
                    performClick()
                }
        }
        return handled
    }

    override fun performClick(): Boolean {
        clickConsumed = true
        return super.performClick()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // Scroll offsets no longer line up after a size change, so re-place the active line.
        if (changed && activeLine != NO_LYRIC_LINE) {
            scrollToLine(activeLine, smooth = false)
        }
    }

    private fun scrollToLine(line: Int, smooth: Boolean) {
        val child = container.getChildAt(line) ?: return
        // Before the first layout pass there is nothing meaningful to scroll to, onLayout will
        // place the line once we finally have a height.
        if (height == 0) {
            return
        }
        val target = child.top - (height - child.height) / 2
        if (smooth) {
            smoothScrollTo(0, target)
        } else {
            scrollTo(0, target)
        }
    }
}
