package com.fearmikey.projectreporter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val projectId: String,
    val projectName: String,
    val timestamp: Long,
    val engineerName: String,
    val isDeleted: Boolean = false,
    val deletedTimestamp: Long? = null
)
