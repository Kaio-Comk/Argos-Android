package com.twinfinder.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.Prefs
import com.twinfinder.app.data.SessionRepository
import com.twinfinder.app.data.SortOption
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.databinding.ItemSettingBinding
import com.twinfinder.app.reminders.Reminders
import com.twinfinder.app.util.Permissions
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var column: LinearLayout

    /** Lembrete esperando a permissão de notificação: "weekly" ou "monthly". */
    private var pendingReminder: String? = null

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when (pendingReminder) {
            "weekly" -> prefs.weeklyReminder = granted
            "monthly" -> prefs.monthlyReminder = granted
        }
        pendingReminder = null
        if (!granted) Toast.makeText(requireContext(), R.string.settings_notifications_denied, Toast.LENGTH_LONG).show()
        Reminders.sync(requireContext())
        build()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        prefs = Prefs(requireContext())
        column = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        return NestedScrollView(requireContext()).apply { addView(column) }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (!hidden) build()
    }

    private fun build() {
        column.removeAllViews()
        val ctx = requireContext()

        section(R.string.settings_appearance)
        val themes = listOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM to R.string.theme_system,
            AppCompatDelegate.MODE_NIGHT_NO to R.string.theme_light,
            AppCompatDelegate.MODE_NIGHT_YES to R.string.theme_dark,
        )
        row(R.string.settings_theme, getString(themes.firstOrNull { it.first == prefs.themeMode }?.second ?: R.string.theme_system)) {
            choose(R.string.settings_theme, themes.map { getString(it.second) }, themes.indexOfFirst { it.first == prefs.themeMode }) { i ->
                prefs.themeMode = themes[i].first
                AppCompatDelegate.setDefaultNightMode(themes[i].first)
            }
        }

        section(R.string.settings_cleaning)
        val trashSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        toggle(
            R.string.settings_use_trash,
            if (trashSupported) getString(R.string.settings_use_trash_desc) else getString(R.string.trash_unsupported),
            prefs.useTrash && trashSupported, enabled = trashSupported,
        ) { prefs.useTrash = it }
        row(R.string.settings_random_count, prefs.randomCount.toString()) { askRandomCount() }
        val sorts = listOf(
            SortOption.DATE_DESC to R.string.sort_date_desc, SortOption.DATE_ASC to R.string.sort_date_asc,
            SortOption.SIZE_DESC to R.string.sort_size_desc, SortOption.SIZE_ASC to R.string.sort_size_asc,
            SortOption.NAME_ASC to R.string.sort_name_asc,
        )
        row(R.string.settings_sort, getString(sorts.first { it.first == prefs.sortOption }.second)) {
            choose(R.string.settings_sort, sorts.map { getString(it.second) }, sorts.indexOfFirst { it.first == prefs.sortOption }) { i ->
                prefs.sortOption = sorts[i].first
                build()
            }
        }

        section(R.string.settings_view)
        toggle(R.string.settings_strip, null, prefs.showStrip) { prefs.showStrip = it }
        toggle(R.string.settings_file_info, null, prefs.showFileInfo) { prefs.showFileInfo = it }
        toggle(R.string.settings_video_sound, null, prefs.videoSound) { prefs.videoSound = it }
        toggle(R.string.settings_animate, null, prefs.animateButtons) { prefs.animateButtons = it }
        toggle(R.string.settings_proceed_top, null, prefs.proceedOnTop) { prefs.proceedOnTop = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            toggle(R.string.settings_organize, getString(R.string.settings_organize_desc), prefs.organizeMode) { prefs.organizeMode = it }
        }
        toggle(R.string.settings_share_text, null, prefs.shareWithText) { prefs.shareWithText = it }

        section(R.string.settings_albums)
        val excludedRow = row(R.string.settings_excluded_albums, null) {
            startActivity(Intent(ctx, AlbumsActivity::class.java))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val n = AppDatabase.get(ctx).albums().excluded().size
            excludedRow.desc.isVisible = n > 0
            excludedRow.desc.text = getString(R.string.settings_excluded_count, n)
        }
        row(R.string.settings_move_targets, null) { startActivity(Intent(ctx, MoveTargetsActivity::class.java)) }

        section(R.string.settings_reminders)
        toggle(R.string.settings_weekly, getString(R.string.settings_weekly_desc), prefs.weeklyReminder) { on ->
            setReminder("weekly", on)
        }
        toggle(R.string.settings_monthly, getString(R.string.settings_monthly_desc), prefs.monthlyReminder) { on ->
            setReminder("monthly", on)
        }

        section(R.string.settings_data)
        row(R.string.settings_reset, getString(R.string.settings_reset_desc)) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.settings_reset)
                .setMessage(R.string.settings_reset_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        SessionRepository(ctx).resetAll()
                        Reminders.sync(ctx)
                        AppCompatDelegate.setDefaultNightMode(prefs.themeMode)
                        Toast.makeText(ctx, R.string.reset_done, Toast.LENGTH_SHORT).show()
                        build()
                    }
                }.show()
        }

        section(R.string.settings_about)
        row(R.string.settings_about_app, null) { startActivity(Intent(ctx, AboutActivity::class.java)) }
        row(R.string.settings_share_app, null) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_message))
            startActivity(Intent.createChooser(send, getString(R.string.settings_share_app)))
        }
        row(R.string.settings_feedback, null) { open(getString(R.string.feedback_url)) }
        row(R.string.settings_rate, null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ctx.packageName}")))
            } catch (_: ActivityNotFoundException) {
                open("https://play.google.com/store/apps/details?id=${ctx.packageName}")
            }
        }
    }

    private fun setReminder(kind: String, on: Boolean) {
        val ctx = requireContext()
        if (on && !Permissions.canNotify(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingReminder = kind
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (kind == "weekly") prefs.weeklyReminder = on else prefs.monthlyReminder = on
        Reminders.sync(ctx)
    }

    private fun askRandomCount() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.randomCount.toString())
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_random_count)
            .setView(LinearLayout(ctx).apply { setPadding(48, 16, 48, 0); addView(input, LinearLayout.LayoutParams(-1, -2)) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n == null || n !in Prefs.RANDOM_MIN..Prefs.RANDOM_MAX) {
                    Toast.makeText(ctx, getString(R.string.settings_random_error, Prefs.RANDOM_MIN, Prefs.RANDOM_MAX), Toast.LENGTH_LONG).show()
                } else {
                    prefs.randomCount = n
                    build()
                }
            }.show()
    }

    private fun choose(title: Int, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), selected) { d, i ->
                onPick(i)
                d.dismiss()
                build()
            }.show()
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show()
        }
    }

    private fun section(title: Int) {
        column.addView(TextView(requireContext()).apply {
            setText(title)
            setTextAppearance(R.style.TextAppearance_TwinFinder_Section)
            setPadding(48, 48, 48, 8)
        })
    }

    private fun row(title: Int, desc: String?, onClick: () -> Unit): ItemSettingBinding {
        val r = ItemSettingBinding.inflate(layoutInflater, column, false)
        r.title.setText(title)
        r.desc.isVisible = desc != null
        r.desc.text = desc
        r.root.setOnClickListener { onClick() }
        column.addView(r.root)
        return r
    }

    private fun toggle(title: Int, desc: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
        val r = row(title, desc) {}
        r.toggle.isVisible = true
        r.toggle.isChecked = value
        r.root.isEnabled = enabled
        r.toggle.isEnabled = enabled
        r.root.setOnClickListener {
            if (!enabled) return@setOnClickListener
            r.toggle.isChecked = !r.toggle.isChecked
            onChange(r.toggle.isChecked)
        }
    }
}
