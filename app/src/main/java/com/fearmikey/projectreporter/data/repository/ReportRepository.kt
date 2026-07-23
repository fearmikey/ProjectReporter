package com.fearmikey.projectreporter.data.repository

import com.fearmikey.projectreporter.data.dao.ReportDao
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ReportRepository {
    fun getAllProjects(): Flow<List<ProjectEntity>>
    suspend fun getProjectById(projectId: String): ProjectEntity?
    suspend fun insertProject(project: ProjectEntity)
    fun getPhotosForProject(projectId: String): Flow<List<PhotoEntity>>
    suspend fun insertPhoto(photo: PhotoEntity)
    suspend fun updatePhoto(photo: PhotoEntity)
    suspend fun deletePhoto(photo: PhotoEntity)
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

    override fun getPhotosForProject(projectId: String): Flow<List<PhotoEntity>> =
        reportDao.getPhotosForProject(projectId)

    override suspend fun insertPhoto(photo: PhotoEntity) =
        reportDao.insertPhoto(photo)

    override suspend fun updatePhoto(photo: PhotoEntity) =
        reportDao.updatePhoto(photo)

    override suspend fun deletePhoto(photo: PhotoEntity) =
        reportDao.deletePhoto(photo)
}
