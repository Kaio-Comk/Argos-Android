package com.twinfinder.app.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.R as M
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.twinfinder.app.R
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Estatísticas das limpezas concluídas. */
class StatsActivity : AppCompatActivity() {

    private val pad by lazy { (16 * resources.displayMetrics.density).toInt() }
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        root.addView(MaterialToolbar(this).apply {
            setTitle(R.string.stats_title)
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, 0, pad, pad) }
        root.addView(NestedScrollView(this).apply { addView(body) })
        setContentView(root)
        lifecycleScope.launch { render() }
    }

    private suspend fun render() {
        val s = SessionRepository(this).statsSummary()
        body.removeAllViews()
        if (s.reviewed == 0) {
            body.addView(text(getString(R.string.stats_empty), M.style.TextAppearance_Material3_BodyLarge).apply { setPadding(0, pad * 4, 0, 0) })
            return
        }
        card {
            it.addView(text(getString(R.string.stats_space_saved), M.style.TextAppearance_Material3_LabelLarge))
            it.addView(text(Fmt.size(this, s.freedBytes), M.style.TextAppearance_Material3_DisplaySmall))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        val cardSpeed = smallCard(getString(R.string.stats_speed), getString(R.string.stats_speed_value, s.perHour))
        val cardTotal = smallCard(getString(R.string.stats_total), s.reviewed.toString())
        row.addView(cardSpeed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            topMargin = pad / 2
            marginEnd = pad / 4
        })
        row.addView(cardTotal, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            topMargin = pad / 2
            marginStart = pad / 4
        })
        body.addView(row)

        card {
            it.addView(text(getString(R.string.stats_style), M.style.TextAppearance_Material3_TitleMedium))
            val total = (s.style.kept + s.style.deleted + s.style.moved + s.style.skipped).coerceAtLeast(1)
            bar(it, getString(R.string.stats_kept, s.style.kept), s.style.kept, total, R.color.keep)
            bar(it, getString(R.string.stats_deleted, s.style.deleted), s.style.deleted, total, R.color.delete)
            bar(it, getString(R.string.stats_moved, s.style.moved), s.style.moved, total, R.color.move)
            bar(it, getString(R.string.stats_skipped, s.style.skipped), s.style.skipped, total, R.color.brand)
            val sessionStr = resources.getQuantityString(R.plurals.stats_sessions, s.sessions, s.sessions, Fmt.elapsed(s.timeMs))
            it.addView(text(sessionStr, M.style.TextAppearance_Material3_BodyMedium).apply {
                setPadding(0, pad, 0, 0)
            })
        }
        s.bestMonth?.let { month ->
            card {
                it.addView(text(getString(R.string.stats_best_month), M.style.TextAppearance_Material3_LabelLarge))
                it.addView(text(getString(R.string.stats_best_month_value, month, s.bestMonthReviewed), M.style.TextAppearance_Material3_TitleLarge))
            }
        }
        body.addView(text(getString(R.string.stats_since, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.since))),
            M.style.TextAppearance_Material3_BodySmall).apply { setPadding(0, pad, 0, 0) })
    }

    private fun text(value: String, style: Int) = TextView(this).apply {
        text = value
        setTextAppearance(style)
    }

    private fun card(fill: (LinearLayout) -> Unit) {
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fill(inner)
        body.addView(MaterialCardView(this).apply {
            addView(inner)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad / 2 }
        })
    }

    private fun smallCard(label: String, value: String) = MaterialCardView(this).apply {
        val inner = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        inner.addView(text(label, M.style.TextAppearance_Material3_LabelLarge))
        inner.addView(text(value, M.style.TextAppearance_Material3_TitleLarge).apply { maxLines = 1 })
        addView(inner)
    }

    private fun bar(parent: LinearLayout, label: String, value: Int, total: Int, color: Int) {
        parent.addView(text(label, M.style.TextAppearance_Material3_BodyMedium).apply { setPadding(0, pad / 2, 0, pad / 4) })
        parent.addView(LinearProgressIndicator(this).apply {
            max = total
            progress = value
            setIndicatorColor(ContextCompat.getColor(context, color))
            trackColor = ContextCompat.getColor(context, R.color.scrim)
            trackCornerRadius = pad
            trackThickness = pad / 2
        })
    }
}
