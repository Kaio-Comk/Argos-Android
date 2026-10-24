package com.twinfinder.app.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/** Lê fotos e vídeos do MediaStore. Toda a leitura é local. */
class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    private val projection: Array<String> = buildList {
        add(MediaStore.Files.FileColumns._ID)
        add(MediaStore.Files.FileColumns.DISPLAY_NAME)
        add(MediaStore.Files.FileColumns.SIZE)
        add(MediaStore.Files.FileColumns.MIME_TYPE)
        add(MediaStore.Files.FileColumns.MEDIA_TYPE)
        add(MediaStore.Files.FileColumns.DATE_ADDED)
        add(MediaStore.Files.FileColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.DATE_TAKEN)
        add(MediaStore.MediaColumns.BUCKET_ID)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.DURATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.RELATIVE_PATH)
        else add(MediaStore.MediaColumns.DATA)
    }.toTypedArray()

    private val mediaSelection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, " +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}) AND ${MediaStore.Files.FileColumns.SIZE} > 0"

    /** Todas as fotos e vídeos do aparelho, sem os álbuns excluídos. */
    fun loadAll(excludedBuckets: Set<Long> = emptySet()): List<Media> {
        val list = mutableListOf<Media>()
        resolver.query(filesUri, projection, mediaSelection, null, null)?.use { c ->
            while (c.moveToNext()) {
                val m = c.toMedia() ?: continue
                if (m.bucketId !in excludedBuckets) list.add(m)
            }
        }
        return list
    }

    /** Busca mídias pelos IDs, mantendo a ordem pedida. IDs que sumiram do aparelho ficam de fora. */
    fun loadByIds(ids: List<Long>): Map<Long, Media> {
        val result = HashMap<Long, Media>(ids.size)
        ids.chunked(500).forEach { chunk ->
            val sel = "${MediaStore.Files.FileColumns._ID} IN (${chunk.joinToString(",")})"
            resolver.query(filesUri, projection, sel, null, null)?.use { c ->
                while (c.moveToNext()) c.toMedia()?.let { result[it.id] = it }
            }
        }
        return result
    }

    fun albums(all: List<Media>): List<Album> =
        all.groupBy { it.bucketId }.map { (id, items) ->
            val newest = items.maxByOrNull { it.date }
            Album(id, items.first().bucketName, items.size, newest?.uri, items.first().relativePath)
        }.sortedByDescending { it.count }

    /** Itens na lixeira do sistema (Android 11+), com a data em que serão apagados de vez. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun loadTrashed(ids: Collection<Long>): List<Pair<Media, Long>> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<Pair<Media, Long>>()
        val proj = projection + MediaStore.MediaColumns.DATE_EXPIRES
        ids.chunked(500).forEach { chunk ->
            val args = Bundle().apply {
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${MediaStore.Files.FileColumns._ID} IN (${chunk.joinToString(",")})"
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            resolver.query(filesUri, proj, args, null)?.use { c ->
                val exp = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
                while (c.moveToNext()) c.toMedia()?.let { out.add(it to c.getLong(exp) * 1000) }
            }
        }
        return out
    }

    private fun Cursor.toMedia(): Media? {
        val id = long(MediaStore.Files.FileColumns._ID)
        val mediaType = int(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val mime = string(MediaStore.Files.FileColumns.MIME_TYPE) ?: return null
        val type = when {
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaType.VIDEO
            mime.equals("image/gif", ignoreCase = true) -> MediaType.GIF
            else -> MediaType.PHOTO
        }
        val base = if (type == MediaType.VIDEO) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val taken = long(MediaStore.MediaColumns.DATE_TAKEN)
        val added = long(MediaStore.Files.FileColumns.DATE_ADDED) * 1000
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            string(MediaStore.MediaColumns.RELATIVE_PATH).orEmpty()
        } else {
            // Antes do Android 10 só existe o caminho absoluto; guardamos a parte relativa ao armazenamento.
            string(MediaStore.MediaColumns.DATA).orEmpty().substringAfter("/0/", "").substringBeforeLast('/') + "/"
        }
        return Media(
            id = id,
            uri = ContentUris.withAppendedId(base, id),
            name = string(MediaStore.Files.FileColumns.DISPLAY_NAME) ?: "?",
            size = long(MediaStore.Files.FileColumns.SIZE),
            mimeType = mime,
            type = type,
            date = if (taken > 0) taken else added,
            dateModified = long(MediaStore.Files.FileColumns.DATE_MODIFIED) * 1000,
            bucketId = long(MediaStore.MediaColumns.BUCKET_ID),
            bucketName = string(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) ?: "?",
            relativePath = path,
            width = int(MediaStore.MediaColumns.WIDTH),
            height = int(MediaStore.MediaColumns.HEIGHT),
            durationMs = long(MediaStore.MediaColumns.DURATION),
        )
    }

    private fun Cursor.long(col: String) = getColumnIndex(col).let { if (it < 0 || isNull(it)) 0L else getLong(it) }
    private fun Cursor.int(col: String) = getColumnIndex(col).let { if (it < 0 || isNull(it)) 0 else getInt(it) }
    private fun Cursor.string(col: String): String? = getColumnIndex(col).let { if (it < 0) null else getString(it) }
}
