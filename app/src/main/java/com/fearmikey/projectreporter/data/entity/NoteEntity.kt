package com.fearmikey.projectreporter.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val noteId: Long = 0,
    val projectId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long? = null
)
