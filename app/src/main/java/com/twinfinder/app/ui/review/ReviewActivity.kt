package com.twinfinder.app.ui.review

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.Media
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionItem
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.db.ActionResult
import com.twinfinder.app.data.db.Decision
import com.twinfinder.app.databinding.ActivityListBinding
import com.twinfinder.app.databinding.ItemThumbBinding
import com.twinfinder.app.ui.SuccessActivity
import com.twinfinder.app.ui.clean.FullScreenActivity
import com.twinfinder.app.ui.common.MediaActions
import com.twinfinder.app.ui.common.hapticConfirm
import com.twinfinder.app.ui.common.hapticTick
import com.twinfinder.app.ui.common.loadThumb
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Última etapa: mostra o que vai ser apagado ou movido e aplica depois da confirmação do sistema. */
class ReviewActivity : AppCompatActivity() {

    private val actions = MediaActions(this)
    private lateinit var b: ActivityListBinding
    private lateinit var repo: SessionRepository
    private lateinit var prefs: Prefs
    private var sessionId = 0L
    private var syncingSwitch = false

    /** Itens marcados nesta tela; o valor diz se continuam ativos (true) ou foram desmarcados. */
    private val toDelete = linkedMapOf<SessionItem, Boolean>()
    private val toMove = linkedMapOf<SessionItem, Boolean>()
    private val adapter = ReviewAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityListBinding.inflate(layoutInflater)
        setContentView(b.root)
        repo = SessionRepository(this)
        prefs = Prefs(this)
        sessionId = intent.getLongExtra(EXTRA_SESSION, 0L)

