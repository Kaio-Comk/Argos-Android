package com.twinfinder.app.ui.duplicates

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.twinfinder.app.R
import com.twinfinder.app.data.DuplicateScanner
import com.twinfinder.app.data.Media
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.ui.common.ThumbAdapter
import com.twinfinder.app.ui.common.ThumbMark
import com.twinfinder.app.ui.review.ReviewActivity
import com.twinfinder.app.util.Fmt
import com.twinfinder.app.databinding.FragmentDuplicatesBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Busca de cópias idênticas. O usuário escolhe qual fica em cada grupo; as outras vão para a revisão. */
class DuplicatesFragment : Fragment() {

    private var _b: FragmentDuplicatesBinding? = null
    private val b get() = _b!!
    private lateinit var repo: SessionRepository
    private var scanJob: Job? = null
    private var groups: List<List<Media>> = emptyList()
    private val keepers = mutableMapOf<Int, Long>()
    private val adapter = GroupAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDuplicatesBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = SessionRepository(requireContext())
        b.list.layoutManager = LinearLayoutManager(requireContext())
        b.list.adapter = adapter
        b.scanButton.setOnClickListener { scan() }
        b.reviewButton.setOnClickListener { review() }
    }

    private fun scan() {
        scanJob?.cancel()
        b.scanButton.isEnabled = false
        b.reviewButton.isVisible = false
        b.progress.isVisible = true
        b.progress.isIndeterminate = true
        b.status.setText(R.string.dup_scanning)
        groups = emptyList()
        adapter.notifyDataSetChanged()
        scanJob = viewLifecycleOwner.lifecycleScope.launch {
            val library = repo.library()
            val found = DuplicateScanner(requireContext()).scan(library) { done, total ->
                b.root.post {
                    _b ?: return@post
                    b.progress.isIndeterminate = false
                    b.progress.max = total.coerceAtLeast(1)
                    b.progress.progress = done
                    b.status.text = getString(R.string.dup_hashing, done, total)
                }
            }
            groups = found
            keepers.clear()
            found.forEachIndexed { i, g -> keepers[i] = g.first().id }
            adapter.notifyDataSetChanged()
            b.progress.isVisible = false
            b.scanButton.isEnabled = true
            if (found.isEmpty()) {
                b.status.setText(R.string.dup_none)
            } else {
                val copies = found.sumOf { it.size - 1 }
                val bytes = found.sumOf { (it.size - 1) * it.first().size }
                b.status.text = getString(R.string.dup_result, found.size, copies, Fmt.size(requireContext(), bytes)) +
                    "\n" + getString(R.string.dup_hint)
                b.reviewButton.isVisible = true
            }
        }
    }

    private fun review() {
        viewLifecycleOwner.lifecycleScope.launch {
            val id = repo.createDuplicateSession(groups, keepers.values.toSet())
            startActivity(ReviewActivity.intent(requireContext(), id))
        }
    }

    private inner class GroupAdapter : RecyclerView.Adapter<GroupAdapter.VH>() {
        inner class VH(val title: TextView, val thumbs: RecyclerView, val thumbAdapter: ThumbAdapter) :
            RecyclerView.ViewHolder((title.parent as View))

        override fun getItemCount() = groups.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), 0)
                layoutParams = RecyclerView.LayoutParams(-1, -2)
            }
            val title = TextView(ctx).apply { setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge) }
            val thumbs = RecyclerView(ctx).apply {
                layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            }
            col.addView(title)
            col.addView(thumbs, LinearLayout.LayoutParams(-1, (104 * density).toInt()))
            lateinit var holder: VH
            val thumbAdapter = ThumbAdapter(fixedSizePx = (100 * density).toInt()) { pos ->
                val g = holder.bindingAdapterPosition.takeIf { it >= 0 } ?: return@ThumbAdapter
                keepers[g] = groups[g][pos].id
                notifyItemChanged(g)
            }
            thumbs.adapter = thumbAdapter
            holder = VH(title, thumbs, thumbAdapter)
            return holder
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val g = groups[position]
            holder.title.text = getString(R.string.dup_group_title, g.size, Fmt.size(holder.title.context, g.first().size))
            val keeper = keepers[position]
            holder.thumbAdapter.items = g.map {
                ThumbAdapter.Item(
                    it, if (it.id == keeper) ThumbMark.KEEP else ThumbMark.DELETE,
                    current = it.id == keeper,
                    label = if (it.id == keeper) getString(R.string.dup_keep_badge) else it.bucketName,
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
        _b = null
    }
}
