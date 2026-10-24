package com.twinfinder.app.ui.clean

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.Media
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionItem
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.SortOption
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.data.db.Decision
import com.twinfinder.app.databinding.ActivitySwipeBinding
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.twinfinder.app.ui.common.ThumbAdapter
import com.twinfinder.app.ui.common.ThumbMark
import com.twinfinder.app.ui.common.bounce
import com.twinfinder.app.ui.common.hapticConfirm
import com.twinfinder.app.ui.common.hapticTick
import com.twinfinder.app.ui.common.loadThumb
import com.twinfinder.app.ui.review.ReviewActivity
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Limpeza por cartões: direita mantém, esquerda apaga. Nada é apagado aqui; só na revisão final. */
class SwipeActivity : AppCompatActivity() {

    private lateinit var b: ActivitySwipeBinding
    private lateinit var repo: SessionRepository
    private lateinit var prefs: Prefs
    private lateinit var strip: ThumbAdapter

    private var sessionId = 0L
    private val items = mutableListOf<SessionItem>()
    private var index = 0
    private val history = ArrayDeque<Int>()
    private var resumedAt = 0L
    private var animating = false
    private var leaving = false
    private var lastWrite: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySwipeBinding.inflate(layoutInflater)
        setContentView(b.root)
        repo = SessionRepository(this)
        prefs = Prefs(this)
        sessionId = intent.getLongExtra(EXTRA_SESSION, 0L)

        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.inflateMenu(R.menu.swipe)
        b.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_proceed -> openReview()
                R.id.action_customize -> CustomizeViewSheet.show(this) { applyViewPrefs(); bind() }
                R.id.action_sort -> sortSession()
                R.id.action_keep_rest -> bulk(Decision.KEEP)
                R.id.action_delete_rest -> bulk(Decision.DELETE)
                R.id.action_discard -> discardChoices()
            }
            true
        }

        val thumbPx = (56 * resources.displayMetrics.density).toInt()
        strip = ThumbAdapter(fixedSizePx = thumbPx) { jumpTo(it) }
        b.strip.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.strip.adapter = strip
        applyViewPrefs()
        b.fullscreenButton.setOnClickListener { current()?.let { startActivity(FullScreenActivity.intent(this, it.media)) } }

        b.keepButton.setOnClickListener { it.bounce(prefs.animateButtons); it.hapticConfirm(); decide(Decision.KEEP, direction = 1) }
        b.deleteButton.setOnClickListener { it.bounce(prefs.animateButtons); it.hapticConfirm(); decide(Decision.DELETE, direction = -1) }
        b.skipButton.setOnClickListener { it.bounce(prefs.animateButtons); it.hapticConfirm(); decide(Decision.SKIP, direction = 0) }
        b.undoButton.setOnClickListener { it.bounce(prefs.animateButtons); it.hapticConfirm(); undo() }
        b.moveButton.setOnClickListener { it.hapticConfirm(); move() }
        b.shareButton.setOnClickListener { current()?.let { share(it.media) } }
        b.infoButton.setOnClickListener { current()?.let { FileInfoSheet.show(this, it.media) } }
        b.proceedButton.setOnClickListener { openReview() }
        setupDrag()

        lifecycleScope.launch { load() }
    }

    private suspend fun load() {
        val session = repo.session(sessionId) ?: return finish()
        b.toolbar.title = session.label
        items.clear()
        items += repo.items(sessionId)
        index = items.indexOfFirst { it.review.decision == Decision.PENDING }.let { if (it < 0) items.size else it }
        if (index >= items.size) {
            openReview()
            return
        }
        bind()
        loadOrganizeBar()
        if (!prefs.swipeTutorialSeen) showTutorial(0)
    }

    /** Aplica os ajustes de "Personalizar tela" (faixa, botão Concluir, modo organizado). */
    private fun applyViewPrefs() {
        b.strip.isVisible = prefs.showStrip
        b.toolbar.menu.findItem(R.id.action_proceed).isVisible = prefs.proceedOnTop
        b.proceedButton.isVisible = !prefs.proceedOnTop
        loadOrganizeBar()
    }

    /** Barra do modo organizado: um chip por destino, mais "criar álbum". */
    private fun loadOrganizeBar() {
        if (!prefs.organizeMode || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            b.organizeBar.isVisible = false
            return
        }
        lifecycleScope.launch {
            val targets = AppDatabase.get(this@SwipeActivity).albums().moveTargets()
            b.organizeChips.removeAllViews()
            targets.forEach { t ->
                b.organizeChips.addView(Chip(this@SwipeActivity).apply {
                    text = t.name
                    setChipIconResource(R.drawable.ic_folder)
                    setOnClickListener { decide(Decision.MOVE, direction = 0, movePath = t.relativePath) }
                })
            }
            b.organizeChips.addView(Chip(this@SwipeActivity).apply {
                text = "+ " + getString(R.string.move_new_album)
                setOnClickListener { move() }
            })
            bindOrganizeState()
        }
    }

    /** No modo organizado, item da pasta Android/ mostra o aviso no lugar da barra. */
    private fun bindOrganizeState() {
        val blocked = current()?.media?.isInsideAndroidFolder == true
        val organize = prefs.organizeMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        b.organizeBar.isVisible = organize && !blocked
        b.androidRestriction.isVisible = blocked
    }

    private fun showTutorial(step: Int) {
        val steps = listOf(R.string.tutorial_step_delete, R.string.tutorial_step_keep, R.string.tutorial_step_proceed)
        if (step >= steps.size) {
            b.tutorial.isVisible = false
            prefs.swipeTutorialSeen = true
            return
        }
        b.tutorial.isVisible = true
        b.tutorialText.setText(steps[step])
        b.tutorialNext.setText(if (step == steps.lastIndex) R.string.got_it else R.string.next)
        b.tutorialNext.setOnClickListener { showTutorial(step + 1) }
        b.tutorialSkip.isVisible = step < steps.lastIndex
        b.tutorialSkip.setOnClickListener { showTutorial(steps.size) }
    }

    private fun sortSession() {
        val options = listOf(
            SortOption.DATE_DESC to R.string.sort_date_desc, SortOption.DATE_ASC to R.string.sort_date_asc,
            SortOption.SIZE_DESC to R.string.sort_size_desc, SortOption.SIZE_ASC to R.string.sort_size_asc,
            SortOption.NAME_ASC to R.string.sort_name_asc,
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_session)
            .setItems(options.map { getString(it.second) }.toTypedArray()) { _, i ->
                val pending = lastWrite
                lifecycleScope.launch {
                    pending?.join()
                    repo.resortPending(sessionId, options[i].first)
                    history.clear()
                    load()
                }
            }.show()
    }

    private fun discardChoices() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.discard_choices)
            .setMessage(R.string.discard_choices_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val pending = lastWrite
                lifecycleScope.launch {
                    pending?.join()
                    repo.resetDecisions(sessionId)
                    history.clear()
                    load()
                }
            }.show()
    }

    private fun current(): SessionItem? = items.getOrNull(index)

    private fun bind() {
        val cur = current()
        b.card.isVisible = cur != null
        b.doneText.isVisible = cur == null
        b.card.translationX = 0f
        b.card.translationY = 0f
        b.card.rotation = 0f
        b.card.alpha = 1f
        b.stampKeep.alpha = 0f
        b.stampDelete.alpha = 0f
        b.backCard.scaleX = 0.92f
        b.backCard.scaleY = 0.92f
        b.backCard.alpha = 0.7f
        if (cur == null) {
            b.video.stopPlayback()
            b.backCard.isVisible = false
            b.info.isVisible = false
        } else {
            showMedia(cur.media)
            val next = items.getOrNull(index + 1)
            b.backCard.isVisible = next != null
            next?.let { b.backImage.loadThumb(it.media, 600) }
            b.info.isVisible = prefs.showFileInfo
            b.info.text = "${cur.media.name} · ${Fmt.date(cur.media.date)}"
            b.sizeChip.text = Fmt.size(this, cur.media.size)
            b.formatChip.text = cur.media.mimeType.substringAfter('/').uppercase()
        }
        b.counter.text = getString(R.string.swipe_counter, (index + 1).coerceAtMost(items.size), items.size)
        b.sizeChip.isVisible = cur != null
        b.formatChip.isVisible = cur != null
        bindOrganizeState()
        val marked = items.filter { it.review.decision == Decision.DELETE }
        b.marked.text = getString(R.string.swipe_marked, marked.size, Fmt.size(this, marked.sumOf { it.media.size }))
        strip.items = items.mapIndexed { i, it -> ThumbAdapter.Item(it.media, markOf(it.review.decision), current = i == index) }
        if (index < items.size) b.strip.scrollToPosition(index)
    }

    private fun markOf(d: Decision) = when (d) {
        Decision.KEEP -> ThumbMark.KEEP
        Decision.DELETE -> ThumbMark.DELETE
        Decision.MOVE -> ThumbMark.MOVE
        Decision.SKIP -> ThumbMark.OFF
        Decision.PENDING -> ThumbMark.NONE
    }

    private fun showMedia(media: Media) {
        b.video.stopPlayback()
        b.image.load(media.uri)
        b.videoBadge.isVisible = media.isVideo
        if (!media.isVideo) {
            b.video.isVisible = false
            return
        }
        b.videoBadge.text = Fmt.duration(media.durationMs)
        b.video.isVisible = true
        b.video.setVideoURI(media.uri)
        b.video.setOnPreparedListener { mp ->
            mp.isLooping = true
            if (!prefs.videoSound) mp.setVolume(0f, 0f)
            b.video.start()
        }
        b.video.setOnErrorListener { _, _, _ ->
            b.video.isVisible = false
            Toast.makeText(this, R.string.video_cannot_play, Toast.LENGTH_SHORT).show()
            true
        }
    }

    /** Registra a decisão do cartão atual e anima a saída. direction: 1 direita, -1 esquerda, 0 para cima. */
    private fun decide(decision: Decision, direction: Int, movePath: String? = null) {
        val cur = current() ?: return
        if (animating) return
        animating = true
        val w = b.stack.width.toFloat()
        if (prefs.animateButtons) {
            b.backCard.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220L).start()
        }
        b.card.animate()
            .translationX(direction * w * 1.5f)
            .translationY(if (direction == 0) -b.stack.height.toFloat() else 0f)
            .rotation(direction * 25f)
            .alpha(if (direction == 0) 0f else 1f)
            .setDuration(if (prefs.animateButtons) 220L else 0L)
            .withEndAction {
                animating = false
                items[index] = cur.copy(review = cur.review.copy(decision = decision, moveToPath = movePath))
                history.addLast(index)
                write { repo.decide(sessionId, cur.media.id, decision, movePath) }
                index = nextPending(index)
                bind()
                if (index >= items.size) openReview()
            }.start()
    }

    private fun nextPending(from: Int): Int {
        for (i in from + 1 until items.size) if (items[i].review.decision == Decision.PENDING) return i
        // Pode haver pendentes antes (o usuário pulou pela faixa do topo).
        return items.indexOfFirst { it.review.decision == Decision.PENDING }.let { if (it < 0) items.size else it }
    }

    private fun undo() {
        val last = history.removeLastOrNull() ?: return
        val item = items[last]
        val prevDecision = item.review.decision
        items[last] = item.copy(review = item.review.copy(decision = Decision.PENDING, moveToPath = null))
        write { repo.decide(sessionId, item.media.id, Decision.PENDING) }
        index = last
        bind()
        if (prefs.animateButtons) {
            val direction = when (prevDecision) {
                Decision.KEEP -> 1
                Decision.DELETE -> -1
                else -> 0
            }
            val w = b.stack.width.toFloat()
            b.card.translationX = direction * w * 1.2f
            b.card.translationY = if (direction == 0) -b.stack.height * 0.4f else 0f
            b.card.rotation = direction * 20f
            b.card.alpha = 0.4f
            b.card.animate()
                .translationX(0f)
                .translationY(0f)
                .rotation(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun jumpTo(position: Int) {
        if (position !in items.indices || animating) return
        index = position
        bind()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var dx = 0f
        var thresholdHapticFired = false
        b.card.setOnTouchListener { v, e ->
            if (animating) return@setOnTouchListener false
            val threshold = b.stack.width * 0.3f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    dx = 0f
                    thresholdHapticFired = false
                }
                MotionEvent.ACTION_MOVE -> {
                    dx = e.rawX - downX
                    v.translationX = dx
                    v.rotation = dx / b.stack.width * 20f
                    val progress = (abs(dx) / threshold).coerceIn(0f, 1f)
                    b.stampKeep.alpha = (dx / threshold).coerceIn(0f, 1f)
                    b.stampDelete.alpha = (-dx / threshold).coerceIn(0f, 1f)

                    // Efeito 3D: o cartão de trás expande e ganha foco à medida que o de cima se move
                    b.backCard.scaleX = 0.92f + 0.08f * progress
                    b.backCard.scaleY = 0.92f + 0.08f * progress
                    b.backCard.alpha = 0.7f + 0.3f * progress

                    if (abs(dx) > threshold && !thresholdHapticFired) {
                        v.hapticTick()
                        thresholdHapticFired = true
                    } else if (abs(dx) <= threshold && thresholdHapticFired) {
                        thresholdHapticFired = false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    when {
                        dx > threshold -> {
                            v.hapticConfirm()
                            decide(Decision.KEEP, 1)
                        }
                        dx < -threshold -> {
                            v.hapticConfirm()
                            decide(Decision.DELETE, -1)
                        }
                        else -> {
                            if (abs(dx) < slop && e.actionMasked == MotionEvent.ACTION_UP) togglePlayback()
                            v.animate().translationX(0f).rotation(0f)
                                .setInterpolator(OvershootInterpolator(1.2f))
                                .setDuration(200).start()
                            b.stampKeep.animate().alpha(0f).setDuration(150).start()
                            b.stampDelete.animate().alpha(0f).setDuration(150).start()
                            b.backCard.animate().scaleX(0.92f).scaleY(0.92f).alpha(0.7f).setDuration(200).start()
                        }
                    }
                }
            }
            true
        }
    }

    private fun togglePlayback() {
        if (!b.video.isVisible) return
        if (b.video.isPlaying) b.video.pause() else b.video.start()
    }

    private fun move() {
        val cur = current() ?: return
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                Toast.makeText(this, R.string.move_unsupported, Toast.LENGTH_LONG).show()
            cur.media.isInsideAndroidFolder ->
                Toast.makeText(this, R.string.move_android_folder, Toast.LENGTH_LONG).show()
            else -> MoveSheet.show(this) { path ->
                decide(Decision.MOVE, direction = 0, movePath = path)
                loadOrganizeBar()
            }
        }
    }

    private fun share(media: Media) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(media.mimeType)
            .putExtra(Intent.EXTRA_STREAM, media.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (prefs.shareWithText) send.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        startActivity(Intent.createChooser(send, getString(R.string.share)))
    }

    private fun bulk(decision: Decision) {
        val word = getString(if (decision == Decision.KEEP) R.string.bulk_word_keep else R.string.bulk_word_delete)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.bulk_actions)
            .setMessage(getString(R.string.bulk_warning, word))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                write { repo.decideAllPending(sessionId, decision) }
                openReview()
            }.show()
    }

    /**
     * Grava no banco em ordem e sem cancelar ao sair da tela. Cada gravação espera a anterior,
     * e a revisão final só abre depois da última.
     */
    private fun write(block: suspend () -> Unit) {
        val previous = lastWrite
        lastWrite = lifecycleScope.launch(NonCancellable) {
            previous?.join()
            block()
        }
    }

    private fun openReview() {
        if (leaving) return
        leaving = true
        saveProgress()
        val pending = lastWrite
        lifecycleScope.launch {
            pending?.join()
            startActivity(ReviewActivity.intent(this@SwipeActivity, sessionId))
            finish()
        }
    }

    private fun saveProgress() {
        val spent = if (resumedAt > 0) System.currentTimeMillis() - resumedAt else 0L
        resumedAt = System.currentTimeMillis()
        val pos = index
        write { repo.saveProgress(sessionId, pos, spent) }
    }

    override fun onResume() {
        super.onResume()
        resumedAt = System.currentTimeMillis()
        if (b.video.isVisible) b.video.start()
    }

    override fun onPause() {
        super.onPause()
        if (b.video.isVisible) b.video.pause()
        saveProgress()
        resumedAt = 0L
    }

    companion object {
        private const val EXTRA_SESSION = "session"
        fun intent(context: Context, sessionId: Long) = Intent(context, SwipeActivity::class.java).putExtra(EXTRA_SESSION, sessionId)
    }
}
