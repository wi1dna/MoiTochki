package com.example.moitochki.gpx

import org.simpleframework.xml.*
import java.io.InputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPX Parser для импорта и экспорта треков и путевых точек
 */
class GpxParser {
    
    private val serializer = org.simpleframework.xml.core.Persister()
    
    /**
     * Парсит GPX файл и извлекает waypoints, tracks и routes
     */
    fun parseGpx(inputStream: InputStream): GpxData {
        return try {
            val gpx = serializer.read(Gpx::class.java, inputStream)
            val waypoints = mutableListOf<Waypoint>()
            val tracks = mutableListOf<Track>()
            val routes = mutableListOf<Route>()
            
            // Waypoints
            gpx.waypoints?.forEach { wpt ->
                wpt.lat?.toDoubleOrNull()?.let { lat ->
                    wpt.lon?.toDoubleOrNull()?.let { lon ->
                        if (isValidCoordinate(lat, lon)) {
                            waypoints.add(
                                Waypoint(
                                    name = wpt.name ?: "WP",
                                    description = wpt.desc ?: "",
                                    latitude = lat,
                                    longitude = lon,
                                    elevation = wpt.ele?.toDoubleOrNull(),
                                    time = wpt.time
                                )
                            )
                        }
                    }
                }
            }
            
            // Tracks
            gpx.tracks?.forEach { trk ->
                val segments = mutableListOf<List<TrackPoint>>()
                trk.segments?.forEach { seg ->
                    val points = seg.points?.mapNotNull { trkpt ->
                        trkpt.lat?.toDoubleOrNull()?.let { lat ->
                            trkpt.lon?.toDoubleOrNull()?.let { lon ->
                                if (isValidCoordinate(lat, lon)) {
                                    TrackPoint(
                                        latitude = lat,
                                        longitude = lon,
                                        elevation = trkpt.ele?.toDoubleOrNull(),
                                        time = trkpt.time
                                    )
                                } else null
                            }
                        }
                    } ?: emptyList()
                    
                    if (points.isNotEmpty()) {
                        segments.add(points)
                    }
                }
                
                if (segments.isNotEmpty()) {
                    tracks.add(
                        Track(
                            name = trk.name ?: "Track",
                            description = trk.desc ?: "",
                            segments = segments
                        )
                    )
                }
            }
            
            // Routes
            gpx.routes?.forEach { rte ->
                val points = rte.points?.mapNotNull { rtept ->
                    rtept.lat?.toDoubleOrNull()?.let { lat ->
                        rtept.lon?.toDoubleOrNull()?.let { lon ->
                            if (isValidCoordinate(lat, lon)) {
                                TrackPoint(
                                    latitude = lat,
                                    longitude = lon,
                                    elevation = rtept.ele?.toDoubleOrNull(),
                                    time = rtept.time
                                )
                            } else null
                        }
                    }
                } ?: emptyList()
                
                if (points.isNotEmpty()) {
                    routes.add(
                        Route(
                            name = rte.name ?: "Route",
                            description = rte.desc ?: "",
                            points = points
                        )
                    )
                }
            }
            
            GpxData(waypoints, tracks, routes)
        } catch (e: Exception) {
            e.printStackTrace()
            GpxData(emptyList(), emptyList(), emptyList())
        }
    }
    
