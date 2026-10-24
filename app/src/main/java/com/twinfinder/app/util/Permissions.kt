package com.twinfinder.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    /**
     * No Android 14+ pedimos também READ_MEDIA_VISUAL_USER_SELECTED. Sem declará-la, o sistema
     * entra em modo de compatibilidade e diz que o acesso é total quando o usuário escolheu só
     * algumas fotos, e o app limparia uma galeria pela metade sem perceber.
     */
    val media: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        // Até o Android 10 (armazenamento legado) apagar e mover exige escrita no armazenamento.
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    /** Acesso à galeria inteira (fotos e vídeos). */
    fun hasMedia(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            granted(context, Manifest.permission.READ_MEDIA_IMAGES) && granted(context, Manifest.permission.READ_MEDIA_VIDEO)
        else -> media.all { granted(context, it) }
    }

    /** Android 14+: o usuário liberou só algumas fotos. */
    fun hasPartialMedia(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasMedia(context) &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
