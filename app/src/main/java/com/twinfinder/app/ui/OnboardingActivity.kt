package com.twinfinder.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.twinfinder.app.MainActivity
import com.twinfinder.app.R
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.databinding.ActivityOnboardingBinding
import com.twinfinder.app.databinding.ItemOnboardingBinding
import com.twinfinder.app.util.Permissions

/** Apresentação em 4 telas e, por último, o pedido de acesso às fotos e vídeos. */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var b: ActivityOnboardingBinding
    private lateinit var prefs: Prefs

    private data class Page(val icon: Int, val title: Int, val desc: Int)

    private val pages = listOf(
        Page(R.drawable.ic_check, R.string.onb_1_title, R.string.onb_1_desc),
        Page(R.drawable.ic_calendar, R.string.onb_2_title, R.string.onb_2_desc),
        Page(R.drawable.ic_copy, R.string.onb_3_title, R.string.onb_3_desc),
        Page(R.drawable.ic_info, R.string.onb_4_title, R.string.onb_4_desc),
        Page(R.drawable.ic_album, R.string.perm_title, R.string.perm_desc),
    )
    private val permissionPage get() = pages.lastIndex

    private val request = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Permissions.hasMedia(this)) finishOnboarding()
        else {
            val msg = if (Permissions.hasPartialMedia(this)) R.string.perm_partial else R.string.perm_denied
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            updateButtons()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.pager.adapter = PageAdapter()
        TabLayoutMediator(b.dots, b.pager) { _, _ -> }.attach()
        // Já viu a apresentação e só falta a permissão: vai direto para ela.
        if (prefs.onboardingDone) b.pager.setCurrentItem(permissionPage, false)
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateButtons()
        })
        b.skipButton.setOnClickListener { b.pager.currentItem = permissionPage }
        b.nextButton.setOnClickListener {
            if (b.pager.currentItem < permissionPage) b.pager.currentItem += 1 else askPermission()
        }
        updateButtons()
    }

    override fun onResume() {
        super.onResume()
        // Voltou dos ajustes do Android com a permissão dada.
        if (Permissions.hasMedia(this) && b.pager.currentItem == permissionPage) finishOnboarding()
    }

    private fun updateButtons() {
        val onPermission = b.pager.currentItem == permissionPage
        b.skipButton.isVisible = !onPermission
        b.nextButton.setText(
            when {
                !onPermission -> R.string.onb_next
                deniedForever() -> R.string.perm_settings
                else -> R.string.perm_grant
            }
        )
    }

    /** Negou e marcou "não perguntar de novo": só dá para liberar nos ajustes do Android. */
    private fun deniedForever(): Boolean =
        prefs.onboardingDone && !Permissions.hasMedia(this) &&
            Permissions.media.none { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }

    private fun askPermission() {
        if (deniedForever()) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            prefs.onboardingDone = true
            request.launch(Permissions.media)
        }
    }

    private fun finishOnboarding() {
        prefs.onboardingDone = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageAdapter.VH>() {
        inner class VH(val b: ItemOnboardingBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemOnboardingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = pages.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = pages[position]
            holder.b.icon.setImageResource(p.icon)
            holder.b.title.setText(p.title)
            holder.b.desc.setText(p.desc)
        }
    }
}
