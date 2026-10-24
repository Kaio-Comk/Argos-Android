package com.twinfinder.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.twinfinder.app.R
import com.twinfinder.app.data.Album
import com.twinfinder.app.data.MediaRepository
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.data.db.ExcludedAlbumEntity
import com.twinfinder.app.databinding.ActivityListBinding
import com.twinfinder.app.databinding.ItemGroupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Escolhe quais álbuns entram nas limpezas. */
class AlbumsActivity : AppCompatActivity() {

    private lateinit var b: ActivityListBinding
    private val db by lazy { AppDatabase.get(this) }
    private val repo by lazy { SessionRepository(this) }
    private var albums: List<Album> = emptyList()
    private val excluded = mutableSetOf<Long>()
    private val adapter = AlbumAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.toolbar.setTitle(R.string.albums_title)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.hint.isVisible = true
        b.hint.setText(R.string.albums_order_hint)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        lifecycleScope.launch {
            excluded += db.albums().excluded().map { it.bucketId }
            // Carrega tudo, inclusive os álbuns já excluídos, para poder incluí-los de novo.
            val all = withContext(Dispatchers.IO) { MediaRepository(this@AlbumsActivity).let { it.albums(it.loadAll()) } }
            // Ordem personalizada primeiro; álbuns sem posição vão para o fim, maiores antes.
            val order = repo.albumOrder()
            albums = all.sortedWith(compareBy<Album> { order[it.bucketId] ?: Int.MAX_VALUE }.thenByDescending { it.count })
            b.progress.isVisible = false
            adapter.notifyDataSetChanged()
        }
    }

    private fun toggle(album: Album) {
        lifecycleScope.launch {
            if (album.bucketId in excluded) {
                excluded -= album.bucketId
                db.albums().include(album.bucketId)
            } else {
                excluded += album.bucketId
                db.albums().exclude(ExcludedAlbumEntity(album.bucketId, album.name))
            }
            adapter.notifyItemChanged(albums.indexOf(album))
        }
    }

    /** Troca dois álbuns de lugar e grava a ordem inteira. */
    private fun swap(from: Int, to: Int) {
        if (to !in albums.indices) return
        val list = albums.toMutableList()
        list[from] = list[to].also { list[to] = list[from] }
        albums = list
        adapter.notifyItemMoved(from, to)
        adapter.notifyItemRangeChanged(minOf(from, to), 2)
        lifecycleScope.launch { repo.saveAlbumOrder(albums.map { it.bucketId }) }
    }

    private inner class AlbumAdapter : RecyclerView.Adapter<AlbumAdapter.VH>() {
        inner class VH(val b: ItemGroupBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = albums.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val a = albums[position]
            val included = a.bucketId !in excluded
            holder.b.cover.load(a.cover) { size(200) }
            holder.b.cover.alpha = if (included) 1f else 0.35f
            holder.b.title.text = a.name
            holder.b.subtitle.text = getString(R.string.items_count, a.count)
            holder.b.bar.isVisible = false
            holder.b.title.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (included) R.drawable.ic_check else R.drawable.ic_close, 0)
            holder.b.trailing.isVisible = true
            holder.b.trailing.setImageResource(R.drawable.ic_more)
            holder.b.root.setOnClickListener { toggle(a) }
            holder.b.trailing.setOnClickListener { v ->
                val pos = holder.bindingAdapterPosition
                PopupMenu(this@AlbumsActivity, v).apply {
                    menu.add(R.string.move_up).setOnMenuItemClickListener { swap(pos, pos - 1); true }
                    menu.add(R.string.move_down).setOnMenuItemClickListener { swap(pos, pos + 1); true }
                }.show()
            }
        }
    }
}
