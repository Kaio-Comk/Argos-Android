package com.twinfinder.app.ui.clean

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.GroupSort
import com.twinfinder.app.data.Media
import com.twinfinder.app.data.MediaGroup
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.db.CleanMode
import com.twinfinder.app.databinding.ActivityModeSelectBinding
import com.twinfinder.app.databinding.ItemGroupBinding
import kotlinx.coroutines.launch

/**
 * Escolha da limpeza: busca, agrupar por mês/álbum/tipo, filtro de já revisados e ordenação.
 * A rodada rápida fica fixa no topo.
 */
class GroupPickerActivity : AppCompatActivity() {

    private lateinit var b: ActivityModeSelectBinding
    private lateinit var repo: SessionRepository
    private lateinit var prefs: Prefs
    private lateinit var mode: CleanMode
    private val adapter = GroupAdapter()

    private var library: List<Media> = emptyList()
    private var settled: Set<Long> = emptySet()
    private var albumOrder: Map<Long, Int> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityModeSelectBinding.inflate(layoutInflater)
        setContentView(b.root)
        repo = SessionRepository(this)
        prefs = Prefs(this)
        mode = CleanMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: CleanMode.MONTH.name)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.search.doAfterTextChanged { render() }
        b.groupButton.setOnClickListener { chooseGrouping() }
        b.filterButton.setOnClickListener { chooseFilter() }
        b.sortButton.setOnClickListener { chooseSort() }

        b.randomCard.icon.setImageResource(R.drawable.ic_shuffle)
        b.randomCard.root.setOnClickListener { lifecycleScope.launch { SessionLauncher.startRandom(this@GroupPickerActivity, repo) } }
    }

    override fun onResume() {
        super.onResume()
        b.randomCard.title.text = getString(R.string.mode_random_label, prefs.randomCount)
        b.randomCard.desc.text = getString(R.string.mode_random_desc, prefs.randomCount)
        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        b.progress.isVisible = library.isEmpty()
        library = repo.library()
        settled = repo.settledIds()
        albumOrder = repo.albumOrder()
        b.progress.isVisible = false
        render()
    }

    private fun render() {
        b.groupButton.text = getString(modeLabel(mode))
        b.groupButton.setIconResource(
            when (mode) {
                CleanMode.ALBUM -> R.drawable.ic_album
                CleanMode.TYPE -> R.drawable.ic_media_type
                else -> R.drawable.ic_calendar
            }
        )
        b.sortButton.text = getString(sortLabel(prefs.groupSort))
        b.filterButton.text = getString(R.string.filters) + if (prefs.hideCompleted) " (1)" else ""

        val groups = when (mode) {
            CleanMode.ALBUM -> repo.albumGroups(library, settled)
            CleanMode.TYPE -> repo.typeGroups(library, settled)
            else -> repo.monthGroups(library, settled)
        }
        val query = b.search.text?.toString()?.trim().orEmpty()
        val sort = prefs.groupSort.takeUnless { it == GroupSort.CUSTOM && mode != CleanMode.ALBUM } ?: GroupSort.DEFAULT
        adapter.groups = repo.sortGroups(groups, sort, albumOrder)
            .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }
            .filter { !prefs.hideCompleted || it.remaining > 0 }
        b.empty.isVisible = adapter.groups.isEmpty() && !b.progress.isVisible
        b.empty.setText(if (library.isEmpty()) R.string.group_empty else R.string.no_results)
    }

    private fun chooseGrouping() {
        val modes = listOf(CleanMode.MONTH, CleanMode.ALBUM, CleanMode.TYPE)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.group_by)
            .setSingleChoiceItems(modes.map { getString(modeLabel(it)) }.toTypedArray(), modes.indexOf(mode)) { d, i ->
                mode = modes[i]
                d.dismiss()
                render()
            }.show()
    }

    private fun chooseFilter() {
        val checked = booleanArrayOf(prefs.hideCompleted)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.filters)
            .setMultiChoiceItems(arrayOf(getString(R.string.filter_hide_completed)), checked) { _, _, on -> checked[0] = on }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                prefs.hideCompleted = checked[0]
                render()
            }.show()
    }

    private fun chooseSort() {
        val options = GroupSort.entries.filter { it != GroupSort.CUSTOM || mode == CleanMode.ALBUM }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_groups)
            .setSingleChoiceItems(options.map { getString(sortLabel(it)) }.toTypedArray(), options.indexOf(prefs.groupSort)) { d, i ->
                prefs.groupSort = options[i]
                d.dismiss()
                render()
            }.show()
    }

    private fun modeLabel(m: CleanMode) = when (m) {
        CleanMode.ALBUM -> R.string.mode_album
        CleanMode.TYPE -> R.string.mode_type
        else -> R.string.mode_month
    }

    private fun sortLabel(s: GroupSort) = when (s) {
        GroupSort.DEFAULT -> if (mode == CleanMode.MONTH) R.string.gsort_default_month else R.string.gsort_default
        GroupSort.OLDEST -> R.string.gsort_oldest
        GroupSort.TOTAL_DESC -> R.string.gsort_total_desc
        GroupSort.TOTAL_ASC -> R.string.gsort_total_asc
        GroupSort.REMAINING_DESC -> R.string.gsort_remaining_desc
        GroupSort.REMAINING_ASC -> R.string.gsort_remaining_asc
        GroupSort.NAME_ASC -> R.string.gsort_name_asc
        GroupSort.NAME_DESC -> R.string.gsort_name_desc
        GroupSort.CUSTOM -> R.string.gsort_custom
    }

    private inner class GroupAdapter : RecyclerView.Adapter<GroupAdapter.VH>() {
        var groups: List<MediaGroup> = emptyList()
            set(v) {
                field = v
                notifyDataSetChanged()
            }

        inner class VH(val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = groups.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val g = groups[position]
            val reviewed = g.total - g.remaining
            holder.b.cover.load(g.cover) { size(200) }
            holder.b.title.text = g.label
            holder.b.subtitle.text = if (g.remaining == 0) getString(R.string.group_done)
            else getString(R.string.group_progress, reviewed, g.total)
            holder.b.bar.max = g.total
            holder.b.bar.progress = reviewed
            holder.b.root.setOnClickListener {
                lifecycleScope.launch { SessionLauncher.startGroup(this@GroupPickerActivity, repo, g) }
            }
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        fun intent(context: Context, mode: CleanMode = CleanMode.MONTH) =
            Intent(context, GroupPickerActivity::class.java).putExtra(EXTRA_MODE, mode.name)
    }
}
