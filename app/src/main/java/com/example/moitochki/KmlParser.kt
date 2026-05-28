package com.example.moitochki

import org.simpleframework.xml.core.Persister
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

class KmlParser {
    private val serializer = Persister()

    /**
     * Парсит KMZ файл и извлекает все метки
     * @param file KMZ файл для парсинга
     * @return Список меток из файла
     * @throws ZipException если файл поврежден или не является ZIP архивом
     * @throws Exception при ошибках парсинга XML
     */
    fun parseKmz(file: File): List<Marker> {
        val markers = mutableListOf<Marker>()
        
        if (!file.exists()) {
            throw IllegalArgumentException("Файл не существует: ${file.absolutePath}")
        }
        
        if (!file.canRead()) {
            throw SecurityException("Нет доступа к файлу: ${file.absolutePath}")
        }
        
        try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".kml", ignoreCase = true)) {
                        // Пропускаем файлы в папках doc.kml, kml/doc.kml и т.д.
                        markers.addAll(parseKml(zis))
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: ZipException) {
            throw ZipException("Неверный формат KMZ файла или файл поврежден: ${e.message}")
        } catch (e: Exception) {
            throw Exception("Ошибка при парсинге KMZ: ${e.message}", e)
        }
        
        return markers
    }

    /**
     * Парсит KML из InputStream
     * @param inputStream Поток с KML данными
     * @return Список распарсенных меток
     */
    fun parseKml(inputStream: InputStream): List<Marker> {
        return try {
            val kml = serializer.read(KmlRoot::class.java, inputStream)
            kml.document?.placemarks?.mapNotNull { placemark ->
                val coords = placemark.point?.coordinates?.split(",") ?: return@mapNotNull null
                if (coords.size >= 2) {
                    val longitude = coords[0].trim().toDoubleOrNull()
                    val latitude = coords[1].trim().toDoubleOrNull()
                    
                    // Валидация координат
                    if (longitude == null || latitude == null) {
                        return@mapNotNull null
                    }
                    
                    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                        return@mapNotNull null
                    }
                    
                    Marker(
                        name = placemark.name?.takeIf { it.isNotBlank() } ?: "Без названия",
                        description = placemark.description ?: "",
                        longitude = longitude,
                        latitude = latitude,
                        folderId = 1 // Default folder for import
                    )
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Парсит KML из строки (альтернативный метод)
     * @param content Строка с KML содержимым
     * @return Список распарсенных меток
     */
    fun parseKmlFromString(content: String): List<Marker> {
        return content.byteInputStream().use { parseKml(it) }
    }
}
