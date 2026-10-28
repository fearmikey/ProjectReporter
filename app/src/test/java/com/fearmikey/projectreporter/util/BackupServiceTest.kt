package com.fearmikey.projectreporter.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupServiceTest {

    @Test
    fun testZipArchiveStructure() {
        val outputStream = ByteArrayOutputStream()
        val zipOut = ZipOutputStream(outputStream)

        val rootJson = JSONObject().apply {
            put("version", 1)
            put("timestamp", System.currentTimeMillis())

            val projectsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("projectId", "PROJ-101")
                    put("projectName", "Bridge Test")
                    put("timestamp", 1700000000000L)
                    put("engineerName", "Engineer Bob")
                })
            }
            put("projects", projectsArray)
        }

        zipOut.putNextEntry(ZipEntry("backup_data.json"))
        zipOut.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()

        zipOut.finish()

        val zipIn = ZipInputStream(ByteArrayInputStream(outputStream.toByteArray()))
        var entry = zipIn.nextEntry
        var foundJson = false
        var jsonText = ""

        while (entry != null) {
            if (entry.name == "backup_data.json") {
                foundJson = true
                jsonText = zipIn.bufferedReader().readText()
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        assertTrue(foundJson)
        val parsedJson = JSONObject(jsonText)
        assertEquals(1, parsedJson.getInt("version"))
        assertEquals("PROJ-101", parsedJson.getJSONArray("projects").getJSONObject(0).getString("projectId"))
    }
}
