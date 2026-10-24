package com.twinfinder.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.data.db.MoveTargetEntity
import com.twinfinder.app.databinding.ActivityListBinding
import com.twinfinder.app.databinding.ItemGroupBinding
import kotlinx.coroutines.launch

/** Destinos que aparecem primeiro na hora de mover: criar, reordenar e remover. */
class MoveTargetsActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private val db by lazy { AppDatabase.get(this) }
    private var targets: List<MoveTargetEntity> = emptyList()
    private val adapter = TargetAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setTitle(R.string.targets_title)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.hint.isVisible = true
        b.hint.setText(R.string.targets_hint)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.actions.isVisible = true
        b.primaryButton.setText(R.string.move_new_album)
        b.primaryButton.setOnClickListener { create() }
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            targets = db.albums().allMoveTargets()
            b.progress.isVisible = false
            b.empty.isVisible = targets.isEmpty()
            b.empty.setText(R.string.targets_empty)
            adapter.notifyDataSetChanged()
        }
    }

    private fun create() {
        val input = EditText(this).apply { hint = getString(R.string.move_new_album_hint); maxLines = 1 }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.move_new_album)
            .setView(LinearLayout(this).apply { setPadding(48, 16, 48, 0); addView(input, LinearLayout.LayoutParams(-1, -2)) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val name = input.text.toString().trim().take(32).replace(Regex("[\\\\/:*?\"<>|]"), "")
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    db.albums().addMoveTarget(MoveTargetEntity(name = name, relativePath = "Pictures/$name/", createdInApp = true, orderIndex = targets.size))
                    reload()
                }
            }.show()
    }

    /** Troca a posição de dois destinos e regrava a ordem de todos. */
    private fun swap(from: Int, to: Int) {
        if (to !in targets.indices) return
        val list = targets.toMutableList()
        val tmp = list[from]
        list[from] = list[to]
        list[to] = tmp
        lifecycleScope.launch {
            db.albums().updateMoveTargets(list.mapIndexed { i, t -> t.copy(orderIndex = i) })
            reload()
        }
    }

    private inner class TargetAdapter : RecyclerView.Adapter<TargetAdapter.VH>() {
        inner class VH(val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = targets.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val t = targets[position]
            holder.b.cover.isVisible = false
            holder.b.bar.isVisible = false
            holder.b.title.text = t.name
            holder.b.subtitle.text = t.relativePath
            holder.b.trailing.isVisible = true
            holder.b.trailing.setImageResource(R.drawable.ic_more)
            holder.b.trailing.setOnClickListener { v ->
                PopupMenu(this@MoveTargetsActivity, v).apply {
                    menu.add("↑").setOnMenuItemClickListener { swap(position, position - 1); true }
                    menu.add("↓").setOnMenuItemClickListener { swap(position, position + 1); true }
                    menu.add(R.string.remove).setOnMenuItemClickListener {
                        lifecycleScope.launch { db.albums().removeMoveTarget(t.id); reload() }
                        true
                    }
                }.show()
            }
        }
    }
}
