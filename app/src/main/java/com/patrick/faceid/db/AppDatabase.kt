package com.patrick.faceid.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local SQLite store. Persists across app restarts, which is an explicit Phase 1 requirement.
 *
 * Phase 1 has no migrations: version 1 is the first schema. The schema is exported to
 * app/schemas/ so later versions have a baseline to migrate from.
 */
@Database(
    entities = [PersonEntity::class, EmbeddingEntity::class, AuditEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val NAME = "faceid.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // WAL: safer against interrupted writes, which matters because a crash during
                // registration must not leave a person without embeddings.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        /** Tests only: an isolated in-memory database. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }
}
