package com.twinfinder.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class SessionCounts(
    val total: Int,
    val pending: Int,
    val keep: Int,
    val delete: Int,
    val skip: Int,
    val move: Int,
)

data class GlobalStats(val reviewed: Int, val freedBytes: Long)

/** Números da tela de Stats, só de sessões concluídas desde [since]. */
data class StyleStats(val kept: Int, val deleted: Int, val moved: Int, val skipped: Int)

data class MonthStat(val month: String, val reviewed: Int, val freedBytes: Long)

data class TimeStats(val sessions: Int, val timeMs: Long)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY updatedAt DESC")
    suspend fun byStatus(status: SessionStatus): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE status = 'IN_PROGRESS' AND mode = :mode AND modeKey = :key LIMIT 1")
    suspend fun openFor(mode: CleanMode, key: String): SessionEntity?

    @Query("SELECT COUNT(*) FROM sessions WHERE status = 'IN_PROGRESS'")
    suspend fun pendingCount(): Int

    @Query("UPDATE sessions SET status = 'DISCARDED', updatedAt = :now WHERE id = :id")
    suspend fun discard(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) AS sessions, COALESCE(SUM(timeSpentMs), 0) AS timeMs FROM sessions WHERE status = 'COMPLETED' AND updatedAt >= :since")
    suspend fun timeStats(since: Long): TimeStats
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<ReviewEntity>)

    @Query("SELECT * FROM reviews WHERE sessionId = :sessionId ORDER BY position")
    suspend fun forSession(sessionId: Long): List<ReviewEntity>

    @Query("UPDATE reviews SET decision = :decision, moveToPath = :movePath, updatedAt = :now WHERE sessionId = :sessionId AND mediaId = :mediaId")
    suspend fun decide(sessionId: Long, mediaId: Long, decision: Decision, movePath: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE reviews SET decision = :decision, updatedAt = :now WHERE sessionId = :sessionId AND decision = 'PENDING'")
    suspend fun decideAllPending(sessionId: Long, decision: Decision, now: Long = System.currentTimeMillis())

    @Query("UPDATE reviews SET result = :result, updatedAt = :now WHERE sessionId = :sessionId AND mediaId IN (:mediaIds)")
    suspend fun setResult(sessionId: Long, mediaIds: List<Long>, result: ActionResult, now: Long = System.currentTimeMillis())

    @Query(
        """SELECT COUNT(*) AS total,
           COALESCE(SUM(decision = 'PENDING'), 0) AS pending, COALESCE(SUM(decision = 'KEEP'), 0) AS keep,
           COALESCE(SUM(decision = 'DELETE'), 0) AS `delete`, COALESCE(SUM(decision = 'SKIP'), 0) AS skip,
           COALESCE(SUM(decision = 'MOVE'), 0) AS move
           FROM reviews WHERE sessionId = :sessionId"""
    )
    suspend fun counts(sessionId: Long): SessionCounts

    /** Mídias já decididas (manter ou mover) em sessões concluídas: não voltam numa sessão nova. */
    @Query(
        """SELECT DISTINCT r.mediaId FROM reviews r JOIN sessions s ON s.id = r.sessionId
           WHERE s.status = 'COMPLETED' AND r.decision IN ('KEEP', 'MOVE')"""
    )
    suspend fun settledMediaIds(): List<Long>

    @Query(
        """SELECT COUNT(*) AS reviewed,
           COALESCE((SELECT SUM(size) FROM reviews WHERE decision = 'DELETE' AND result = 'DONE' AND updatedAt >= :since), 0) AS freedBytes
           FROM reviews WHERE decision IN ('KEEP', 'DELETE', 'MOVE') AND updatedAt >= :since"""
    )
    suspend fun stats(since: Long): GlobalStats

    @Query(
        """SELECT COALESCE(SUM(r.decision = 'KEEP'), 0) AS kept, COALESCE(SUM(r.decision = 'DELETE' AND r.result = 'DONE'), 0) AS deleted,
           COALESCE(SUM(r.decision = 'MOVE' AND r.result = 'DONE'), 0) AS moved, COALESCE(SUM(r.decision = 'SKIP'), 0) AS skipped
           FROM reviews r JOIN sessions s ON s.id = r.sessionId
           WHERE s.status = 'COMPLETED' AND r.updatedAt >= :since"""
    )
    suspend fun style(since: Long): StyleStats

    /** Revisões e espaço liberado por mês (yyyy-MM, hora local), para achar o melhor mês. */
    @Query(
        """SELECT strftime('%Y-%m', r.updatedAt / 1000, 'unixepoch', 'localtime') AS month,
           COUNT(*) AS reviewed, COALESCE(SUM(CASE WHEN r.decision = 'DELETE' AND r.result = 'DONE' THEN r.size ELSE 0 END), 0) AS freedBytes
           FROM reviews r JOIN sessions s ON s.id = r.sessionId
           WHERE s.status = 'COMPLETED' AND r.decision IN ('KEEP', 'DELETE', 'MOVE') AND r.updatedAt >= :since
           GROUP BY month ORDER BY reviewed DESC"""
    )
    suspend fun byMonth(since: Long): List<MonthStat>

    @Query("UPDATE reviews SET position = :position WHERE sessionId = :sessionId AND mediaId = :mediaId")
    suspend fun setPosition(sessionId: Long, mediaId: Long, position: Int)

    /** "Descartar escolhas": volta tudo que ainda não foi aplicado para pendente. */
    @Query("UPDATE reviews SET decision = 'PENDING', moveToPath = NULL, updatedAt = :now WHERE sessionId = :sessionId AND result != 'DONE'")
    suspend fun resetDecisions(sessionId: Long, now: Long = System.currentTimeMillis())
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM excluded_albums ORDER BY name")
    suspend fun excluded(): List<ExcludedAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun exclude(album: ExcludedAlbumEntity)

    @Query("DELETE FROM excluded_albums WHERE bucketId = :bucketId")
    suspend fun include(bucketId: Long)

    @Query("SELECT * FROM move_targets WHERE hidden = 0 ORDER BY orderIndex, name")
    suspend fun moveTargets(): List<MoveTargetEntity>

    @Query("SELECT * FROM move_targets ORDER BY orderIndex, name")
    suspend fun allMoveTargets(): List<MoveTargetEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMoveTarget(target: MoveTargetEntity): Long

    @Update
    suspend fun updateMoveTargets(targets: List<MoveTargetEntity>)

    @Query("DELETE FROM move_targets WHERE id = :id")
    suspend fun removeMoveTarget(id: Long)

    @Query("SELECT * FROM album_order ORDER BY orderIndex")
    suspend fun albumOrder(): List<AlbumOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlbumOrder(order: List<AlbumOrderEntity>)
}

@Dao
interface TrashDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(items: List<TrashedEntity>)

    @Query("SELECT * FROM trashed")
    suspend fun all(): List<TrashedEntity>

    @Query("DELETE FROM trashed WHERE mediaId IN (:ids)")
    suspend fun remove(ids: List<Long>)

    @Query("DELETE FROM trashed")
    suspend fun clear()
}
