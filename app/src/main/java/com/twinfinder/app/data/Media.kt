package com.twinfinder.app.data

import android.net.Uri

enum class MediaType { PHOTO, VIDEO, GIF }

/** Uma foto ou vídeo do MediaStore. Nada sai do aparelho: só lemos metadados e o conteúdo local. */
data class Media(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val type: MediaType,
    /** Data em milissegundos: data da captura, ou a data em que entrou no aparelho. */
    val date: Long,
    val dateModified: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
) {
    val isVideo get() = type == MediaType.VIDEO

    /** Arquivos em Android/ pertencem a outros apps e o sistema não deixa movê-los. */
    val isInsideAndroidFolder get() = relativePath.startsWith("Android/", ignoreCase = true)
}

/** Um álbum (pasta) do MediaStore, com a contagem de mídias. */
data class Album(val bucketId: Long, val name: String, val count: Int, val cover: Uri?, val relativePath: String)