    /**
     * Экспортирует данные в GPX формат
     */
    fun exportGpx(data: GpxExportData): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<gpx version=\"1.1\" creator=\"MoiTochki\" ")
        builder.append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        builder.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        builder.append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        
        // Metadata
        builder.append("  <metadata>\n")
        builder.append("    <name>${escapeXml(data.name)}</name>\n")
        builder.append("    <time>${getCurrentTime()}</time>\n")
        builder.append("  </metadata>\n")
        
        // Waypoints
        data.waypoints.forEach { wpt ->
            builder.append("  <wpt lat=\"${wpt.latitude}\" lon=\"${wpt.longitude}\">\n")
            builder.append("    <name>${escapeXml(wpt.name)}</name>\n")
            if (wpt.description.isNotBlank()) {
                builder.append("    <desc>${escapeXml(wpt.description)}</desc>\n")
            }
            wpt.elevation?.let { ele ->
                builder.append("    <ele>$ele</ele>\n")
            }
            builder.append("  </wpt>\n")
        }
        
        // Tracks
        data.tracks.forEach { track ->
            builder.append("  <trk>\n")
            builder.append("    <name>${escapeXml(track.name)}</name>\n")
            if (track.description.isNotBlank()) {
                builder.append("    <desc>${escapeXml(track.description)}</desc>\n")
            }
            
            track.segments.forEachIndexed { index, segment ->
                builder.append("    <trkseg>\n")
                segment.forEach { point ->
                    builder.append("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">\n")
                    point.elevation?.let { ele ->
                        builder.append("        <ele>$ele</ele>\n")
                    }
                    builder.append("      </trkpt>\n")
                }
                builder.append("    </trkseg>\n")
            }
            
            builder.append("  </trk>\n")
        }
        
        builder.append("</gpx>")
        return builder.toString()
    }
    
    /**
     * Вычисляет длину трека в метрах
     */
    fun calculateTrackLength(points: List<TrackPoint>): Double {
        var totalDistance = 0.0
        
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            totalDistance += haversineDistance(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        }
        
        return totalDistance
    }
    
    /**
     * Формула гаверсинуса для вычисления расстояния между двумя точками
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // метров
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
    
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;")
    }
    
    private fun getCurrentTime(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.TimeZone.getTimeZone("UTC"))
            .format(java.util.Date())
    }
}

// GPX Data Classes

@Root(name = "gpx", strict = false)
data class Gpx(
    @field:Attribute(name = "version", required = false)
    @param:Attribute(name = "version", required = false)
    var version: String? = null,
    
    @field:Element(name = "metadata", required = false)
    @param:Element(name = "metadata", required = false)
    var metadata: Metadata? = null,
    
    @field:ElementList(name = "wpt", inline = true, required = false)
    @param:ElementList(name = "wpt", inline = true, required = false)
    var waypoints: List<Wpt>? = null,
    
    @field:ElementList(name = "trk", inline = true, required = false)
    @param:ElementList(name = "trk", inline = true, required = false)
    var tracks: List<Trk>? = null,
    
    @field:ElementList(name = "rte", inline = true, required = false)
    @param:ElementList(name = "rte", inline = true, required = false)
    var routes: List<Rte>? = null
)

@Root(name = "metadata", strict = false)
data class Metadata(
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,
    
    @field:Element(name = "time", required = false)
    @param:Element(name = "time", required = false)
    var time: String? = null
)

@Root(name = "wpt", strict = false)
data class Wpt(
    @field:Attribute(name = "lat", required = false)
    @param:Attribute(name = "lat", required = false)
    var lat: String? = null,
    
    @field:Attribute(name = "lon", required = false)
    @param:Attribute(name = "lon", required = false)
    var lon: String? = null,
    
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,
    
    @field:Element(name = "desc", required = false)
    @param:Element(name = "desc", required = false)
    var desc: String? = null,
    
    @field:Element(name = "ele", required = false)
    @param:Element(name = "ele", required = false)
    var ele: String? = null,
    
    @field:Element(name = "time", required = false)
    @param:Element(name = "time", required = false)
    var time: String? = null
)

@Root(name = "trk", strict = false)
data class Trk(
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,
    
    @field:Element(name = "desc", required = false)
    @param:Element(name = "desc", required = false)
    var desc: String? = null,
    
    @field:ElementList(name = "trkseg", inline = true, required = false)
    @param:ElementList(name = "trkseg", inline = true, required = false)
    var segments: List<Trkseg>? = null
)

@Root(name = "trkseg", strict = false)
data class Trkseg(
    @field:ElementList(name = "trkpt", inline = true, required = false)
    @param:ElementList(name = "trkpt", inline = true, required = false)
    var points: List<Trkpt>? = null
)

@Root(name = "trkpt", strict = false)
data class Trkpt(
    @field:Attribute(name = "lat", required = false)
    @param:Attribute(name = "lat", required = false)
    var lat: String? = null,
    
    @field:Attribute(name = "lon", required = false)
    @param:Attribute(name = "lon", required = false)
    var lon: String? = null,
    
    @field:Element(name = "ele", required = false)
    @param:Element(name = "ele", required = false)
    var ele: String? = null,
    
    @field:Element(name = "time", required = false)
    @param:Element(name = "time", required = false)
    var time: String? = null
)

@Root(name = "rte", strict = false)
data class Rte(
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,
    
    @field:Element(name = "desc", required = false)
    @param:Element(name = "desc", required = false)
    var desc: String? = null,
    
    @field:ElementList(name = "rtept", inline = true, required = false)
    @param:ElementList(name = "rtept", inline = true, required = false)
    var points: List<Rtept>? = null
)

@Root(name = "rtept", strict = false)
data class Rtept(
    @field:Attribute(name = "lat", required = false)
    @param:Attribute(name = "lat", required = false)
    var lat: String? = null,
    
    @field:Attribute(name = "lon", required = false)
    @param:Attribute(name = "lon", required = false)
    var lon: String? = null,
    
    @field:Element(name = "ele", required = false)
    @param:Element(name = "ele", required = false)
    var ele: String? = null,
    
    @field:Element(name = "time", required = false)
    @param:Element(name = "time", required = false)
    var time: String? = null
)

// Domain models

data class GpxData(
    val waypoints: List<Waypoint>,
    val tracks: List<Track>,
    val routes: List<Route>
)

data class Waypoint(
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: String? = null
)

data class Track(
    val name: String,
    val description: String,
    val segments: List<List<TrackPoint>>
)

data class Route(
    val name: String,
    val description: String,
    val points: List<TrackPoint>
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: String? = null
)

data class GpxExportData(
    val name: String,
    val waypoints: List<WaypointExport>,
    val tracks: List<TrackExport>
)

data class WaypointExport(
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null
)

data class TrackExport(
    val name: String,
    val description: String,
    val segments: List<List<TrackPointExport>>
)

data class TrackPointExport(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null
)
