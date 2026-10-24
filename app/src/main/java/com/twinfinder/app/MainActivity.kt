package com.twinfinder.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.databinding.ActivityMainBinding
import com.twinfinder.app.ui.OnboardingActivity
import com.twinfinder.app.ui.duplicates.DuplicatesFragment
import com.twinfinder.app.ui.home.HomeFragment
import com.twinfinder.app.ui.sessions.SessionsFragment
import com.twinfinder.app.ui.settings.SettingsFragment
import com.twinfinder.app.util.Permissions

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs(this).onboardingDone || !Permissions.hasMedia(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.bottomNav.setOnItemSelectedListener { show(it.itemId); true }
        if (savedInstanceState == null) b.bottomNav.selectedItemId = R.id.nav_home
    }

    fun select(itemId: Int) {
        b.bottomNav.selectedItemId = itemId
    }

    /** Troca de aba sem recriar os fragments (a busca de duplicatas não se perde). */
    private fun show(itemId: Int) {
        val fm = supportFragmentManager
        val tag = itemId.toString()
        val tx = fm.beginTransaction()
        fm.fragments.forEach { tx.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing != null) {
            tx.show(existing)
        } else {
            val fragment: Fragment = when (itemId) {
                R.id.nav_duplicates -> DuplicatesFragment()
                R.id.nav_sessions -> SessionsFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            tx.add(R.id.container, fragment, tag)
        }
        tx.commit()
    }
}
