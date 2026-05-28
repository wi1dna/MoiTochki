package com.example.moitochki.parser

import com.example.moitochki.KmlParser
import com.example.moitochki.KmlParserExtended
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream

/**
 * Unit tests for KML/KMZ parsers
 */
class KmlParserTest {
    
    private val kmlParser = KmlParser()
    private val kmlParserExtended = KmlParserExtended()
    
    @Test
    fun `parse valid KML point`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark>
                        <name>Test Point</name>
                        <description>Test Description</description>
                        <Point>
                            <coordinates>37.618423,55.751244,0</coordinates>
                        </Point>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()
        
        val markers = kmlParser.parseKmlFromString(kmlContent)
        
        assertEquals(1, markers.size)
        assertEquals("Test Point", markers[0].name)
        assertEquals("Test Description", markers[0].description)
        assertEquals(55.751244, markers[0].latitude, 0.000001)
        assertEquals(37.618423, markers[0].longitude, 0.000001)
    }
    
    @Test
    fun `parse KML with invalid coordinates`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark>
                        <name>Invalid Point</name>
                        <Point>
                            <coordinates>200.0,100.0,0</coordinates>
                        </Point>
                    </Placemark>
                    <Placemark>
                        <name>Valid Point</name>
                        <Point>
                            <coordinates>37.0,55.0,0</coordinates>
                        </Point>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()
        
        val markers = kmlParser.parseKmlFromString(kmlContent)
        
        assertEquals(1, markers.size)
        assertEquals("Valid Point", markers[0].name)
    }
    
    @Test
    fun `parse KML with empty name`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark>
                        <name>   </name>
                        <Point>
                            <coordinates>37.0,55.0,0</coordinates>
                        </Point>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()
        
        val markers = kmlParser.parseKmlFromString(kmlContent)
        
        assertEquals(1, markers.size)
        assertEquals("Без названия", markers[0].name)
    }
    
    @Test
    fun `parse LineString`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark>
                        <name>Test Path</name>
                        <description>A test path</description>
                        <LineString>
                            <coordinates>
                                37.0,55.0,0
                                37.1,55.1,0
                                37.2,55.2,0
                            </coordinates>
                        </LineString>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()
        
        val result = kmlContent.byteInputStream().use { 
            kmlParserExtended.parseKmlExtended(it) 
        }
        
        assertEquals(1, result.paths.size)
        assertEquals("Test Path", result.paths[0].name)
        assertEquals(3, result.paths[0].points.size)
    }
    
    @Test
    fun `parse Polygon`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark>
                        <name>Test Polygon</name>
                        <Polygon>
                            <outerBoundaryIs>
                                <LinearRing>
                                    <coordinates>
                                        37.0,55.0,0
                                        37.1,55.0,0
                                        37.1,55.1,0
                                        37.0,55.1,0
                                        37.0,55.0,0
                                    </coordinates>
                                </LinearRing>
                            </outerBoundaryIs>
                        </Polygon>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()
        
        val result = kmlContent.byteInputStream().use { 
            kmlParserExtended.parseKmlExtended(it) 
        }
        
        assertEquals(1, result.polygons.size)
        assertEquals("Test Polygon", result.polygons[0].name)
        assertEquals(5, result.polygons[0].outerPoints.size)
    }
    
    @Test
    fun `parse KML color ABGR to ARGB`() {
        val kmlColor = "ff0000ff" // Blue in KML (AABBGGRR)
        
        val color = kmlParserExtended.parseKmlColor(kmlColor)
        
        // Should be converted to Android Color (AARRGGBB)
        assertEquals(0xFFFF0000.toInt(), color) // Red in Android format
    }
    
    @Test
    fun `empty KML returns empty list`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                </Document>
            </kml>
        """.trimIndent()
        
        val markers = kmlParser.parseKmlFromString(kmlContent)
        
        assertTrue(markers.isEmpty())
    }
    
    @Test
    fun `parse multiple placemarks`() {
        val kmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
                <Document>
                    <Placemark>
                        <name>Point 1</name>
                        <Point><coordinates>37.0,55.0,0</coordinates></Point>
                    </Placemark>
                    <Placemark>
                        <name>Point 2</name>
                        <Point><coordinates>38.0,56.0,0</coordinates></Point>
                    </Placemark>
                    <Placemark>
                        <name>Point 3</name>
                        <Point><coordinates>39.0,57.0,0</coordinates></Point>
                    </Placemark>
                </Document>
            </kml>
        """.trimIndent()
        
        val markers = kmlParser.parseKmlFromString(kmlContent)
        
        assertEquals(3, markers.size)
    }
}
