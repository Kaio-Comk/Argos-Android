package com.twinfinder.app.ui.common

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.twinfinder.app.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Aplica lixeira, exclusão e movimentação nos arquivos.
 *
 * No Android 11+ cada pedido abre o diálogo de confirmação do próprio sistema (o app nunca apaga
 * sozinho). Precisa ser criado como campo da Activity, antes do onStart, por causa do
 * registerForActivityResult.
 */
class MediaActions(private val activity: ComponentActivity) {

    private var onResult: ((Boolean) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        onResult?.invoke(it.resultCode == Activity.RESULT_OK)
        onResult = null
    }

    private val resolver get() = activity.contentResolver

    /** Quantos pedidos de confirmação serão abertos para [count] itens. */
    fun parts(count: Int) = (count + CHUNK - 1) / CHUNK

    /** Manda para a lixeira (Android 11+). Devolve os IDs confirmados pelo usuário. */
    suspend fun trash(items: List<Media>, trashed: Boolean = true): List<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return confirmInChunks(items) { MediaStore.createTrashRequest(resolver, it, trashed) }
    }

    /** Apaga de vez. No Android 11+ pede confirmação ao sistema; antes disso apaga direto (modo legado). */
    suspend fun delete(items: List<Media>): List<Long> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return confirmInChunks(items) { MediaStore.createDeleteRequest(resolver, it) }
        }
        return withContext(Dispatchers.IO) {
            items.filter { runCatching { resolver.delete(it.uri, null, null) > 0 }.getOrDefault(false) }.map { it.id }
        }
    }

    /**
     * Move cada mídia para o RELATIVE_PATH indicado (Android 10+).
     * Devolve os IDs movidos; os que falharem continuam onde estavam.
     */
    suspend fun move(items: List<Pair<Media, String>>): List<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val allowed = items.filterNot { it.first.isInsideAndroidFolder }
        val granted: Set<Long> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            confirmInChunks(allowed.map { it.first }) { MediaStore.createWriteRequest(resolver, it) }.toSet()
        } else {
            allowed.map { it.first.id }.toSet()
        }
        return withContext(Dispatchers.IO) {
            allowed.filter { it.first.id in granted }.filter { (media, path) ->
                runCatching {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, path) }
                    resolver.update(media.uri, values, null, null) > 0
                }.getOrDefault(false)
            }.map { it.first.id }
        }
    }

    private suspend fun confirmInChunks(items: List<Media>, request: (List<Uri>) -> PendingIntent): List<Long> {
        val done = mutableListOf<Long>()
        for (chunk in items.chunked(CHUNK)) {
            val ok = launch(request(chunk.map { it.uri }))
            if (!ok) break
            done += chunk.map { it.id }
        }
        return done
    }

    private suspend fun launch(pi: PendingIntent): Boolean = suspendCancellableCoroutine { cont ->
        onResult = { if (cont.isActive) cont.resume(it) }
        launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    companion object {
        /** Itens por pedido ao sistema: listas muito grandes estouram o limite do Binder. */
        const val CHUNK = 500
    }
}
