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
}
