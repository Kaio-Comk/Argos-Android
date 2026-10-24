package com.twinfinder.app.data

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import com.twinfinder.app.R
import com.twinfinder.app.data.db.ActionResult
import com.twinfinder.app.data.db.AlbumOrderEntity
import com.twinfinder.app.data.db.AppDatabase
import com.twinfinder.app.data.db.CleanMode
import com.twinfinder.app.data.db.Decision
import com.twinfinder.app.data.db.GlobalStats
import com.twinfinder.app.data.db.ReviewEntity
import com.twinfinder.app.data.db.SessionEntity
import com.twinfinder.app.data.db.SessionStatus
import com.twinfinder.app.data.db.StyleStats
import com.twinfinder.app.data.db.TrashedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Um grupo de mídias para escolher a limpeza (um mês, um álbum ou um tipo). */
data class MediaGroup(
    val mode: CleanMode,
    val key: String,
    val label: String,
    val total: Int,
    val remaining: Int,
    val cover: Uri?,
    val items: List<Media>,
)

/** Uma mídia da sessão com a decisão atual. */
data class SessionItem(val media: Media, val review: ReviewEntity)

data class StatsSummary(
    val reviewed: Int,
    val freedBytes: Long,
    val style: StyleStats,
    val sessions: Int,
    val timeMs: Long,
    val bestMonth: String?,
    val bestMonthReviewed: Int,
    val since: Long,
) {
    /** Itens revisados por hora de limpeza. */
    val perHour: Int get() = if (timeMs < 1000) 0 else (reviewed * 3_600_000L / timeMs).toInt()
}

class SessionRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val mediaRepo = MediaRepository(context)
    private val prefs = Prefs(context)

    suspend fun library(): List<Media> = withContext(Dispatchers.IO) {
        val excluded = db.albums().excluded().map { it.bucketId }.toSet()
        mediaRepo.loadAll(excluded)
    }

    suspend fun settledIds(): Set<Long> = db.reviews().settledMediaIds().toSet()

    fun monthGroups(all: List<Media>, settled: Set<Long>): List<MediaGroup> {
        val keyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFmt = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy"), Locale.getDefault())
        return all.groupBy { keyFmt.format(Date(it.date)) }
            .map { (key, items) ->
                val label = labelFmt.format(Date(items.first().date)).replaceFirstChar { it.titlecase(Locale.getDefault()) }
                group(CleanMode.MONTH, key, label, items, settled)
            }
            .sortedByDescending { it.key }
    }

    fun albumGroups(all: List<Media>, settled: Set<Long>): List<MediaGroup> =
        all.groupBy { it.bucketId }
            .map { (id, items) -> group(CleanMode.ALBUM, id.toString(), items.first().bucketName, items, settled) }
            .sortedByDescending { it.total }

    fun typeGroups(all: List<Media>, settled: Set<Long>): List<MediaGroup> {
        val labels = mapOf(
            MediaType.PHOTO to context.getString(R.string.type_photos),
            MediaType.VIDEO to context.getString(R.string.type_videos),
            MediaType.GIF to context.getString(R.string.type_gifs),
        )
        return MediaType.entries.mapNotNull { type ->
            val items = all.filter { it.type == type }
            if (items.isEmpty()) null else group(CleanMode.TYPE, type.name, labels.getValue(type), items, settled)
        }
    }

    private fun group(mode: CleanMode, key: String, label: String, items: List<Media>, settled: Set<Long>) =
        MediaGroup(mode, key, label, items.size, items.count { it.id !in settled }, items.maxByOrNull { it.date }?.uri, items)

    /** Ordem personalizada dos álbuns (bucketId → posição). */
    suspend fun albumOrder(): Map<Long, Int> = db.albums().albumOrder().associate { it.bucketId to it.orderIndex }

    suspend fun saveAlbumOrder(bucketIds: List<Long>) =
        db.albums().saveAlbumOrder(bucketIds.mapIndexed { i, id -> AlbumOrderEntity(id, i) })

    /** Ordena os grupos da escolha da limpeza. DEFAULT mantém a ordem natural de cada agrupamento. */
    fun sortGroups(groups: List<MediaGroup>, sort: GroupSort, custom: Map<Long, Int> = emptyMap()): List<MediaGroup> {
        val byName = compareBy<MediaGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
        return when (sort) {
            GroupSort.DEFAULT -> groups
            GroupSort.OLDEST -> groups.sortedBy { g -> g.items.minOf { it.date } }
            GroupSort.TOTAL_DESC -> groups.sortedByDescending { it.total }
            GroupSort.TOTAL_ASC -> groups.sortedBy { it.total }
            GroupSort.REMAINING_DESC -> groups.sortedByDescending { it.remaining }
            GroupSort.REMAINING_ASC -> groups.sortedBy { it.remaining }
            GroupSort.NAME_ASC -> groups.sortedWith(byName)
            GroupSort.NAME_DESC -> groups.sortedWith(byName.reversed())
            GroupSort.CUSTOM -> groups.sortedWith(
                compareBy<MediaGroup> { custom[it.key.toLongOrNull()] ?: Int.MAX_VALUE }.thenByDescending { it.total }
            )
        }
    }

    /** Reordena só os itens ainda pendentes de uma sessão aberta; os já decididos ficam onde estão. */
    suspend fun resortPending(sessionId: Long, option: SortOption) {
        val items = items(sessionId)
        val decided = items.filter { it.review.decision != Decision.PENDING }
        val pending = sort(items.filter { it.review.decision == Decision.PENDING }.map { it.media }, option)
        (decided.map { it.media } + pending).forEachIndexed { i, m -> db.reviews().setPosition(sessionId, m.id, i) }
    }

    /** Tudo o que a tela de Stats mostra. */
    suspend fun statsSummary(): StatsSummary {
        val since = prefs.statsSince
        val base = db.reviews().stats(since)
        val time = db.sessions().timeStats(since)
        val best = db.reviews().byMonth(since).firstOrNull()
        val bestLabel = best?.let { m ->
            val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(m.month) ?: return@let null
            SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMyyyy"), Locale.getDefault())
                .format(date).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
        return StatsSummary(
            reviewed = base.reviewed, freedBytes = base.freedBytes, style = db.reviews().style(since),
            sessions = time.sessions, timeMs = time.timeMs, bestMonth = bestLabel, bestMonthReviewed = best?.reviewed ?: 0,
            since = since,
        )
    }

    /** Sessão aberta para este grupo, se existir. */
    suspend fun openSession(mode: CleanMode, key: String): SessionEntity? = db.sessions().openFor(mode, key)

    /**
     * Cria a sessão de um grupo. Sem [reviewAgain], o que já foi mantido ou movido em sessões
     * concluídas fica de fora.
     */
    suspend fun createSession(mode: CleanMode, key: String, label: String, items: List<Media>, reviewAgain: Boolean): Long {
        val settled = if (reviewAgain) emptySet() else settledIds()
        var chosen = sort(items.filter { it.id !in settled })
        if (mode == CleanMode.RANDOM) chosen = chosen.shuffled().take(prefs.randomCount)
        val sessionId = db.sessions().insert(SessionEntity(mode = mode, modeKey = key, label = label))
        db.reviews().insertAll(chosen.mapIndexed { i, m ->
            ReviewEntity(sessionId = sessionId, mediaId = m.id, position = i, size = m.size, mimeType = m.mimeType)
        })
        return sessionId
    }

    /** Sessão de duplicatas: já vem com as cópias marcadas para apagar e uma de cada grupo mantida. */
    suspend fun createDuplicateSession(groups: List<List<Media>>, keepIds: Set<Long>): Long {
        val label = context.getString(R.string.mode_duplicates)
        val sessionId = db.sessions().insert(SessionEntity(mode = CleanMode.DUPLICATES, modeKey = "", label = label))
        var pos = 0
        db.reviews().insertAll(groups.flatten().map { m ->
            ReviewEntity(
                sessionId = sessionId, mediaId = m.id, position = pos++, size = m.size, mimeType = m.mimeType,
                decision = if (m.id in keepIds) Decision.KEEP else Decision.DELETE,
            )
        })
        return sessionId
    }

    fun sort(items: List<Media>, option: SortOption = prefs.sortOption): List<Media> = when (option) {
        SortOption.DATE_DESC -> items.sortedByDescending { it.date }
        SortOption.DATE_ASC -> items.sortedBy { it.date }
        SortOption.SIZE_DESC -> items.sortedByDescending { it.size }
        SortOption.SIZE_ASC -> items.sortedBy { it.size }
        SortOption.NAME_ASC -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    suspend fun session(id: Long): SessionEntity? = db.sessions().get(id)

    /** Itens da sessão na ordem dos cartões. Mídias apagadas por fora do app ficam de fora. */
    suspend fun items(sessionId: Long): List<SessionItem> = withContext(Dispatchers.IO) {
        val reviews = db.reviews().forSession(sessionId)
        val media = mediaRepo.loadByIds(reviews.map { it.mediaId })
        reviews.mapNotNull { r -> media[r.mediaId]?.let { SessionItem(it, r) } }
    }

    suspend fun decide(sessionId: Long, mediaId: Long, decision: Decision, movePath: String? = null) =
        db.reviews().decide(sessionId, mediaId, decision, movePath)

    suspend fun decideAllPending(sessionId: Long, decision: Decision) = db.reviews().decideAllPending(sessionId, decision)

    suspend fun counts(sessionId: Long) = db.reviews().counts(sessionId)

    suspend fun saveProgress(sessionId: Long, position: Int, addTimeMs: Long) {
        val s = db.sessions().get(sessionId) ?: return
        db.sessions().update(s.copy(position = position, timeSpentMs = s.timeSpentMs + addTimeMs, updatedAt = System.currentTimeMillis()))
    }

    suspend fun markResult(sessionId: Long, mediaIds: List<Long>, result: ActionResult) {
        mediaIds.chunked(500).forEach { db.reviews().setResult(sessionId, it, result) }
    }

    suspend fun rememberTrashed(items: List<Media>) = db.trash().add(items.map { TrashedEntity(it.id, it.size) })

    suspend fun complete(sessionId: Long, freedBytes: Long) {
        val s = db.sessions().get(sessionId) ?: return
        db.sessions().update(s.copy(status = SessionStatus.COMPLETED, freedBytes = s.freedBytes + freedBytes, updatedAt = System.currentTimeMillis()))
    }

    suspend fun discard(sessionId: Long) = db.sessions().discard(sessionId)

    suspend fun resetDecisions(sessionId: Long) {
        db.reviews().resetDecisions(sessionId)
        saveProgress(sessionId, 0, 0)
    }

    suspend fun sessions(status: SessionStatus) = db.sessions().byStatus(status)

    suspend fun pendingCount() = db.sessions().pendingCount()

    suspend fun stats(): GlobalStats = db.reviews().stats(prefs.statsSince)

    /** Apaga histórico, sessões e ajustes. As fotos e vídeos não são tocados. */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        val onboarding = prefs.onboardingDone
        prefs.clear()
        prefs.onboardingDone = onboarding
        prefs.statsSince = System.currentTimeMillis()
    }
}