        b.toolbar.setTitle(R.string.review_title)
        b.toolbar.setNavigationOnClickListener { finish() }
        val grid = GridLayoutManager(this, 3)
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (adapter.rows[position] is Row.Header) 3 else 1
        }
        b.list.layoutManager = grid
        b.list.adapter = adapter
        b.primaryButton.setOnClickListener { it.hapticConfirm(); execute() }
        // "Decidir depois": a sessão fica em andamento, com as marcações guardadas.
        b.secondaryButton.setText(R.string.decide_later)
        b.secondaryButton.setOnClickListener { finish() }
        b.toolbar.menu.add(R.string.review_mark_all_delete).setOnMenuItemClickListener { setAll(true); true }
        b.toolbar.menu.add(R.string.review_mark_all_keep).setOnMenuItemClickListener { setAll(false); true }
        b.actionsSwitch.setText(R.string.settings_use_trash)
        b.actionsSwitch.setOnCheckedChangeListener { _, on ->
            // Ajuste feito pelo próprio render() não é escolha do usuário.
            if (syncingSwitch) return@setOnCheckedChangeListener
            prefs.useTrash = on
            prefs.deleteChoiceAsked = true
            render()
        }
        lifecycleScope.launch { load() }
    }

    /** Marca (ou desmarca) todos os itens desta revisão de uma vez. */
    private fun setAll(on: Boolean) {
        val changes = (toDelete.keys.map { it to false } + toMove.keys.map { it to true })
        toDelete.keys.forEach { toDelete[it] = on }
        toMove.keys.forEach { toMove[it] = on }
        lifecycleScope.launch {
            changes.forEach { (item, isMove) ->
                val decision = when {
                    !on -> Decision.KEEP
                    isMove -> Decision.MOVE
                    else -> Decision.DELETE
                }
                repo.decide(sessionId, item.media.id, decision, item.review.moveToPath.takeIf { on && isMove })
            }
        }
        render()
    }

    private suspend fun load() {
        b.progress.isVisible = true
        val items = repo.items(sessionId)
        toDelete.clear()
        toMove.clear()
        items.filter { it.review.decision == Decision.DELETE && it.review.result != ActionResult.DONE }.forEach { toDelete[it] = true }
        items.filter { it.review.decision == Decision.MOVE && it.review.result != ActionResult.DONE }.forEach { toMove[it] = true }
        b.progress.isVisible = false
        render()
    }

    private fun render() {
        val rows = mutableListOf<Row>()
        val deleting = toDelete.filterValues { it }.keys
        val moving = toMove.filterValues { it }.keys
        if (toDelete.isNotEmpty()) {
            val delHeader = resources.getQuantityString(R.plurals.review_to_delete, deleting.size, deleting.size, Fmt.size(this, deleting.sumOf { it.media.size }))
            rows += Row.Header(delHeader)
            toDelete.forEach { (item, on) -> rows += Row.Thumb(item, on, isMove = false) }
        }
        if (toMove.isNotEmpty()) {
            val moveHeader = resources.getQuantityString(R.plurals.review_to_move, moving.size, moving.size)
            rows += Row.Header(moveHeader)
            // Um bloco por destino, como no Sponge: "Para Pictures/Viagem".
            toMove.entries.groupBy { it.key.review.moveToPath.orEmpty() }.forEach { (path, entries) ->
                rows += Row.Header(getString(R.string.review_move_to, path.trimEnd('/')), small = true)
                entries.forEach { (item, on) -> rows += Row.Thumb(item, on, isMove = true) }
            }
        }
        adapter.rows = rows
        b.empty.isVisible = rows.isEmpty()
        b.empty.setText(R.string.review_nothing)
        b.hint.isVisible = rows.isNotEmpty()
        b.hint.setText(R.string.review_tap_hint)

        b.actions.isVisible = true
        b.secondaryButton.isVisible = rows.isNotEmpty()
        b.actionsSwitch.isVisible = deleting.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (b.actionsSwitch.isChecked != prefs.useTrash) {
            syncingSwitch = true
            b.actionsSwitch.isChecked = prefs.useTrash
            syncingSwitch = false
        }
        val trash = prefs.useTrash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        b.primaryButton.text = when {
            deleting.isNotEmpty() && moving.isNotEmpty() -> getString(R.string.confirm)
            deleting.isNotEmpty() && trash -> resources.getQuantityString(R.plurals.review_confirm_trash, deleting.size, deleting.size)
            deleting.isNotEmpty() -> resources.getQuantityString(R.plurals.review_confirm_delete, deleting.size, deleting.size)
            moving.isNotEmpty() -> resources.getQuantityString(R.plurals.review_confirm_move, moving.size, moving.size)
            else -> getString(R.string.review_finish)
        }
        val notes = mutableListOf<String>()
        if (deleting.isNotEmpty()) notes += getString(R.string.cloud_warning)
        val parts = actions.parts(deleting.size)
        if (parts > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) notes += getString(R.string.review_multi_part, MediaActions.CHUNK, parts)
        b.actionsNote.isVisible = notes.isNotEmpty()
        b.actionsNote.text = notes.joinToString("\n\n")
    }

    private fun toggle(row: Row.Thumb) {
        val map = if (row.isMove) toMove else toDelete
        val on = !(map[row.item] ?: true)
        map[row.item] = on
        // Desmarcar vira "manter"; marcar de novo volta à decisão original.
        val decision = when {
            on && row.isMove -> Decision.MOVE
            on -> Decision.DELETE
            else -> Decision.KEEP
        }
        lifecycleScope.launch { repo.decide(sessionId, row.item.media.id, decision, row.item.review.moveToPath.takeIf { on }) }
        render()
    }

    private fun execute() {
        val deleting = toDelete.filterValues { it }.keys.map { it.media }
        val moving = toMove.filterValues { it }.keys.mapNotNull { item -> item.review.moveToPath?.let { item.media to it } }
        lifecycleScope.launch {
            if (deleting.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !confirmLegacy(deleting.size)) return@launch
            // Primeira exclusão: o usuário escolhe uma vez entre lixeira e apagar de vez.
            if (deleting.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !prefs.deleteChoiceAsked) {
                val trash = askDeleteChoice() ?: return@launch
                prefs.useTrash = trash
                prefs.deleteChoiceAsked = true
                render()
            }
            setBusy(true)
            try {
                if (moving.isNotEmpty()) {
                    val moved = actions.move(moving).toSet()
                    repo.markResult(sessionId, moved.toList(), ActionResult.DONE)
                    val failed = moving.map { it.first.id }.filterNot { it in moved }
                    if (failed.isNotEmpty()) {
                        repo.markResult(sessionId, failed, ActionResult.FAILED)
                        Toast.makeText(this@ReviewActivity, getString(R.string.review_move_failed, failed.size), Toast.LENGTH_LONG).show()
                    }
                }
                var freed = 0L
                if (deleting.isNotEmpty()) {
                    val useTrash = prefs.useTrash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    val done = (if (useTrash) actions.trash(deleting) else actions.delete(deleting)).toSet()
                    repo.markResult(sessionId, done.toList(), ActionResult.DONE)
                    if (useTrash) repo.rememberTrashed(deleting.filter { it.id in done })
                    freed = deleting.filter { it.id in done }.sumOf { it.size }
                    if (done.size < deleting.size) {
                        // O usuário recusou algum pedido do sistema: a sessão continua aberta.
                        Toast.makeText(this@ReviewActivity, R.string.review_cancelled, Toast.LENGTH_LONG).show()
                        load()
                        return@launch
                    }
                }
                repo.complete(sessionId, freed)
                startActivity(SuccessActivity.intent(this@ReviewActivity, sessionId))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@ReviewActivity, R.string.error_generic, Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    /** true = lixeira, false = apagar de vez, null = cancelou. */
    private suspend fun askDeleteChoice(): Boolean? = suspendCancellableCoroutine { cont ->
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_choice_title)
            .setMessage(R.string.delete_choice_message)
            .setNeutralButton(R.string.cancel) { _, _ -> cont.resume(null) }
            .setNegativeButton(R.string.delete_choice_forever) { _, _ -> cont.resume(false) }
            .setPositiveButton(R.string.delete_choice_trash) { _, _ -> cont.resume(true) }
            .setOnCancelListener { if (cont.isActive) cont.resume(null) }
            .show()
    }

    private suspend fun confirmLegacy(count: Int): Boolean = suspendCancellableCoroutine { cont ->
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.review_legacy_confirm, count))
            .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(false) }
            .setPositiveButton(R.string.confirm) { _, _ -> cont.resume(true) }
            .setOnCancelListener { if (cont.isActive) cont.resume(false) }
            .show()
    }

    private fun setBusy(busy: Boolean) {
        b.progress.isVisible = busy
        b.primaryButton.isEnabled = !busy
        if (busy) b.primaryButton.setText(R.string.review_working)
    }

    private sealed class Row {
        data class Header(val text: String, val small: Boolean = false) : Row()
        data class Thumb(val item: SessionItem, val on: Boolean, val isMove: Boolean) : Row()
    }

    private inner class ReviewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()
            set(v) {
                field = v
                notifyDataSetChanged()
            }

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 0) {
                val tv = TextView(parent.context).apply {
                    setTextAppearance(R.style.TextAppearance_TwinFinder_Section)
                    setPadding(16, 32, 16, 12)
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            return object : RecyclerView.ViewHolder(ItemThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder.itemView as TextView).apply {
                    text = row.text
                    setTextAppearance(if (row.small) com.google.android.material.R.style.TextAppearance_Material3_LabelMedium else R.style.TextAppearance_TwinFinder_Section)
                    setPadding(16, if (row.small) 12 else 32, 16, 12)
                }
                is Row.Thumb -> {
                    val tb = ItemThumbBinding.bind(holder.itemView)
                    bindThumb(tb, row.item.media, row.on, row.isMove, row.item.review.moveToPath)
                    holder.itemView.setOnClickListener {
                        holder.itemView.hapticTick()
                        toggle(row)
                    }
                    holder.itemView.setOnLongClickListener {
                        holder.itemView.hapticConfirm()
                        startActivity(FullScreenActivity.intent(this@ReviewActivity, row.item.media))
                        true
                    }
                }
            }
        }

        private fun bindThumb(tb: ItemThumbBinding, media: Media, on: Boolean, isMove: Boolean, path: String?) {
            tb.image.loadThumb(media)
            tb.play.isVisible = media.isVideo
            tb.dim.isVisible = !on
            tb.badge.isVisible = on
            tb.badge.setImageResource(if (isMove) R.drawable.ic_folder else R.drawable.ic_delete)
            tb.badge.setBackgroundColor(getColor(if (isMove) R.color.move else R.color.delete))
            tb.badge.setColorFilter(getColor(android.R.color.white))
            tb.label.isVisible = true
            tb.label.text = if (isMove) path?.trimEnd('/')?.substringAfterLast('/') else Fmt.size(this@ReviewActivity, media.size)
        }
    }

    companion object {
        private const val EXTRA_SESSION = "session"
        fun intent(context: Context, sessionId: Long) = Intent(context, ReviewActivity::class.java).putExtra(EXTRA_SESSION, sessionId)
    }
}
