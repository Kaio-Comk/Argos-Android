package com.twinfinder.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.MainActivity
import com.twinfinder.app.R
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.databinding.ActivitySuccessBinding
import com.twinfinder.app.reminders.Reminders
import com.twinfinder.app.util.Fmt
import com.twinfinder.app.util.Links
import kotlinx.coroutines.launch

class SuccessActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.weeklyReminder = granted
        Reminders.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivitySuccessBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        val repo = SessionRepository(this)
        val sessionId = intent.getLongExtra(EXTRA_SESSION, 0L)

        b.backButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }

        // Ícone entra com um "pulo".
        b.checkIcon.scaleX = 0f
        b.checkIcon.scaleY = 0f
        b.checkIcon.animate().scaleX(1f).scaleY(1f).setDuration(450)
            .setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()

        b.rateCard.isVisible = !prefs.ratingAsked
        b.rateButton.setOnClickListener {
            prefs.ratingAsked = true
            b.rateCard.isVisible = false
            Links.rate(this)
        }
        b.notExpected.setOnClickListener {
            prefs.ratingAsked = true
            b.rateCard.isVisible = false
            Links.feedback(this)
        }

        lifecycleScope.launch {
            val session = repo.session(sessionId) ?: return@launch
            val c = repo.counts(sessionId)
            b.message.text = getString(R.string.success_message, c.keep + c.delete + c.move, Fmt.size(this@SuccessActivity, session.freedBytes))
            b.time.text = getString(R.string.success_time, Fmt.elapsed(session.timeSpentMs))
        }
        if (!prefs.reminderAsked && !prefs.weeklyReminder) suggestReminder()
    }

    private fun suggestReminder() {
        prefs.reminderAsked = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reminder_suggest_title)
            .setMessage(R.string.reminder_suggest_message)
            .setNegativeButton(R.string.no_thanks, null)
            .setPositiveButton(R.string.yes) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    prefs.weeklyReminder = true
                    Reminders.sync(this)
                }
            }.show()
    }

    companion object {
        private const val EXTRA_SESSION = "session"
        fun intent(context: Context, sessionId: Long) = Intent(context, SuccessActivity::class.java).putExtra(EXTRA_SESSION, sessionId)
    }
}
