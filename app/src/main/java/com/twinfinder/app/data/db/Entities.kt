package com.twinfinder.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Como a sessão escolhe as mídias. */
enum class CleanMode { MONTH, ALBUM, TYPE, RANDOM, DUPLICATES }

enum class SessionStatus { IN_PROGRESS, COMPLETED, DISCARDED }

/** O que o usuário decidiu para cada mídia da sessão. */
enum class Decision { PENDING, KEEP, DELETE, SKIP, MOVE }

/** O que aconteceu quando a decisão foi aplicada. */
enum class ActionResult { NONE, DONE, FAILED }

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: CleanMode,
    /** Chave do modo: "2026-10" (mês), bucketId (álbum), "VIDEO" (tipo); vazia para aleatório e duplicatas. */
    val modeKey: String,
    val label: String,
    val status: SessionStatus = SessionStatus.IN_PROGRESS,
    /** Posição atual na pilha de cartões, para continuar de onde parou. */
    val position: Int = 0,
    val timeSpentMs: Long = 0,
    val freedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "reviews",
    primaryKeys = ["sessionId", "mediaId"],
    indices = [Index("mediaId")],
)
data class ReviewEntity(
    val sessionId: Long,
    val mediaId: Long,
    val position: Int,
    val decision: Decision = Decision.PENDING,
    /** RELATIVE_PATH de destino quando a decisão é mover (ex.: "Pictures/Viagem/"). */
    val moveToPath: String? = null,
    val size: Long,
    val mimeType: String,
    val result: ActionResult = ActionResult.NONE,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "excluded_albums")
data class ExcludedAlbumEntity(
    @PrimaryKey val bucketId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "move_targets", indices = [Index(value = ["relativePath"], unique = true)])
data class MoveTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relativePath: String,
    val createdInApp: Boolean,
    val orderIndex: Int,
    val hidden: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ordem personalizada dos álbuns na escolha da limpeza (Ajustes → Álbuns). */
@Entity(tableName = "album_order")
data class AlbumOrderEntity(
    @PrimaryKey val bucketId: Long,
    val orderIndex: Int,
)

/** Mídias que o app mandou para a lixeira do sistema (para a tela de Lixeira mostrar só o que é nosso). */
@Entity(tableName = "trashed")
data class TrashedEntity(
    @PrimaryKey val mediaId: Long,
    val size: Long,
    val trashedAt: Long = System.currentTimeMillis(),
)
