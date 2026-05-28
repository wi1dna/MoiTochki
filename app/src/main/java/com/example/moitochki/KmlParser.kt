package com.example.moitochki

import org.simpleframework.xml.core.Persister
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class KmlParser {
    private val serializer = Persister()

    fun parseKmz(file: File): List<Marker> {
        val markers = mutableListOf<Marker>()
        ZipInputStream(FileInputStream(file)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", ignoreCase = true)) {
                    markers.addAll(parseKml(zis))
                }
                entry = zis.nextEntry
            }
        }
        return markers
    }

    fun parseKml(inputStream: InputStream): List<Marker> {
        return try {
            val kml = serializer.read(KmlRoot::class.java, inputStream)
            kml.document?.placemarks?.mapNotNull { placemark ->
                val coords = placemark.point?.coordinates?.split(",") ?: return@mapNotNull null
                if (coords.size >= 2) {
                    Marker(
                        name = placemark.name ?: "Unnamed",
                        description = placemark.description ?: "",
                        longitude = coords[0].trim().toDouble(),
                        latitude = coords[1].trim().toDouble(),
                        folderId = 1 // Default folder for import
                    )
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
