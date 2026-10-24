package com.twinfinder.app.ui.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.twinfinder.app.R

/** Versão, privacidade, créditos e licença. */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        col.addView(MaterialToolbar(this).apply {
            setTitle(R.string.settings_about_app)
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, 0, pad, pad) }
        fun text(value: String, style: Int) = body.addView(TextView(this).apply {
            text = value
            setTextAppearance(style)
            setPadding(0, pad / 2, 0, pad / 2)
        })
        text(getString(R.string.app_name), com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall)
        text(getString(R.string.about_version, version), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        text(getString(R.string.about_text), com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        text(getString(R.string.about_credits), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        text(getString(R.string.about_license), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        col.addView(NestedScrollView(this).apply { addView(body) })
        setContentView(col)
    }
}
