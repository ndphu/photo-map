package com.photomap.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE local_assets ADD COLUMN uploadSessionId TEXT")
        database.execSQL(
            "ALTER TABLE local_assets ADD COLUMN uploadAttemptCount INTEGER NOT NULL DEFAULT 0",
        )
        database.execSQL("ALTER TABLE local_assets ADD COLUMN nextRetryAt INTEGER")
    }
}
