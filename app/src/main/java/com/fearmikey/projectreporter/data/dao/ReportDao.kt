package com.fearmikey.projectreporter.data.dao

import androidx.room.*
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoEntity)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE projectId = :projectId AND isDeleted = 0 ORDER BY photoId ASC")
    fun getPhotosForProject(projectId: String): Flow<List<PhotoEntity>>

    @Delete
    suspend fun deletePhotoPermanently(photo: PhotoEntity)

    // Notes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE projectId = :projectId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getNotesForProject(projectId: String): Flow<List<NoteEntity>>

    @Delete
    suspend fun deleteNotePermanently(note: NoteEntity)

    // Profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun getProfile(): ProfileEntity?

    @Query("SELECT * FROM profile WHERE id = 0")
    fun getProfileFlow(): Flow<ProfileEntity?>

    // Recycle Bin Queries
    @Query("SELECT * FROM photos WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM projects WHERE isDeleted = 1 ORDER BY deletedTimestamp DESC")
    fun getDeletedProjects(): Flow<List<ProjectEntity>>

    @Query("DELETE FROM photos WHERE isDeleted = 1 AND deletedTimestamp < :threshold")
    suspend fun purgeDeletedPhotos(threshold: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND deletedTimestamp < :threshold")
    suspend fun purgeDeletedNotes(threshold: Long)

    @Query("DELETE FROM projects WHERE isDeleted = 1 AND deletedTimestamp < :threshold")
    suspend fun purgeDeletedProjects(threshold: Long)
    
    @Delete
    suspend fun deleteProjectPermanently(project: ProjectEntity)
}
