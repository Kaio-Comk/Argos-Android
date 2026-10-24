package com.twinfinder.app.ui.clean

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.MediaGroup
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.db.CleanMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Abre a limpeza de um grupo: retoma a sessão aberta, ou cria uma nova. */
object SessionLauncher {

    suspend fun startGroup(activity: AppCompatActivity, repo: SessionRepository, group: MediaGroup) {
        repo.openSession(group.mode, group.key)?.let { return open(activity, it.id) }
        val again = group.remaining == 0
        if (again && !askReviewAgain(activity, group.label)) return
        open(activity, repo.createSession(group.mode, group.key, group.label, group.items, reviewAgain = again))
    }

    suspend fun startRandom(activity: AppCompatActivity, repo: SessionRepository) {
        repo.openSession(CleanMode.RANDOM, "")?.let { return open(activity, it.id) }
        val count = Prefs(activity).randomCount
        val library = repo.library()
        val label = activity.getString(R.string.mode_random_label, count)
        val settled = repo.settledIds()
        // Se tudo já foi revisado, a rodada rápida sorteia da galeria inteira.
        val reviewAgain = library.all { it.id in settled }
        open(activity, repo.createSession(CleanMode.RANDOM, "", label, library, reviewAgain))
    }

    fun open(activity: AppCompatActivity, sessionId: Long) {
        activity.startActivity(SwipeActivity.intent(activity, sessionId))
    }

    private suspend fun askReviewAgain(activity: AppCompatActivity, label: String): Boolean = suspendCancellableCoroutine { cont ->
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.review_again_title)
            .setMessage(activity.getString(R.string.review_again_message, label))
            .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(false) }
            .setPositiveButton(R.string.review_again) { _, _ -> cont.resume(true) }
            .setOnCancelListener { if (cont.isActive) cont.resume(false) }
            .show()
    }
}
