package com.fearmikey.projectreporter.util

import android.content.Context
import android.net.Uri
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import com.fearmikey.projectreporter.data.repository.ReportRepository
import com.fearmikey.projectreporter.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupImportResult(
    val projectsCount: Int,
    val photosCount: Int,
    val notesCount: Int
)

@Singleton
class BackupService @Inject constructor(
    private val reportRepository: ReportRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) {

    suspend fun exportBackup(outputStream: OutputStream): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))

            val activeProjects = reportRepository.getAllProjects().first()
            val deletedProjects = reportRepository.getDeletedProjects().first()
            val allProjects = (activeProjects + deletedProjects).distinctBy { it.projectId }

            val allPhotos = mutableListOf<PhotoEntity>()
            val allNotes = mutableListOf<NoteEntity>()

            for (project in allProjects) {
                val photos = reportRepository.getPhotosForProject(project.projectId).first()
                val deletedPhotos = reportRepository.getDeletedPhotosForProject(project.projectId).first()
                allPhotos.addAll((photos + deletedPhotos).distinctBy { it.photoId })

                val notes = reportRepository.getNotesForProject(project.projectId).first()
                val deletedNotes = reportRepository.getDeletedNotesForProject(project.projectId).first()
                allNotes.addAll((notes + deletedNotes).distinctBy { it.noteId })
            }

            val profile = reportRepository.getProfile()
            val settings = settingsRepository.themeSettings.first()

            val photoZipNameMap = mutableMapOf<Long, String>()
            for (photo in allPhotos) {
                val zipFileName = "photos/photo_${photo.photoId}_${photo.timestamp}.jpg"
                val photoStream = openInputStreamForUri(photo.imageUri)
                if (photoStream != null) {
                    try {
                        zipOut.putNextEntry(ZipEntry(zipFileName))
                        photoStream.copyTo(zipOut)
                        zipOut.closeEntry()
                        photoZipNameMap[photo.photoId] = zipFileName
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        runCatching { photoStream.close() }
                    }
                }
            }

            // Export company logo if present
            var hasLogo = false
            val logoFile = File(context.filesDir, "company_logo.png")
            if (logoFile.exists()) {
                try {
                    zipOut.putNextEntry(ZipEntry("company_logo.png"))
                    logoFile.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                    hasLogo = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val rootJson = JSONObject().apply {
                put("version", 1)
                put("timestamp", System.currentTimeMillis())

                if (profile != null) {
                    put("profile", JSONObject().apply {
                        put("engineerName", profile.engineerName)
                        put("engineerPhone", profile.engineerPhone)
                        put("company", profile.company)
                        put("role", profile.role)
                    })
                }

                put("settings", JSONObject().apply {
                    put("appTheme", settings.appTheme.name)
                    put("useDynamicColor", settings.useDynamicColor)
                    put("amoledMode", settings.amoledMode)
                    put("highContrast", settings.highContrast)
                    put("colorSchemeOption", settings.colorSchemeOption.name)
                    put("defaultFlashMode", settings.defaultFlashMode.name)
                    put("watermarkTimestamp", settings.watermarkTimestamp)
                    put("watermarkGps", settings.watermarkGps)
                    put("watermarkProjectDetails", settings.watermarkProjectDetails)
                    put("hasCompanyLogo", hasLogo)
                    if (settings.logoPrimaryColor != null) put("logoPrimaryColor", settings.logoPrimaryColor)
                    if (settings.logoSecondaryColor != null) put("logoSecondaryColor", settings.logoSecondaryColor)
                })

                val projectsArray = JSONArray()
                for (p in allProjects) {
                    projectsArray.put(JSONObject().apply {
                        put("projectId", p.projectId)
                        put("projectName", p.projectName)
                        put("timestamp", p.timestamp)
                        put("engineerName", p.engineerName)
                        put("engineerPhone", p.engineerPhone)
                        put("clientCompany", p.clientCompany)
                        put("poc", p.poc)
                        put("phoneNumber", p.phoneNumber)
                        put("notes", p.notes)
                        put("isDeleted", p.isDeleted)
                        if (p.deletedTimestamp != null) put("deletedTimestamp", p.deletedTimestamp)
                    })
                }
                put("projects", projectsArray)

                val photosArray = JSONArray()
                for (ph in allPhotos) {
                    photosArray.put(JSONObject().apply {
                        put("photoId", ph.photoId)
                        put("projectId", ph.projectId)
                        put("timestampOverlay", ph.timestampOverlay)
                        put("timestamp", ph.timestamp)
                        put("annotation", ph.annotation)
                        put("isDeleted", ph.isDeleted)
                        if (ph.deletedTimestamp != null) put("deletedTimestamp", ph.deletedTimestamp)
                        photoZipNameMap[ph.photoId]?.let { put("zipFileName", it) }
                    })
                }
                put("photos", photosArray)

                val notesArray = JSONArray()
                for (n in allNotes) {
                    notesArray.put(JSONObject().apply {
                        put("noteId", n.noteId)
                        put("projectId", n.projectId)
                        put("content", n.content)
                        put("timestamp", n.timestamp)
                        put("isDeleted", n.isDeleted)
                        if (n.deletedTimestamp != null) put("deletedTimestamp", n.deletedTimestamp)
                    })
                }
                put("notes", notesArray)
            }

            zipOut.putNextEntry(ZipEntry("backup_data.json"))
            zipOut.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.finish()
            zipOut.flush()

            allProjects.size
        }
    }

    suspend fun importBackup(inputStream: InputStream): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val outputDirectory = context.getExternalFilesDir(null) ?: context.filesDir
            var jsonContent: String? = null
            val restoredPhotosMap = mutableMapOf<String, String>() // zipFileName -> localUriString

            val bufferedInput = BufferedInputStream(inputStream)
            bufferedInput.mark(1024 * 1024)

            // Try reading as ZIP archive
            try {
                val zipIn = ZipInputStream(bufferedInput)
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName == "backup_data.json") {
                        val reader = BufferedReader(InputStreamReader(zipIn, Charsets.UTF_8))
                        jsonContent = reader.readText()
                    } else if (entryName == "company_logo.png") {
                        val logoFile = File(context.filesDir, "company_logo.png")
                        FileOutputStream(logoFile).use { out ->
                            zipIn.copyTo(out)
                        }
                    } else if (entryName.startsWith("photos/")) {
                        val fileName = entryName.substringAfterLast('/')
                        if (fileName.isNotBlank()) {
                            val restoredFile = File(outputDirectory, "restored_${System.currentTimeMillis()}_$fileName")
                            FileOutputStream(restoredFile).use { out ->
                                zipIn.copyTo(out)
                            }
                            restoredPhotosMap[entryName] = Uri.fromFile(restoredFile).toString()
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            } catch (e: Exception) {
                // Not a valid ZIP stream, reset stream and try reading directly as plain JSON
                e.printStackTrace()
            }

            // Fallback: If not extracted from ZIP, read raw JSON from input
            if (jsonContent == null) {
                bufferedInput.reset()
                val reader = BufferedReader(InputStreamReader(bufferedInput, Charsets.UTF_8))
                jsonContent = reader.readText()
            }

            val rootJson = JSONObject(jsonContent)

            // 1. Profile
            if (rootJson.has("profile")) {
                val profileObj = rootJson.getJSONObject("profile")
                val engineerName = profileObj.optString("engineerName", "")
                val engineerPhone = profileObj.optString("engineerPhone", "")
                val company = profileObj.optString("company", "")
                val role = profileObj.optString("role", "")
                if (engineerName.isNotBlank()) {
                    reportRepository.insertProfile(
                        ProfileEntity(
                            id = 0,
                            engineerName = engineerName,
                            engineerPhone = engineerPhone,
                            company = company,
                            role = role
                        )
                    )
                }
            }

            // 2. Settings
            if (rootJson.has("settings")) {
                val settingsObj = rootJson.getJSONObject("settings")
                runCatching {
                    val themeName = settingsObj.optString("appTheme", AppTheme.DARK.name)
                    settingsRepository.setAppTheme(AppTheme.valueOf(themeName))
                }
                if (settingsObj.has("useDynamicColor")) {
                    settingsRepository.setDynamicColor(settingsObj.getBoolean("useDynamicColor"))
                }
                if (settingsObj.has("amoledMode")) {
                    settingsRepository.setAmoledMode(settingsObj.getBoolean("amoledMode"))
                }
                if (settingsObj.has("highContrast")) {
                    settingsRepository.setHighContrast(settingsObj.getBoolean("highContrast"))
                }
                runCatching {
                    val schemeName = settingsObj.optString("colorSchemeOption", ColorSchemeOption.INDUSTRIAL.name)
                    settingsRepository.setColorScheme(ColorSchemeOption.valueOf(schemeName))
                }
                runCatching {
                    val flashName = settingsObj.optString("defaultFlashMode", FlashModeOption.OFF.name)
                    settingsRepository.setDefaultFlashMode(FlashModeOption.valueOf(flashName))
                }
                if (settingsObj.has("watermarkTimestamp")) {
                    settingsRepository.setWatermarkTimestamp(settingsObj.getBoolean("watermarkTimestamp"))
                }
                if (settingsObj.has("watermarkGps")) {
                    settingsRepository.setWatermarkGps(settingsObj.getBoolean("watermarkGps"))
                }
                if (settingsObj.has("watermarkProjectDetails")) {
                    settingsRepository.setWatermarkProjectDetails(settingsObj.getBoolean("watermarkProjectDetails"))
                }

                if (settingsObj.optBoolean("hasCompanyLogo", false)) {
                    val logoFile = File(context.filesDir, "company_logo.png")
                    if (logoFile.exists()) {
                        val logoUri = Uri.fromFile(logoFile).toString()
                        val primaryColor = if (settingsObj.has("logoPrimaryColor")) settingsObj.getInt("logoPrimaryColor") else null
                        val secondaryColor = if (settingsObj.has("logoSecondaryColor")) settingsObj.getInt("logoSecondaryColor") else null
                        settingsRepository.setCompanyLogo(logoUri, primaryColor, secondaryColor)
                    }
                }
            }

            // 3. Projects
            var projectsImported = 0
            if (rootJson.has("projects")) {
                val projectsArray = rootJson.getJSONArray("projects")
                for (i in 0 until projectsArray.length()) {
                    val pObj = projectsArray.getJSONObject(i)
                    val project = ProjectEntity(
                        projectId = pObj.getString("projectId"),
                        projectName = pObj.getString("projectName"),
                        timestamp = pObj.getLong("timestamp"),
                        engineerName = pObj.optString("engineerName", ""),
                        engineerPhone = pObj.optString("engineerPhone", ""),
                        clientCompany = pObj.optString("clientCompany", ""),
                        poc = pObj.optString("poc", ""),
                        phoneNumber = pObj.optString("phoneNumber", ""),
                        notes = pObj.optString("notes", ""),
                        isDeleted = pObj.optBoolean("isDeleted", false),
                        deletedTimestamp = if (pObj.has("deletedTimestamp") && !pObj.isNull("deletedTimestamp")) pObj.getLong("deletedTimestamp") else null
                    )
                    reportRepository.insertProject(project)
                    projectsImported++
                }
            }

            // 4. Photos
            var photosImported = 0
            if (rootJson.has("photos")) {
                val photosArray = rootJson.getJSONArray("photos")
                for (i in 0 until photosArray.length()) {
                    val phObj = photosArray.getJSONObject(i)
                    val zipFileName = phObj.optString("zipFileName", "")
                    val imageUri = restoredPhotosMap[zipFileName]
                        ?: phObj.optString("imageUri", "")

                    if (imageUri.isNotBlank()) {
                        val photo = PhotoEntity(
                            photoId = phObj.optLong("photoId", 0L),
                            projectId = phObj.getString("projectId"),
                            imageUri = imageUri,
                            timestampOverlay = phObj.optString("timestampOverlay", ""),
                            timestamp = phObj.optLong("timestamp", System.currentTimeMillis()),
                            annotation = phObj.optString("annotation", ""),
                            isDeleted = phObj.optBoolean("isDeleted", false),
                            deletedTimestamp = if (phObj.has("deletedTimestamp") && !phObj.isNull("deletedTimestamp")) phObj.getLong("deletedTimestamp") else null
                        )
                        reportRepository.insertPhoto(photo)
                        photosImported++
                    }
                }
            }

            // 5. Notes
            var notesImported = 0
            if (rootJson.has("notes")) {
                val notesArray = rootJson.getJSONArray("notes")
                for (i in 0 until notesArray.length()) {
                    val nObj = notesArray.getJSONObject(i)
                    val note = NoteEntity(
                        noteId = nObj.optLong("noteId", 0L),
                        projectId = nObj.getString("projectId"),
                        content = nObj.getString("content"),
                        timestamp = nObj.optLong("timestamp", System.currentTimeMillis()),
                        isDeleted = nObj.optBoolean("isDeleted", false),
                        deletedTimestamp = if (nObj.has("deletedTimestamp") && !nObj.isNull("deletedTimestamp")) nObj.getLong("deletedTimestamp") else null
                    )
                    reportRepository.insertNote(note)
                    notesImported++
                }
            }

            BackupImportResult(
                projectsCount = projectsImported,
                photosCount = photosImported,
                notesCount = notesImported
            )
        }
    }

    private fun openInputStreamForUri(uriString: String): InputStream? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)
                ?: if (uri.scheme == "file" || uri.scheme == null) {
                    val file = File(uri.path ?: uriString)
                    if (file.exists()) file.inputStream() else null
                } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
