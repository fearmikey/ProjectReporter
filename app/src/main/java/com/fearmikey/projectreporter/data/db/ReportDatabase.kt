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
    version = 3,
    exportSchema = false
)
abstract class ReportDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao
}
