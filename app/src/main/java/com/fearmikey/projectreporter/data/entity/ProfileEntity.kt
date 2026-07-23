package com.fearmikey.projectreporter.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey
    val id: Int = 0, // Singleton profile
    val engineerName: String
)
