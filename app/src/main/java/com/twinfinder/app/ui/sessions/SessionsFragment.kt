package com.twinfinder.app.ui.sessions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.twinfinder.app.R
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.db.SessionCounts
import com.twinfinder.app.data.db.SessionEntity
import com.twinfinder.app.data.db.SessionStatus
import com.twinfinder.app.databinding.FragmentSessionsBinding
import com.twinfinder.app.databinding.ItemGroupBinding
import com.twinfinder.app.ui.clean.SessionLauncher
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.launch

/** Histórico: limpezas em andamento (continuar ou descartar) e concluídas. */
class SessionsFragment : Fragment() {

    private var _b: FragmentSessionsBinding? = null
    private val b get() = _b!!
    private lateinit var repo: SessionRepository
    private val adapter = SessionAdapter()
    private var showCompleted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentSessionsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = SessionRepository(requireContext())
        b.tabs.addTab(b.tabs.newTab().setText(R.string.sessions_pending))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.sessions_completed))
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showCompleted = tab.position == 1
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) refresh()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val status = if (showCompleted) SessionStatus.COMPLETED else SessionStatus.IN_PROGRESS
            adapter.rows = repo.sessions(status).map { it to repo.counts(it.id) }
            b.empty.isVisible = adapter.rows.isEmpty()
            b.empty.setText(if (showCompleted) R.string.sessions_empty_completed else R.string.sessions_empty_pending)
        }
    }

    private fun discard(session: SessionEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.session_discard_title)
            .setMessage(R.string.session_discard_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.session_discard) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repo.discard(session.id)
                    refresh()
                }
            }.show()
    }

    private inner class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {
        var rows: List<Pair<SessionEntity, SessionCounts>> = emptyList()
            set(v) {
                field = v
                notifyDataSetChanged()
            }

        inner class VH(val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (s, c) = rows[position]
            val ctx = holder.b.root.context
            holder.b.cover.isVisible = false
            holder.b.title.text = s.label
            val reviewed = c.total - c.pending
            if (s.status == SessionStatus.COMPLETED) {
                holder.b.subtitle.text = ctx.getString(R.string.session_line, c.keep, c.delete, c.move) +
                    "\n" + Fmt.date(s.updatedAt) + " · " + Fmt.size(ctx, s.freedBytes)
                holder.b.bar.isVisible = false
                holder.b.trailing.isVisible = false
                holder.b.root.setOnClickListener(null)
            } else {
                holder.b.subtitle.text = ctx.getString(R.string.session_line_pending, reviewed, c.total) + " · " + Fmt.date(s.updatedAt)
                holder.b.bar.isVisible = true
                holder.b.bar.max = c.total.coerceAtLeast(1)
                holder.b.bar.progress = reviewed
                holder.b.trailing.isVisible = true
                holder.b.trailing.setImageResource(R.drawable.ic_more)
                holder.b.trailing.contentDescription = ctx.getString(R.string.session_discard)
                holder.b.root.setOnClickListener { SessionLauncher.open(requireActivity() as AppCompatActivity, s.id) }
                holder.b.trailing.setOnClickListener { v ->
                    PopupMenu(ctx, v).apply {
                        menu.add(R.string.session_resume).setOnMenuItemClickListener {
                            SessionLauncher.open(requireActivity() as AppCompatActivity, s.id); true
                        }
                        menu.add(R.string.session_discard).setOnMenuItemClickListener { discard(s); true }
                    }.show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
