package com.fearmikey.projectreporter.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fearmikey.projectreporter.data.dao.ReportDao
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        PhotoEntity::class,
        NoteEntity::class,
        ProfileEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class ReportDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add new columns to projects table
                db.execSQL("ALTER TABLE projects ADD COLUMN clientCompany TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN poc TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN phoneNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                // Add new columns to profile table
                db.execSQL("ALTER TABLE profile ADD COLUMN company TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN role TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN logoUri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN engineerPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profile ADD COLUMN engineerPhone TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
