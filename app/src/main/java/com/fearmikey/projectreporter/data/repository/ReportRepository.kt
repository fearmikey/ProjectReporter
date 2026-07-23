package com.fearmikey.projectreporter.data.repository

import com.fearmikey.projectreporter.data.dao.ReportDao
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ReportRepository {
    fun getAllProjects(): Flow<List<ProjectEntity>>
    suspend fun getProjectById(projectId: String): ProjectEntity?
    suspend fun insertProject(project: ProjectEntity)
    suspend fun softDeleteProject(project: ProjectEntity)
    suspend fun restoreProject(project: ProjectEntity)
    suspend fun deleteProjectPermanently(project: ProjectEntity)
    
    fun getPhotosForProject(projectId: String): Flow<List<PhotoEntity>>
    suspend fun insertPhoto(photo: PhotoEntity)
    suspend fun updatePhoto(photo: PhotoEntity)
    suspend fun softDeletePhoto(photo: PhotoEntity)
    suspend fun restorePhoto(photo: PhotoEntity)
    suspend fun deletePhotoPermanently(photo: PhotoEntity)
    
    // Notes
    fun getNotesForProject(projectId: String): Flow<List<NoteEntity>>
    suspend fun insertNote(note: NoteEntity)
    suspend fun updateNote(note: NoteEntity)
    suspend fun softDeleteNote(note: NoteEntity)
    suspend fun restoreNote(note: NoteEntity)
    suspend fun deleteNotePermanently(note: NoteEntity)
    
    // Profile
    suspend fun getProfile(): ProfileEntity?
    fun getProfileFlow(): Flow<ProfileEntity?>
    suspend fun insertProfile(profile: ProfileEntity)

    // Recycle Bin
    fun getDeletedPhotos(): Flow<List<PhotoEntity>>
    fun getDeletedNotes(): Flow<List<NoteEntity>>
    fun getDeletedProjects(): Flow<List<ProjectEntity>>
    suspend fun purgeOldDeletedItems(threshold: Long)
}

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao
) : ReportRepository {
    override fun getAllProjects(): Flow<List<ProjectEntity>> = reportDao.getAllProjects()

    override suspend fun getProjectById(projectId: String): ProjectEntity? =
        reportDao.getProjectById(projectId)

    override suspend fun insertProject(project: ProjectEntity) =
        reportDao.insertProject(project)

    override suspend fun softDeleteProject(project: ProjectEntity) =
        reportDao.updateProject(project.copy(isDeleted = true, deletedTimestamp = System.currentTimeMillis()))

    override suspend fun restoreProject(project: ProjectEntity) =
        reportDao.updateProject(project.copy(isDeleted = false, deletedTimestamp = null))

    override suspend fun deleteProjectPermanently(project: ProjectEntity) =
        reportDao.deleteProjectPermanently(project)

    override fun getPhotosForProject(projectId: String): Flow<List<PhotoEntity>> =
        reportDao.getPhotosForProject(projectId)

    override suspend fun insertPhoto(photo: PhotoEntity) =
        reportDao.insertPhoto(photo)

    override suspend fun updatePhoto(photo: PhotoEntity) =
        reportDao.updatePhoto(photo)

    override suspend fun softDeletePhoto(photo: PhotoEntity) =
        reportDao.updatePhoto(photo.copy(isDeleted = true, deletedTimestamp = System.currentTimeMillis()))

    override suspend fun restorePhoto(photo: PhotoEntity) =
        reportDao.updatePhoto(photo.copy(isDeleted = false, deletedTimestamp = null))

    override suspend fun deletePhotoPermanently(photo: PhotoEntity) =
        reportDao.deletePhotoPermanently(photo)

    override fun getNotesForProject(projectId: String): Flow<List<NoteEntity>> =
        reportDao.getNotesForProject(projectId)

    override suspend fun insertNote(note: NoteEntity) =
        reportDao.insertNote(note)

    override suspend fun updateNote(note: NoteEntity) =
        reportDao.updateNote(note)

    override suspend fun softDeleteNote(note: NoteEntity) =
        reportDao.updateNote(note.copy(isDeleted = true, deletedTimestamp = System.currentTimeMillis()))

    override suspend fun restoreNote(note: NoteEntity) =
        reportDao.updateNote(note.copy(isDeleted = false, deletedTimestamp = null))

    override suspend fun deleteNotePermanently(note: NoteEntity) =
        reportDao.deleteNotePermanently(note)

    override suspend fun getProfile(): ProfileEntity? =
        reportDao.getProfile()

    override fun getProfileFlow(): Flow<ProfileEntity?> =
        reportDao.getProfileFlow()

    override suspend fun insertProfile(profile: ProfileEntity) =
        reportDao.insertProfile(profile)

    override fun getDeletedPhotos(): Flow<List<PhotoEntity>> =
        reportDao.getDeletedPhotos()

    override fun getDeletedNotes(): Flow<List<NoteEntity>> =
        reportDao.getDeletedNotes()

    override fun getDeletedProjects(): Flow<List<ProjectEntity>> =
        reportDao.getDeletedProjects()

    override suspend fun purgeOldDeletedItems(threshold: Long) {
        reportDao.purgeDeletedPhotos(threshold)
        reportDao.purgeDeletedNotes(threshold)
        reportDao.purgeDeletedProjects(threshold)
    }
}
