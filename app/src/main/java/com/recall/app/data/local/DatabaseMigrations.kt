package com.recall.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Phase 5 Migration: Adding FTS table and embedding column

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Add embeddingByteArray column to screenshots table safely
        val cursor = database.query("PRAGMA table_info(screenshots)")
        var hasColumn = false
        while (cursor.moveToNext()) {
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex != -1 && cursor.getString(nameIndex) == "embeddingByteArray") {
                hasColumn = true
                break
            }
        }
        cursor.close()

        if (!hasColumn) {
            database.execSQL("ALTER TABLE screenshots ADD COLUMN embeddingByteArray BLOB")
        }
        
        // 2. Create the FTS4 table if not exists
        database.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS `screenshots_fts` USING FTS4(
                `ocrText` TEXT, 
                content=`screenshots`
            )
        """)
        
        // 3. Populate existing data into FTS
        database.execSQL("""
            INSERT INTO screenshots_fts(screenshots_fts, docid, ocrText)
            SELECT 'rebuild', id, ocrText FROM screenshots
        """)
    }
}
