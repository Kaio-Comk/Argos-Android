package com.twinfinder.app.ui.clean

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.twinfinder.app.R
import com.twinfinder.app.data.Media
import com.twinfinder.app.data.MediaRepository
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.data.db.MoveTargetEntity
import com.twinfinder.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun AppCompatActivity.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun AppCompatActivity.sheetColumn(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20))
}

private fun AppCompatActivity.textRow(text: String, style: Int, onClick: (() -> Unit)? = null) = TextView(this).apply {
    this.text = text
    setTextAppearance(style)
    setPadding(0, dp(10), 0, dp(10))
    if (onClick != null) {
        val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        background = attrs.getDrawable(0)
        attrs.recycle()
        setOnClickListener { onClick() }
    }
}

/** Detalhes do arquivo atual. */
object FileInfoSheet {
    fun show(activity: AppCompatActivity, media: Media) {
        val dialog = BottomSheetDialog(activity)
        val col = activity.sheetColumn()
        col.addView(activity.textRow(activity.getString(R.string.file_info), com.google.android.material.R.style.TextAppearance_Material3_TitleLarge))
        fun row(label: Int, value: String) {
            col.addView(activity.textRow(activity.getString(label), com.google.android.material.R.style.TextAppearance_Material3_LabelMedium))
            col.addView(TextView(activity).apply { text = value; setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge) })
        }
        row(R.string.info_name, media.name)
        row(R.string.info_date, Fmt.date(media.date))
        if (media.dateModified > 0) row(R.string.info_modified, Fmt.date(media.dateModified))
        row(R.string.info_size, Fmt.size(activity, media.size))
        if (media.width > 0 && media.height > 0) row(R.string.info_dimensions, "${media.width} × ${media.height}")
        if (media.isVideo) row(R.string.info_duration, Fmt.duration(media.durationMs))
        row(R.string.info_type, media.mimeType)
        row(R.string.info_album, media.bucketName)
        row(R.string.info_path, media.relativePath)
        dialog.setContentView(col)
        dialog.show()
    }
}

/** Escolha do destino para mover: meus destinos, álbuns do aparelho ou um álbum novo. */
object MoveSheet {
    fun show(activity: AppCompatActivity, onPick: (String) -> Unit) {
        val db = AppDatabase.get(activity)
        val dialog = BottomSheetDialog(activity)
        val col = activity.sheetColumn()
        val title = com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
        val section = com.google.android.material.R.style.TextAppearance_Material3_LabelLarge
        val body = com.google.android.material.R.style.TextAppearance_Material3_BodyLarge
        col.addView(activity.textRow(activity.getString(R.string.move_title), title))
        dialog.setContentView(android.widget.ScrollView(activity).apply { addView(col) })

        fun pick(name: String, path: String) {
            activity.lifecycleScope.launch {
                db.albums().addMoveTarget(MoveTargetEntity(name = name, relativePath = path, createdInApp = false, orderIndex = Int.MAX_VALUE))
                dialog.dismiss()
                onPick(path)
            }
        }

        col.addView(activity.textRow("+ " + activity.getString(R.string.move_new_album), body) {
            val input = EditText(activity).apply { hint = activity.getString(R.string.move_new_album_hint); maxLines = 1 }
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.move_new_album)
                .setView(LinearLayout(activity).apply { setPadding(activity.dp(20)); addView(input, LinearLayout.LayoutParams(-1, -2)) })
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val name = input.text.toString().trim().take(32).replace(Regex("[\\\\/:*?\"<>|]"), "")
                    if (name.isNotEmpty()) {
                        activity.lifecycleScope.launch {
                            val path = "Pictures/$name/"
                            db.albums().addMoveTarget(MoveTargetEntity(name = name, relativePath = path, createdInApp = true, orderIndex = 0))
                            dialog.dismiss()
                            onPick(path)
                        }
                    }
                }.show()
        })

        activity.lifecycleScope.launch {
            val targets = db.albums().moveTargets()
            val albums = withContext(Dispatchers.IO) { MediaRepository(activity).let { it.albums(it.loadAll()) } }
                .filterNot { it.relativePath.startsWith("Android/", true) || it.relativePath.isBlank() }
            if (targets.isNotEmpty()) {
                col.addView(activity.textRow(activity.getString(R.string.move_my_targets), section))
                targets.forEach { t -> col.addView(activity.textRow(t.name, body) { pick(t.name, t.relativePath) }) }
            }
            col.addView(activity.textRow(activity.getString(R.string.move_existing_albums), section))
            albums.filter { a -> targets.none { it.relativePath == a.relativePath } }.forEach { a ->
                col.addView(activity.textRow("${a.name}  ·  ${a.count}", body) { pick(a.name, a.relativePath) })
            }
        }
        dialog.show()
    }
}

/** "Personalizar tela": os ajustes da tela de limpeza, aplicados na hora. */
object CustomizeViewSheet {
    fun show(activity: AppCompatActivity, onChange: () -> Unit) {
        val prefs = com.twinfinder.app.data.Prefs(activity)
        val dialog = BottomSheetDialog(activity)
        val col = activity.sheetColumn()
        col.addView(activity.textRow(activity.getString(R.string.customize_view), com.google.android.material.R.style.TextAppearance_Material3_TitleLarge))
        col.addView(activity.textRow(activity.getString(R.string.customize_view_desc), com.google.android.material.R.style.TextAppearance_Material3_BodyMedium))
        fun toggle(title: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
            col.addView(com.google.android.material.materialswitch.MaterialSwitch(activity).apply {
                text = activity.getString(title)
                isChecked = get()
                setPadding(0, activity.dp(6), 0, activity.dp(6))
                setOnCheckedChangeListener { _, on -> set(on); onChange() }
            })
        }
        toggle(R.string.settings_strip, { prefs.showStrip }) { prefs.showStrip = it }
        toggle(R.string.settings_file_info, { prefs.showFileInfo }) { prefs.showFileInfo = it }
        toggle(R.string.settings_proceed_top, { prefs.proceedOnTop }) { prefs.proceedOnTop = it }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            toggle(R.string.settings_organize, { prefs.organizeMode }) { prefs.organizeMode = it }
        }
        toggle(R.string.settings_video_sound, { prefs.videoSound }) { prefs.videoSound = it }
        toggle(R.string.settings_animate, { prefs.animateButtons }) { prefs.animateButtons = it }
        dialog.setContentView(android.widget.ScrollView(activity).apply { addView(col) })
        dialog.show()
    }
}
