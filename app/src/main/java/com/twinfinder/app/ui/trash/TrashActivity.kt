package com.twinfinder.app.ui.trash

import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.Media
import com.twinfinder.app.data.MediaRepository
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.databinding.ActivityListBinding
import com.twinfinder.app.ui.clean.FullScreenActivity
import com.twinfinder.app.ui.common.MediaActions
import com.twinfinder.app.ui.common.ThumbAdapter
import com.twinfinder.app.ui.common.ThumbMark
import com.twinfinder.app.ui.common.hapticConfirm
import com.twinfinder.app.ui.common.hapticTick
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** O que o TwinFinder mandou para a lixeira do sistema: restaurar ou apagar de vez. */
class TrashActivity : AppCompatActivity() {

    private val actions = MediaActions(this)
    private lateinit var b: ActivityListBinding
    private val db by lazy { AppDatabase.get(this) }
    private var items: List<Pair<Media, Long>> = emptyList()
    private val selected = mutableSetOf<Long>()
    private var cancelSelection: MenuItem? = null
    private val adapter = ThumbAdapter(
        onLongClick = { pos ->
            items.getOrNull(pos)?.let {
                b.root.hapticConfirm()
                startActivity(FullScreenActivity.intent(this, it.first))
            }
            true
        },
        onClick = { pos ->
            b.root.hapticTick()
            toggle(items[pos].first.id)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setTitle(R.string.trash_title)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.list.layoutManager = GridLayoutManager(this, 3)
        b.list.adapter = adapter

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            b.progress.isVisible = false
            b.empty.isVisible = true
            b.empty.setText(R.string.trash_unsupported)
            return
        }
        b.hint.isVisible = true
        b.hint.setText(R.string.trash_info)
        b.toolbar.menu.add(R.string.trash_select_all).setOnMenuItemClickListener {
            if (selected.size == items.size) selected.clear() else selected += items.map { it.first.id }
            render()
            true
        }
        cancelSelection = b.toolbar.menu.add(R.string.trash_cancel_selection).setOnMenuItemClickListener {
            selected.clear()
            render()
            true
        }
        b.secondaryButton.setText(R.string.trash_restore)
        b.secondaryButton.setOnClickListener { it.hapticConfirm(); apply(restore = true) }
        // Sem seleção, o botão principal vira "Apagar tudo".
        b.primaryButton.setOnClickListener { it.hapticConfirm(); if (selected.isEmpty()) deleteAll() else apply(restore = false) }
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        b.progress.isVisible = true
        val ours = db.trash().all().map { it.mediaId }
        items = withContext(Dispatchers.IO) { MediaRepository(this@TrashActivity).loadTrashed(ours) }
        // O que saiu da lixeira por fora do app (restaurado ou expirado) deixa de ser acompanhado.
        val still = items.map { it.first.id }.toSet()
        ours.filterNot { it in still }.takeIf { it.isNotEmpty() }?.let { db.trash().remove(it) }
        selected.retainAll(still)
        b.progress.isVisible = false
        render()
    }

    private fun toggle(id: Long) {
        if (!selected.add(id)) selected.remove(id)
        render()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        adapter.items = items.map { (m, expires) ->
            val days = ((expires - now).coerceAtLeast(0) + TimeUnit.DAYS.toMillis(1) - 1).let { TimeUnit.MILLISECONDS.toDays(it) }.toInt()
            ThumbAdapter.Item(m, if (m.id in selected) ThumbMark.SELECTED else ThumbMark.NONE, label = getString(R.string.trash_days_left, days))
        }
        b.empty.isVisible = items.isEmpty()
        b.empty.setText(R.string.trash_empty)
        val hasSelection = selected.isNotEmpty()
        b.actions.isVisible = items.isNotEmpty()
        b.actionsNote.isVisible = items.isNotEmpty()
        b.actionsNote.text = resources.getQuantityString(R.plurals.trash_summary, items.size, items.size, Fmt.size(this, items.sumOf { it.first.size }))
        b.secondaryButton.isVisible = hasSelection
        b.primaryButton.setText(if (hasSelection) R.string.trash_delete_forever else R.string.trash_delete_all)
        cancelSelection?.isVisible = hasSelection
    }

    private fun deleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.trash_delete_all)
            .setMessage(resources.getQuantityString(R.plurals.trash_delete_all_confirm, items.size, items.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.trash_delete_forever) { _, _ ->
                selected += items.map { it.first.id }
                apply(restore = false)
            }.show()
    }

    private fun apply(restore: Boolean) {
        val chosen = items.map { it.first }.filter { it.id in selected }
        if (chosen.isEmpty()) return
        lifecycleScope.launch {
            val done = if (restore) actions.trash(chosen, trashed = false) else actions.delete(chosen)
            if (done.isNotEmpty()) db.trash().remove(done)
            selected.removeAll(done.toSet())
            load()
        }
    }
}
