package com.twinfinder.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.twinfinder.app.R

/** Links que saem do app (Play Store e sugestões). O app em si não usa a internet. */
object Links {
    fun rate(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
        } catch (_: ActivityNotFoundException) {
            open(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    fun feedback(context: Context) = open(context, context.getString(R.string.feedback_url))

    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }
}
