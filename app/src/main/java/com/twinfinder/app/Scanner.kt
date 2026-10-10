package com.twinfinder.app

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.security.MessageDigest

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val type: String
)

class Scanner(private val context: Context) {

    fun scanImages(): List<MediaItem> {
        val mediaList = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "unknown"
                val size = cursor.getLong(sizeColumn)
                
                // Ignorar arquivos vazios
                if (size <= 0) continue

                val contentUri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                mediaList.add(MediaItem(id, contentUri, name, size, "image"))
            }
        }
        return mediaList
    }

    // Calcula o hash MD5 (para duplicatas exatas)
    fun calculateHash(uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
