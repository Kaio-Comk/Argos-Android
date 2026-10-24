package com.twinfinder.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Acha fotos e vídeos idênticos pelo conteúdo, em etapas, para ler o mínimo do armazenamento:
 * 1) agrupa por tamanho exato; 2) compara o começo e o fim do arquivo; 3) confirma com SHA-256 completo.
 */
class DuplicateScanner(private val context: Context) {

    /** Grupos de cópias idênticas, do que mais desperdiça espaço para o que menos desperdiça. */
    suspend fun scan(library: List<Media>, onProgress: (done: Int, total: Int) -> Unit): List<List<Media>> = withContext(Dispatchers.IO) {
        val bySize = library.groupBy { it.size }.values.filter { it.size > 1 }
        val total = bySize.sumOf { it.size }
        var done = 0
        val groups = mutableListOf<List<Media>>()
        for (sameSize in bySize) {
            coroutineContext.ensureActive()
            val byEdges = sameSize.groupBy { edgeHash(it) }.filterKeys { it != null }.values.filter { it.size > 1 }
            done += sameSize.size
            for (candidates in byEdges) {
                coroutineContext.ensureActive()
                candidates.groupBy { fullHash(it) }.filterKeys { it != null }.values
                    .filter { it.size > 1 }
                    .forEach { groups += it.sortedBy { m -> m.date } }
            }
            onProgress(done, total)
        }
        groups.sortedByDescending { g -> (g.size - 1) * g.first().size }
    }

    /** Hash dos primeiros e últimos 64 KB: separa rápido arquivos de mesmo tamanho mas conteúdo diferente. */
    private fun edgeHash(media: Media): String? = digest(media) { input, md ->
        val buf = ByteArray(EDGE)
        val head = input.readFully(buf)
        md.update(buf, 0, head)
        val toSkip = media.size - EDGE * 2
        if (toSkip > 0) {
            var skipped = 0L
            while (skipped < toSkip) {
                val s = input.skip(toSkip - skipped)
                if (s <= 0) break
                skipped += s
            }
            val tail = input.readFully(buf)
            md.update(buf, 0, tail)
        }
    }

    private fun fullHash(media: Media): String? = digest(media) { input, md ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }

    private inline fun digest(media: Media, feed: (InputStream, MessageDigest) -> Unit): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(media.uri) ?: return null
            input.use { feed(it, md) }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun InputStream.readFully(buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private companion object {
        const val EDGE = 64 * 1024
    }
}
