package com.twinfinder.app.util

import android.content.Context
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

object Fmt {
    fun size(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

    fun date(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

    /** Duração de vídeo: 1:05 ou 1:02:03. */
    fun duration(ms: Long): String {
        val total = TimeUnit.MILLISECONDS.toSeconds(ms)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Tempo gasto numa sessão: "12 min 5 s". */
    fun elapsed(ms: Long): String {
        val total = TimeUnit.MILLISECONDS.toSeconds(ms)
        val m = total / 60
        val s = total % 60
        return if (m > 0) "$m min $s s" else "$s s"
    }
}
