package com.example.moitochki

import android.graphics.Color
import org.osmdroid.util.GeoPoint
import java.io.InputStream
import org.simpleframework.xml.core.Persister

/**
 * Расширенный KML парсер с поддержкой LineString и Polygon
 */
class KmlParserExtended {
    private val serializer = Persister()

    /**
     * Парсит KML файл и извлекает метки, линии и полигоны
     */
    fun parseKmlExtended(inputStream: InputStream): KmlParseResult {
        return try {
            val root = serializer.read(KmlRootExtended::class.java, inputStream)
            val markers = mutableListOf<Marker>()
            val paths = mutableListOf<PathData>()
            val polygons = mutableListOf<PolygonData>()

            // Process document placemarks
            root?.document?.placemarks?.forEach { placemark ->
                processPlacemark(placemark, 1L, markers, paths, polygons)
            }

            // Process folders
            root?.document?.folders?.forEach { folder ->
                val folderName = folder.name ?: "Folder"
                // Folder processing would need folder ID management
                folder.placemarks?.forEach { placemark ->
                    processPlacemark(placemark, 1L, markers, paths, polygons)
                }
            }

            KmlParseResult(markers, paths, polygons)
        } catch (e: Exception) {
            e.printStackTrace()
            KmlParseResult(emptyList(), emptyList(), emptyList())
        }
    }

    private fun processPlacemark(
        placemark: KmlPlacemarkExtended,
        folderId: Long,
        markers: MutableList<Marker>,
        paths: MutableList<PathData>,
        polygons: MutableList<PolygonData>
    ) {
        val name = placemark.name?.takeIf { it.isNotBlank() } ?: "Без названия"
        val description = placemark.description ?: ""

        // Parse Point
        placemark.point?.coordinates?.let { coordsStr ->
            parseCoordinates(coordsStr).firstOrNull()?.let { (lon, lat) ->
                if (isValidCoordinate(lat, lon)) {
                    val color = placemark.style?.iconStyle?.color?.let { parseKmlColor(it) } ?: -1
                    markers.add(
                        Marker(
                            name = name,
                            description = description,
                            latitude = lat,
                            longitude = lon,
                            folderId = folderId,
                            color = color
                        )
                    )
                }
            }
        }

        // Parse LineString
        placemark.lineString?.coordinates?.let { coordsStr ->
            val points = parseCoordinates(coordsStr)
            if (points.size >= 2) {
                val validPoints = points.filter { isValidCoordinate(it.second, it.first) }
                if (validPoints.size >= 2) {
                    val color = placemark.style?.lineStyle?.color?.let { parseKmlColor(it) } ?: -1
                    val width = placemark.style?.lineStyle?.width ?: 3f
                    paths.add(
                        PathData(
                            name = name,
                            description = description,
                            points = validPoints.map { Pair(it.second, it.first) }, // lat, lon
                            color = color,
                            width = width,
                            folderId = folderId
                        )
                    )
                }
            }
        }

        // Parse Polygon
        placemark.polygon?.outerBoundary?.linearRing?.coordinates?.let { coordsStr ->
            val outerPoints = parseCoordinates(coordsStr)
            if (outerPoints.size >= 3) {
                val validOuter = outerPoints.filter { isValidCoordinate(it.second, it.first) }
                if (validOuter.size >= 3) {
                    val innerRings = mutableListOf<List<Pair<Double, Double>>>()
                    placemark.polygon.innerBoundaries?.forEach { boundary ->
                        boundary.linearRing?.coordinates?.let { innerCoords ->
                            val innerPoints = parseCoordinates(innerCoords)
                                .filter { isValidCoordinate(it.second, it.first) }
                                .map { Pair(it.second, it.first) }
                            if (innerPoints.size >= 3) {
                                innerRings.add(innerPoints)
                            }
                        }
                    }

                    val fillColor = placemark.style?.polyStyle?.color?.let { 
                        parseKmlColor(it, isPolyStyle = true) 
                    } ?: 0x40FF0000
                    
                    val strokeColor = placemark.style?.polyStyle?.outline?.let { 
                        if (it) placemark.style?.lineStyle?.color?.let { parseKmlColor(it) } ?: -1 else Color.TRANSPARENT 
                    } ?: -1

                    polygons.add(
                        PolygonData(
                            name = name,
                            description = description,
                            outerPoints = validOuter.map { Pair(it.second, it.first) },
                            innerPoints = innerRings,
                            fillColor = fillColor,
                            strokeColor = strokeColor,
                            folderId = folderId
                        )
                    )
                }
            }
        }
    }

    /**
     * Парсит строку координат KML в список пар (longitude, latitude)
     */
    private fun parseCoordinates(coordsStr: String): List<Pair<Double, Double>> {
        return coordsStr.trim().split("\\s+".toRegex()).mapNotNull { coord ->
            val parts = coord.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].trim().toDoubleOrNull()
                val lat = parts[1].trim().toDoubleOrNull()
                if (lon != null && lat != null) {
                    Pair(lon, lat)
                } else null
            } else null
        }
    }

    /**
     * Проверяет валидность координат
     */
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /**
     * Парсит цвет из формата KML (AABBGGRR) в Android Color (AARRGGBB)
     */
    fun parseKmlColor(kmlColor: String, isPolyStyle: Boolean = false): Int {
        return try {
            if (kmlColor.length == 8) {
                val a = kmlColor.substring(0, 2).toInt(16)
                val b = kmlColor.substring(2, 4).toInt(16)
                val g = kmlColor.substring(4, 6).toInt(16)
                val r = kmlColor.substring(6, 8).toInt(16)
                
                if (isPolyStyle) {
                    // Для полигонов KML использует прозрачность как alpha
                    Color.argb(a, r, g, b)
                } else {
                    Color.argb(a, r, g, b)
                }
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }
}

/**
 * Результат парсинга KML файла
 */
data class KmlParseResult(
    val markers: List<Marker>,
    val paths: List<PathData>,
    val polygons: List<PolygonData>
)
