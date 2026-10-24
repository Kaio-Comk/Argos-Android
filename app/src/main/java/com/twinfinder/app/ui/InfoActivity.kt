package com.twinfinder.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.twinfinder.app.R

/** Página de texto simples (ex.: "Saber mais" sobre backups na nuvem). */
class InfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        root.addView(MaterialToolbar(this).apply {
            title = getString(intent.getIntExtra(EXTRA_TITLE, R.string.app_name))
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        })
        root.addView(NestedScrollView(this).apply {
            addView(TextView(context).apply {
                setText(intent.getIntExtra(EXTRA_TEXT, R.string.app_name))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setPadding(pad, 0, pad, pad)
            })
        })
        setContentView(root)
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        fun intent(context: Context, title: Int, text: Int) =
            Intent(context, InfoActivity::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
    }
}
