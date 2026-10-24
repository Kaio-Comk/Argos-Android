package com.twinfinder.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun mode(v: CleanMode): String = v.name
    @TypeConverter fun mode(v: String): CleanMode = CleanMode.valueOf(v)
    @TypeConverter fun status(v: SessionStatus): String = v.name
    @TypeConverter fun status(v: String): SessionStatus = SessionStatus.valueOf(v)
    @TypeConverter fun decision(v: Decision): String = v.name
    @TypeConverter fun decision(v: String): Decision = Decision.valueOf(v)
    @TypeConverter fun result(v: ActionResult): String = v.name
    @TypeConverter fun result(v: String): ActionResult = ActionResult.valueOf(v)
}

@Database(
    entities = [
        SessionEntity::class, ReviewEntity::class, ExcludedAlbumEntity::class, MoveTargetEntity::class,
        TrashedEntity::class, AlbumOrderEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2 (23/10/2026): tabela album_order. Migração automática, sem perder sessões.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun reviews(): ReviewDao
    abstract fun albums(): AlbumDao
    abstract fun trash(): TrashDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "twinfinder.db")
                .build().also { instance = it }
        }
    }
}
