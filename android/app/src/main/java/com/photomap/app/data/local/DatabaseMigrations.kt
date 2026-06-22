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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS remote_assets (
                id TEXT NOT NULL PRIMARY KEY,
                media_type TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                original_filename TEXT,
                file_size_bytes INTEGER,
                checksum_sha256 TEXT,
                thumbnail_key TEXT,
                preview_key TEXT,
                poster_frame_key TEXT,
                thumbnail_url TEXT,
                preview_url TEXT,
                poster_frame_url TEXT,
                signed_url_updated_at INTEGER,
                taken_at TEXT,
                width INTEGER,
                height INTEGER,
                duration_ms INTEGER,
                orientation INTEGER,
                latitude REAL,
                longitude REAL,
                country TEXT,
                region TEXT,
                city TEXT,
                place_name TEXT,
                camera_make TEXT,
                camera_model TEXT,
                is_favorite INTEGER NOT NULL,
                is_archived INTEGER NOT NULL,
                is_trashed INTEGER NOT NULL,
                uploaded_at TEXT,
                updated_at TEXT,
                local_cached_at INTEGER NOT NULL,
                updated_from_server_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS remote_sync_state (
                id TEXT NOT NULL PRIMARY KEY,
                last_change_cursor INTEGER NOT NULL,
                last_synced_at INTEGER,
                last_error TEXT,
                is_initial_sync_completed INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS index_remote_assets_taken_at_id ON remote_assets(taken_at, id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_remote_assets_media_type_taken_at ON remote_assets(media_type, taken_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_remote_assets_is_favorite_taken_at ON remote_assets(is_favorite, taken_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_remote_assets_is_archived_taken_at ON remote_assets(is_archived, taken_at)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_remote_assets_is_trashed_taken_at ON remote_assets(is_trashed, taken_at)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS remote_asset_pending_ops (
                op_id TEXT NOT NULL PRIMARY KEY,
                asset_id TEXT NOT NULL,
                op_type TEXT NOT NULL,
                payload_json TEXT,
                status TEXT NOT NULL,
                attempt_count INTEGER NOT NULL,
                next_retry_at INTEGER,
                last_error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_remote_asset_pending_ops_status_next_retry_at " +
                "ON remote_asset_pending_ops(status, next_retry_at)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_remote_asset_pending_ops_asset_id " +
                "ON remote_asset_pending_ops(asset_id)",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE remote_assets ADD COLUMN signed_url_expires_at INTEGER")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE local_assets ADD COLUMN metadataBackfillStatus TEXT NOT NULL DEFAULT 'pending'")
        database.execSQL("ALTER TABLE local_assets ADD COLUMN metadataBackfilledAt INTEGER")
        database.execSQL("ALTER TABLE local_assets ADD COLUMN metadataBackfillError TEXT")
        database.execSQL("ALTER TABLE remote_assets ADD COLUMN taken_at_source TEXT")
        database.execSQL("ALTER TABLE remote_assets ADD COLUMN timezone_offset_minutes INTEGER")
        database.execSQL("ALTER TABLE remote_assets ADD COLUMN software TEXT")
    }
}
