package com.spendingapp.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE budgets ADD COLUMN lastWarningLevel TEXT")
            db.execSQL("ALTER TABLE budgets ADD COLUMN lastWarningSpentAmount INTEGER")
            db.execSQL("ALTER TABLE budgets ADD COLUMN lastWarningAt INTEGER")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN linkedGoalId INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_linkedGoalId ON transactions(linkedGoalId)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS domain_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    feature TEXT NOT NULL,
                    aggregateType TEXT NOT NULL,
                    aggregateId INTEGER,
                    actionId TEXT,
                    payloadJson TEXT,
                    createdAt INTEGER NOT NULL,
                    dispatchedAt INTEGER,
                    dispatchAttempts INTEGER NOT NULL,
                    lastDispatchError TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_events_type ON domain_events(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_events_feature ON domain_events(feature)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_events_aggregateType_aggregateId ON domain_events(aggregateType, aggregateId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_domain_events_dispatchedAt ON domain_events(dispatchedAt)")
        }
    }
}
