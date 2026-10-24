package com.twinfinder.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.twinfinder.app.MainActivity
import com.twinfinder.app.R
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.util.Permissions
import java.util.concurrent.TimeUnit

/** Lembretes 100% locais (WorkManager + notificação). Nenhum servidor de push. */
object Reminders {
    const val CHANNEL = "reminders"
    private const val WEEKLY = "reminder_weekly"
    private const val MONTHLY = "reminder_monthly"
    const val KIND_PENDING = "pending"
    const val KIND_MONTHLY = "monthly"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Liga ou desliga os lembretes conforme os ajustes. */
    fun sync(context: Context) {
        val prefs = Prefs(context)
        val wm = WorkManager.getInstance(context)
        if (prefs.weeklyReminder) {
            wm.enqueueUniquePeriodicWork(
                WEEKLY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(7, TimeUnit.DAYS)
                    .setInputData(workDataOf("kind" to KIND_PENDING)).build()
            )
        } else wm.cancelUniqueWork(WEEKLY)
        if (prefs.monthlyReminder) {
            wm.enqueueUniquePeriodicWork(
                MONTHLY, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.DAYS)
                    .setInitialDelay(30, TimeUnit.DAYS)
                    .setInputData(workDataOf("kind" to KIND_MONTHLY)).build()
            )
        } else wm.cancelUniqueWork(MONTHLY)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Permissions.canNotify(ctx)) return Result.success()
        val kind = inputData.getString("kind")
        val (title, text) = when (kind) {
            Reminders.KIND_PENDING -> {
                if (SessionRepository(ctx).pendingCount() == 0) return Result.success()
                ctx.getString(R.string.notif_pending_title) to ctx.getString(R.string.notif_pending_text)
            }
            else -> ctx.getString(R.string.notif_monthly_title) to ctx.getString(R.string.notif_monthly_text)
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_copy)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(if (kind == Reminders.KIND_PENDING) 1 else 2, notification)
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem e o envio.
        }
        return Result.success()
    }
}
