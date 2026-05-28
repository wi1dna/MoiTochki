package com.example.moitochki.cluster

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker as OsmMarker
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.view.MotionEvent
import kotlin.math.*

/**
 * Менеджер кластеризации меток для оптимизации отображения большого количества точек
 */
class ClusterManager(private val mapView: MapView) {
    
    private val clusters = mutableListOf<Cluster>()
    private val gridSize = 60 // Размер ячейки сетки в пикселях
    private var lastZoomLevel = -1.0
    
    /**
     * Обновляет кластеры на основе текущих меток
     */
    fun updateClusters(markers: List<MarkerEntity>) {
        val zoom = mapView.zoomLevelDouble
        
        // Пересчитываем кластеры только при изменении зума более чем на 0.5
        if (abs(zoom - lastZoomLevel) < 0.5 && clusters.isNotEmpty()) {
            return
        }
        
        lastZoomLevel = zoom
        clusters.clear()
        
        if (markers.size < 2) {
            // Если меток мало, кластеризация не нужна
            return
        }
        
        // Группируем метки по сетке
        val grid = mutableMapOf<String, MutableList<MarkerEntity>>()
        
        markers.forEach { marker ->
            val geoPoint = GeoPoint(marker.latitude, marker.longitude)
            val screenPoint = mapView.projection.toPixels(geoPoint, null)
            
            if (screenPoint != null) {
                val gridX = (screenPoint.x / gridSize).toInt()
                val gridY = (screenPoint.y / gridSize).toInt()
                val key = "${gridX}_${gridY}"
                
                grid.getOrPut(key) { mutableListOf() }.add(marker)
            }
        }
        
        // Создаем кластеры
        grid.forEach { (_, clusterMarkers) ->
            if (clusterMarkers.size >= 2) {
                // Вычисляем центр кластера
                val avgLat = clusterMarkers.map { it.latitude }.average()
                val avgLon = clusterMarkers.map { it.longitude }.average()
                
                clusters.add(
                    Cluster(
                        position = GeoPoint(avgLat, avgLon),
                        markers = clusterMarkers,
                        count = clusterMarkers.size
                    )
                )
            }
        }
    }
    
    /**
     * Проверяет, находится ли точка внутри кластера
     */
    fun getClusterAtPoint(x: Float, y: Float): Cluster? {
        val tapGeoPoint = mapView.projection.fromPixels(x.toInt(), y.toInt())
        
        return clusters.find { cluster ->
            val clusterScreen = mapView.projection.toPixels(cluster.position, null)
            if (clusterScreen != null) {
                val distance = sqrt(
                    pow((x - clusterScreen.x).toDouble(), 2.0) + 
                    pow((y - clusterScreen.y).toDouble(), 2.0)
                )
                distance < 30 // Радиус клика по кластеру
            } else false
        }
    }
    
    /**
     * Получает отдельные метки (не в кластерах)
     */
    fun getNonClusteredMarkers(allMarkers: List<MarkerEntity>): List<MarkerEntity> {
        val clusteredIds = clusters.flatMap { it.markers }.map { it.id }.toSet()
        return allMarkers.filter { it.id !in clusteredIds }
    }
    
    /**
     * Очищает кластеры
     */
    fun clear() {
        clusters.clear()
        lastZoomLevel = -1.0
    }
    
    /**
     * Получает все кластеры
     */
    fun getClusters(): List<Cluster> = clusters.toList()
}

/**
 * Модель кластера
 */
data class Cluster(
    val position: GeoPoint,
    val markers: List<MarkerEntity>,
    val count: Int
)

/**
 * Оверлей для отрисовки кластеров
 */
class ClusterOverlay(
    private val clusterManager: ClusterManager,
    private val onClusterClick: (Cluster) -> Boolean
) : org.osmdroid.views.overlay.Overlay() {
    
    private val clusterPaint = Paint().apply {
        color = Color.parseColor("#FF5722")
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        clusterManager.getClusters().forEach { cluster ->
            val screenPoint = mapView.projection.toPixels(cluster.position, null)
            if (screenPoint != null) {
                val radius = 25 + (cluster.count.coerceAtMost(100) / 4).toFloat()
                
                // Рисуем круг
                canvas.drawCircle(screenPoint.x, screenPoint.y, radius, clusterPaint)
                
                // Рисуем количество
                canvas.drawText(cluster.count.toString(), screenPoint.x, screenPoint.y + 15, textPaint)
            }
        }
    }
    
    override fun onSingleTapConfirmed(event: MotionEvent?, mapView: MapView?): Boolean {
        if (event == null || mapView == null) return false
        
        val cluster = clusterManager.getClusterAtPoint(event.x, event.y)
        return cluster?.let { onClusterClick(it) } ?: false
    }
}
