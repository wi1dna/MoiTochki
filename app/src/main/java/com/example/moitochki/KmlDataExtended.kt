package com.example.moitochki

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

/**
 * Расширенные данные KML для поддержки LineString, Polygon и других геометрий
 */

@Root(name = "LineString", strict = false)
data class KmlLineString(
    @field:Element(name = "coordinates", required = false)
    @param:Element(name = "coordinates", required = false)
    var coordinates: String? = null
)

@Root(name = "Polygon", strict = false)
data class KmlPolygon(
    @field:Element(name = "outerBoundaryIs", required = false)
    @param:Element(name = "outerBoundaryIs", required = false)
    var outerBoundary: Boundary? = null,
    
    @field:Element(name = "innerBoundaryIs", required = false)
    @param:Element(name = "innerBoundaryIs", required = false)
    var innerBoundaries: List<Boundary>? = null
)

@Root(name = "outerBoundaryIs", strict = false)
data class Boundary(
    @field:Element(name = "LinearRing", required = false)
    @param:Element(name = "LinearRing", required = false)
    var linearRing: LinearRing? = null
)

@Root(name = "LinearRing", strict = false)
data class LinearRing(
    @field:Element(name = "coordinates", required = false)
    @param:Element(name = "coordinates", required = false)
    var coordinates: String? = null
)

@Root(name = "Placemark", strict = false)
data class KmlPlacemarkExtended(
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,

    @field:Element(name = "description", required = false)
    @param:Element(name = "description", required = false)
    var description: String? = null,

    @field:Element(name = "Point", required = false)
    @param:Element(name = "Point", required = false)
    var point: KmlPoint? = null,
    
    @field:Element(name = "LineString", required = false)
    @param:Element(name = "LineString", required = false)
    var lineString: KmlLineString? = null,
    
    @field:Element(name = "Polygon", required = false)
    @param:Element(name = "Polygon", required = false)
    var polygon: KmlPolygon? = null,
    
    @field:Element(name = "Style", required = false)
    @param:Element(name = "Style", required = false)
    var style: KmlStyle? = null
)

@Root(name = "Style", strict = false)
data class KmlStyle(
    @field:Element(name = "IconStyle", required = false)
    @param:Element(name = "IconStyle", required = false)
    var iconStyle: IconStyle? = null,
    
    @field:Element(name = "LineStyle", required = false)
    @param:Element(name = "LineStyle", required = false)
    var lineStyle: LineStyle? = null,
    
    @field:Element(name = "PolyStyle", required = false)
    @param:Element(name = "PolyStyle", required = false)
    var polyStyle: PolyStyle? = null
)

@Root(name = "IconStyle", strict = false)
data class IconStyle(
    @field:Element(name = "color", required = false)
    @param:Element(name = "color", required = false)
    var color: String? = null,
    
    @field:Element(name = "scale", required = false)
    @param:Element(name = "scale", required = false)
    var scale: Float = 1.0f
)

@Root(name = "LineStyle", strict = false)
data class LineStyle(
    @field:Element(name = "color", required = false)
    @param:Element(name = "color", required = false)
    var color: String? = null,
    
    @field:Element(name = "width", required = false)
    @param:Element(name = "width", required = false)
    var width: Float = 1.0f
)

@Root(name = "PolyStyle", strict = false)
data class PolyStyle(
    @field:Element(name = "color", required = false)
    @param:Element(name = "color", required = false)
    var color: String? = null,
    
    @field:Element(name = "fill", required = false)
    @param:Element(name = "fill", required = false)
    var fill: Boolean = true,
    
    @field:Element(name = "outline", required = false)
    @param:Element(name = "outline", required = false)
    var outline: Boolean = true
)

@Root(name = "Document", strict = false)
data class KmlDocumentExtended(
    @field:ElementList(inline = true, required = false)
    @param:ElementList(inline = true, required = false)
    var placemarks: List<KmlPlacemarkExtended>? = null,
    
    @field:ElementList(name = "Folder", inline = true, required = false)
    @param:ElementList(name = "Folder", inline = true, required = false)
    var folders: List<KmlFolder>? = null
)

@Root(name = "Folder", strict = false)
data class KmlFolder(
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,
    
    @field:ElementList(inline = true, required = false)
    @param:ElementList(inline = true, required = false)
    var placemarks: List<KmlPlacemarkExtended>? = null
)

@Root(name = "kml", strict = false)
data class KmlRootExtended(
    @field:Element(name = "Document", required = false)
    @param:Element(name = "Document", required = false)
    var document: KmlDocumentExtended? = null
)

/**
 * Данные для линий (LineString)
 */
data class PathData(
    val name: String,
    val description: String,
    val points: List<Pair<Double, Double>>, // lat, lon
    val color: Int = -1,
    val width: Float = 3f,
    val folderId: Long
)

/**
 * Данные для полигонов (Polygon)
 */
data class PolygonData(
    val name: String,
    val description: String,
    val outerPoints: List<Pair<Double, Double>>,
    val innerPoints: List<List<Pair<Double, Double>>>,
    val fillColor: Int = 0x40FF0000,
    val strokeColor: Int = -1,
    val strokeWidth: Float = 2f,
    val folderId: Long
)
