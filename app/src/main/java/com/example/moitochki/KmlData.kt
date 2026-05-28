package com.example.moitochki

import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root

@Root(name = "kml", strict = false)
data class KmlRoot(
    @field:Element(name = "Document")
    @param:Element(name = "Document")
    var document: KmlDocument? = null
)

@Root(name = "Document", strict = false)
data class KmlDocument(
    @field:ElementList(inline = true, required = false)
    @param:ElementList(inline = true, required = false)
    var placemarks: List<KmlPlacemark>? = null
)

@Root(name = "Placemark", strict = false)
data class KmlPlacemark(
    @field:Element(name = "name", required = false)
    @param:Element(name = "name", required = false)
    var name: String? = null,

    @field:Element(name = "description", required = false)
    @param:Element(name = "description", required = false)
    var description: String? = null,

    @field:Element(name = "Point", required = false)
    @param:Element(name = "Point", required = false)
    var point: KmlPoint? = null
)

@Root(name = "Point", strict = false)
data class KmlPoint(
    @field:Element(name = "coordinates")
    @param:Element(name = "coordinates")
    var coordinates: String? = null // "lng,lat,alt"
)
