package com.example.moitochki

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker as OsmMarker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Job
import org.osmdroid.views.overlay.Polyline
import android.graphics.DashPathEffect
import android.text.format.Formatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.util.MapTileIndex
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import org.osmdroid.views.Projection

class EsriSatelliteTileSource : OnlineTileSourceBase(
    "Esri_Satellite",
    0, 18, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Sources: Esri, Maxar, Earthstar Geographics"
) {
    override fun getTileURLString(pTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pTileIndex) + "/" + 
               MapTileIndex.getY(pTileIndex) + "/" + 
               MapTileIndex.getX(pTileIndex)
    }

    override fun getTileSourcePolicy(): TileSourcePolicy {
        return TileSourcePolicy(0, 2)
    }
}

val ESRI_SATELLITE = EsriSatelliteTileSource()

class LabelMarker(private val map: MapView) : OsmMarker(map) {
    var showNameLabel: Boolean = false
    var markerId: Long = -1
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 36f
        isAntiAlias = true
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bgPaint = Paint().apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        super.draw(canvas, projection)
        if (showNameLabel && title != null && map.zoomLevelDouble >= 11.0) {
            val p = projection.toPixels(position, null)
            val textWidth = textPaint.measureText(title)
            val x = p.x.toFloat() + 45
            val y = p.y.toFloat()
            
            canvas.drawRect(x - 5, y - 35, x + textWidth + 5, y + 10, bgPaint)
            canvas.drawText(title, x, y, textPaint)
        }
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    
    private var currentPhotoPath: String? = null
    private var lastPhotoPreview: ImageView? = null
    private var tempSearchMarker: OsmMarker? = null
    private val searchHistory = mutableListOf<String>()
    
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var searchPopup: ListPopupWindow? = null
    private var isSearchingProgrammatically = false
    private var searchJob: Job? = null
    
    private var navigationPolyline: Polyline? = null
    private var navigationTarget: GeoPoint? = null
    private var selectedMarkerId: Long? = null

    private val viewModel: MapViewModel by viewModels {
        MapViewModelFactory(MarkerRepository(AppDatabase.getDatabase(this).markerDao()))
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportData(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importData(it) }
    }

    private val exportKmzLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kmz")) { uri ->
        uri?.let { exportKmz(it) }
    }

    private val importKmzLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importKmz(it) }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                val bitmap = BitmapFactory.decodeFile(path)
                lastPhotoPreview?.setImageBitmap(bitmap)
                lastPhotoPreview?.visibility = View.VISIBLE
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationOverlay.enableMyLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getPreferences(Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        setupMap()
        setupUI()
        
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        ))
        
        viewModel.allFolders.observe(this) { folders ->
            if (folders.isEmpty()) {
                viewModel.insertFolder(getString(R.string.default_folder_name))
            } else {
                val visibleFolders = folders.filter { it.isVisible }.associateBy { it.id }
                viewModel.allMarkers.observe(this) { markers ->
                    updateMarkersOnMap(markers, visibleFolders)
                }
            }
        }
        
        restoreMapPos()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setBuiltInZoomControls(false)

        val copyrightOverlay = object : CopyrightOverlay(this) {
            override fun draw(canvas: Canvas, projection: Projection) {
                val text = when (mapView.tileProvider.tileSource.name()) {
                    "Esri_Satellite" -> "Sources: Esri, Maxar, Earthstar Geographics"
                    else -> "© OpenStreetMap contributors"
                }
                
                val paint = Paint().apply {
                    color = Color.BLACK
                    textSize = 28f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                val x = canvas.width / 2f
                val y = canvas.height - 40f
                
                val bgPaint = Paint().apply {
                    color = Color.argb(120, 255, 255, 255)
                }
                
                val bounds = Rect()
                paint.getTextBounds(text, 0, text.length, bounds)
                canvas.drawRect(
                    x - bounds.width() / 2f - 15, 
                    y - bounds.height() - 10, 
                    x + bounds.width() / 2f + 15, 
                    y + 10, 
                    bgPaint
                )
                
                canvas.drawText(text, x, y, paint)
            }
        }
        mapView.overlays.add(copyrightOverlay)

        val startPoint = GeoPoint(55.751244, 37.618423)
        mapView.controller.setZoom(10.0)
        mapView.controller.setCenter(startPoint)

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        locationOverlay.enableMyLocation()
        locationOverlay.disableFollowLocation()
        
        val redDot = resources.getDrawable(android.R.drawable.presence_online, theme) as BitmapDrawable
        locationOverlay.setPersonIcon(redDot.bitmap)
        locationOverlay.setDirectionIcon(redDot.bitmap)
        
        mapView.overlays.add(locationOverlay)

        val rotationGestureOverlay = RotationGestureOverlay(mapView)
        rotationGestureOverlay.isEnabled = true
        mapView.overlays.add(rotationGestureOverlay)

        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                hideKeyboard()
                findViewById<EditText>(R.id.etSearch).clearFocus()
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { showAddMarkerDialog(it) }
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(eventsReceiver))
    }

    private fun setupUI() {
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val btnClear = findViewById<ImageButton>(R.id.btnClearSearch)
        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (isSearchingProgrammatically) return
                
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    if (!s.isNullOrBlank() && s.length >= 2) {
                        performSearch(s.toString().trim())
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 800)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClear.setOnClickListener {
            etSearch.setText("")
            selectedMarkerId = null
            updateMarkersOnMap(viewModel.allMarkers.value ?: emptyList(), viewModel.allFolders.value?.filter { it.isVisible }?.associateBy { it.id } ?: emptyMap())
        }

        findViewById<ImageButton>(R.id.btnMyLocation).setOnClickListener {
            locationOverlay.myLocation?.let {
                mapView.controller.animateTo(it)
                mapView.controller.setZoom(15.0)
            } ?: Toast.makeText(this, "Определение координат...", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener { 
            performSearch(findViewById<EditText>(R.id.etSearch).text.toString().trim()) 
        }
        findViewById<ImageButton>(R.id.btnLayers).setOnClickListener { showLayersDialog() }
        findViewById<ImageButton>(R.id.btnFolders).setOnClickListener { showFoldersDialog() }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { showSettingsDialog() }
        
        findViewById<ImageButton>(R.id.btnZoomIn).setOnClickListener { mapView.controller.zoomIn() }
        findViewById<ImageButton>(R.id.btnZoomOut).setOnClickListener { mapView.controller.zoomOut() }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        
        if (!searchHistory.contains(query)) searchHistory.add(0, query)

        val progress = findViewById<ProgressBar>(R.id.searchProgress)
        progress.visibility = View.VISIBLE

        val mapCenter = mapView.mapCenter as GeoPoint
        val normalizedQuery = query.replace(Regex("[\\s-]"), "").lowercase()

        searchJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Search in local markers with normalization
                val allMarkers = viewModel.allMarkers.value ?: emptyList()
                val localResults = allMarkers.filter { m ->
                    val normName = m.name.replace(Regex("[\\s-]"), "").lowercase()
                    normName.contains(normalizedQuery) || m.description.contains(query, ignoreCase = true)
                }.sortedWith(compareBy({ !it.name.startsWith(query, ignoreCase = true) }, {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(mapCenter.latitude, mapCenter.longitude, it.latitude, it.longitude, results)
                    results[0]
                }))

                // 2. Search by coordinates
                val coords = query.split(",").mapNotNull { it.trim().toDoubleOrNull() }

                // 3. Search by address (Enhanced with regional bias)
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addresses = try {
                    val delta = 1.0 // Ограничиваем поиск областью ~100км вокруг центра карты
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        geocoder.getFromLocationName(query, 10,
                            mapCenter.latitude - delta, mapCenter.longitude - delta,
                            mapCenter.latitude + delta, mapCenter.longitude + delta
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(query, 10,
                            mapCenter.latitude - delta, mapCenter.longitude - delta,
                            mapCenter.latitude + delta, mapCenter.longitude + delta
                        )
                    } ?: geocoder.getFromLocationName(query, 5) // Если в регионе пусто, ищем глобально
                } catch (e: Exception) { null }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    if (coords.size == 2) {
                        val point = GeoPoint(coords[0], coords[1])
                        mapView.controller.animateTo(point)
                        mapView.controller.setZoom(17.0)
                        addTempMarker(point, getString(R.string.coords_prefix, query))
                        return@withContext
                    }

                    if (localResults.isNotEmpty() || !addresses.isNullOrEmpty()) {
                        showCombinedSearchResults(localResults, addresses ?: emptyList())
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.nothing_found), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        progress.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun addTempMarker(point: GeoPoint, title: String) {
        tempSearchMarker?.let { mapView.overlays.remove(it) }
        val marker = OsmMarker(mapView)
        marker.position = point
        marker.title = title
        marker.setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
        
        // Делаем иконку красной для выделения
        val icon = resources.getDrawable(R.drawable.ic_target, theme).mutate()
        icon.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
        marker.icon = icon
        
        val infoWindow = object : MarkerInfoWindow(R.layout.modern_info_window, mapView) {
            override fun onOpen(item: Any?) {
                super.onOpen(item)
                val m = item as OsmMarker
                val coords = String.format(Locale.US, "%.6f, %.6f", m.position.latitude, m.position.longitude)
                val subDesc = mView.findViewById<TextView>(R.id.bubble_subdescription)
                subDesc.text = coords
                subDesc.visibility = View.VISIBLE
                
                val btnCopy = mView.findViewById<Button>(R.id.bubble_copy_coords)
                btnCopy.visibility = View.VISIBLE
                btnCopy.setOnClickListener { copyToClipboard(coords) }
                
                mView.findViewById<TextView>(R.id.bubble_title).text = m.title
            }
        }
        marker.infoWindow = infoWindow
        
        mapView.overlays.add(marker)
        tempSearchMarker = marker
        mapView.invalidate()
        marker.showInfoWindow() 
    }

    private fun showCombinedSearchResults(localMarkers: List<Marker>, addresses: List<Address>) {
        val resultsCopy = mutableListOf<SearchResultItem>()
        localMarkers.forEach { resultsCopy.add(SearchResultItem.LocalMarker(it)) }
        addresses.forEach { resultsCopy.add(SearchResultItem.AddressItem(it)) }

        if (resultsCopy.isEmpty()) {
            searchPopup?.dismiss()
            return
        }

        if (searchPopup == null) {
            searchPopup = ListPopupWindow(this)
            searchPopup?.anchorView = findViewById(R.id.searchContainer)
            searchPopup?.setBackgroundDrawable(resources.getDrawable(R.drawable.rounded_popup_bg, theme))
            searchPopup?.width = findViewById<View>(R.id.searchContainer).width
            searchPopup?.height = ListPopupWindow.WRAP_CONTENT
            searchPopup?.verticalOffset = 4
            searchPopup?.isModal = false
        }

        val adapter = object : ArrayAdapter<String>(this, R.layout.modern_spinner_item, resultsCopy.map { it.getDisplayName(this@MainActivity) }) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val text = view.findViewById<TextView>(android.R.id.text1)
                text.setTextColor(Color.BLACK)
                text.textSize = 14f
                return view
            }
        }
        
        searchPopup?.setAdapter(adapter)
        searchPopup?.setOnItemClickListener { _, _, position, _ ->
            if (position < 0 || position >= resultsCopy.size) return@setOnItemClickListener
            searchJob?.cancel()
            val selected = resultsCopy[position]
            when (selected) {
                is SearchResultItem.LocalMarker -> {
                    val m = selected.marker
                    selectedMarkerId = m.id
                    mapView.controller.animateTo(GeoPoint(m.latitude, m.longitude))
                    mapView.controller.setZoom(17.0)
                    mapView.post { 
                        updateMarkersOnMap(viewModel.allMarkers.value ?: emptyList(), viewModel.allFolders.value?.filter { it.isVisible }?.associateBy { it.id } ?: emptyMap())
                        highlightMarkerItem(m) 
                    }
                }
                is SearchResultItem.AddressItem -> {
                    selectedMarkerId = null
                    val addr = selected.address
                    val point = GeoPoint(addr.latitude, addr.longitude)
                    mapView.controller.animateTo(point)
                    mapView.controller.setZoom(18.0)
                    addTempMarker(point, selected.getDisplayFull())
                    updateMarkersOnMap(viewModel.allMarkers.value ?: emptyList(), viewModel.allFolders.value?.filter { it.isVisible }?.associateBy { it.id } ?: emptyMap())
                }
            }
            searchPopup?.dismiss()
            val etSearch = findViewById<EditText>(R.id.etSearch)
            isSearchingProgrammatically = true
            val name = when(selected) {
                is SearchResultItem.LocalMarker -> selected.marker.name
                is SearchResultItem.AddressItem -> selected.address.getAddressLine(0).substringBefore(", Россия")
            }
            etSearch.setText(name)
            isSearchingProgrammatically = false
            etSearch.clearFocus()
        }
        searchPopup?.show()
    }

    sealed class SearchResultItem {
        abstract fun getDisplayName(context: Context): String
        abstract fun getDisplayFull(): String

        data class LocalMarker(val marker: Marker) : SearchResultItem() {
            override fun getDisplayName(context: Context): String = "📍 ${marker.name} ${context.getString(R.string.my_label_suffix)}"
            override fun getDisplayFull(): String = marker.name
        }

        data class AddressItem(val address: Address) : SearchResultItem() {
            override fun getDisplayName(context: Context): String {
                val city = address.locality ?: ""
                val street = address.thoroughfare ?: ""
                val house = address.subThoroughfare ?: ""
                val parts = mutableListOf<String>()
                if (city.isNotBlank()) parts.add(city)
                if (street.isNotBlank()) {
                    if (house.isNotBlank()) parts.add("$street, $house") else parts.add(street)
                }
                val base = if (parts.size >= 2) parts.joinToString(", ") else (address.getAddressLine(0) ?: "")
                return "🏠 ${base.substringBefore(", Россия")}"
            }
            override fun getDisplayFull(): String = address.getAddressLine(0) ?: "Неизвестный адрес"
        }
    }

    private fun showLayersDialog() {
        val layers = arrayOf(getString(R.string.normal_layer), getString(R.string.satellite_layer))
        val adapter = createModernAdapter(layers)
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.layers)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> mapView.setTileSource(TileSourceFactory.MAPNIK)
                    1 -> mapView.setTileSource(ESRI_SATELLITE)
                }
                mapView.invalidate()
            }
            .show()
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.search_history),
            getString(R.string.data_management),
            getString(R.string.offline_maps)
        )
        val adapter = createModernAdapter(options)
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.settings)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showSearchHistoryDialog()
                    1 -> showDataManagementDialog()
                    2 -> showOfflineMapsDialog()
                }
            }
            .show()
    }

    private fun showSearchHistoryDialog() {
        if (searchHistory.isEmpty()) {
            Toast.makeText(this, getString(R.string.history_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val items = searchHistory.toMutableList().apply { add(getString(R.string.clear_history)) }
        val adapter = createModernAdapter(items.toTypedArray())
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.search_history)
            .setAdapter(adapter) { _, which ->
                if (which < searchHistory.size) {
                    val query = searchHistory[which]
                    val etSearch = findViewById<EditText>(R.id.etSearch)
                    isSearchingProgrammatically = true
                    etSearch.setText(query)
                    isSearchingProgrammatically = false
                    performSearch(query)
                } else {
                    searchHistory.clear()
                }
            }
            .show()
    }

    private fun showDataManagementDialog() {
        val options = arrayOf(
            getString(R.string.export_json),
            getString(R.string.import_json),
            getString(R.string.export_kmz),
            getString(R.string.import_kmz)
        )
        val adapter = createModernAdapter(options)
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.data_management)
            .setAdapter(adapter) { _, which ->
                val time = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                when (which) {
                    0 -> exportLauncher.launch("backup_$time.json")
                    1 -> importLauncher.launch(arrayOf("application/json", "*/*"))
                    2 -> exportKmzLauncher.launch("points_$time.kmz")
                    3 -> importKmzLauncher.launch(arrayOf("*/*"))
                }
            }
            .show()
    }

    private fun showOfflineMapsDialog() {
        val options = arrayOf(
            getString(R.string.download_area),
            getString(R.string.view_cache),
            getString(R.string.clear_cache)
        )
        val adapter = createModernAdapter(options)
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.offline_maps)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> downloadOfflineMap()
                    1 -> showDownloadedRegionsInfo()
                    2 -> clearCache()
                }
            }
            .show()
    }

    private fun showDownloadedRegionsInfo() {
        val size = getCacheSize()
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.offline_maps)
            .setMessage(getString(R.string.cache_info, size))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun clearCache() {
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.clear_cache)
            .setMessage("Вы действительно хотите удалить все загруженные тайлы?")
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val cacheDir = Configuration.getInstance().osmdroidTileCache
                    cacheDir.deleteRecursively()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Кэш очищен", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun getCacheSize(): String {
        val cacheDir = Configuration.getInstance().osmdroidTileCache
        val size = getFolderSize(cacheDir)
        return Formatter.formatFileSize(this, size)
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.exists() && file.isDirectory) {
            file.listFiles()?.forEach { 
                size += if (it.isDirectory) getFolderSize(it) else it.length() 
            }
        } else if (file.exists()) {
            size = file.length()
        }
        return size
    }

    private fun downloadOfflineMap() {
        val box = mapView.boundingBox
        if (box == null || (box.latNorth == 0.0 && box.latSouth == 0.0)) {
            Toast.makeText(this, "Сначала загрузите карту", Toast.LENGTH_SHORT).show()
            return
        }

        val cm = CacheManager(mapView)
        val zoomMin = 10
        val zoomMax = 16
        
        val totalTiles = try { cm.possibleTilesInArea(box, zoomMin, zoomMax) } catch (e: Exception) { 0 }
        
        if (totalTiles > 20000) {
            Toast.makeText(this, "Область слишком велика ($totalTiles тайлов). Уменьшите масштаб.", Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentSource = mapView.tileProvider.tileSource
        if (currentSource.name() == "Mapnik") {
            Toast.makeText(this, "Слой OSM Mapnik блокирует скачивание. Пожалуйста, используйте Спутник.", Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.download_area)
            .setMessage("${getString(R.string.download_confirm)}\nПримерно тайлов: $totalTiles")
            .setPositiveButton(R.string.yes) { _, _ -> 
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        cm.downloadAreaAsync(this@MainActivity, box, zoomMin, zoomMax, object : CacheManager.CacheManagerCallback {
                            override fun onTaskComplete() { showToast("Загрузка завершена") }
                            override fun onTaskFailed(errors: Int) { showToast("Ошибка загрузки (возможно, сервер отклонил запрос)") }
                            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {}
                            override fun downloadStarted() { showToast("Загрузка началась") }
                            override fun setPossibleTilesInArea(total: Int) {}
                        })
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showToast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFoldersDialog() {
        val folders = viewModel.allFolders.value ?: emptyList()
        val names = folders.map { it.name }.toMutableList().apply { add(getString(R.string.add_new_folder)) }
        val adapter = createModernAdapter(names.toTypedArray())
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.folders)
            .setAdapter(adapter) { _, pos ->
                if (pos < folders.size) showMarkersInFolderDialog(folders[pos]) else showAddFolderDialog()
            }
            .show()
            .apply {
                listView.setOnItemLongClickListener { _, _, pos, _ ->
                    if (pos < folders.size) { showFolderActionDialog(folders[pos]); dismiss(); true } else false
                }
            }
    }

    private fun showMarkersInFolderDialog(folder: Folder) {
        lifecycleScope.launch {
            val markers = viewModel.getMarkersInFolderSync(folder.id)
            val names = markers.mapIndexed { i, m -> "${i+1}. ${m.name}" }.toMutableList().apply { add(getString(R.string.back_label)) }
            
            val adapter = object : ArrayAdapter<String>(this@MainActivity, R.layout.modern_spinner_item, names.toTypedArray()) {
                override fun getView(pos: Int, conv: View?, parent: ViewGroup): View {
                    val v = super.getView(pos, conv, parent)
                    v.findViewById<TextView>(android.R.id.text1).setTextColor(Color.BLACK)
                    return v
                }
            }

            AlertDialog.Builder(this@MainActivity, R.style.ModernDialog)
                .setTitle(folder.name)
                .setAdapter(adapter) { _, pos ->
                    if (pos < markers.size) {
                        showMarkerInfoDialog(markers[pos])
                    } else {
                        showFoldersDialog()
                    }
                }
                .show()
                .apply {
                    listView.setOnItemLongClickListener { _, _, pos, _ ->
                        if (pos < markers.size) { 
                            showEditMarkerDialog(markers[pos])
                            this.dismiss()
                            true 
                        } else false
                    }
                }
        }
    }

    private fun highlightMarkerItem(marker: Marker) {
        mapView.post {
            try {
                val overlaysCopy = mapView.overlays.filterIsInstance<LabelMarker>()
                for (overlay in overlaysCopy) {
                    if (overlay.markerId == marker.id) {
                        mapView.controller.animateTo(overlay.position)
                        overlay.showInfoWindow()
                        return@post
                    }
                }
                // Fallback to coordinate search if ID not found
                for (overlay in mapView.overlays.filterIsInstance<OsmMarker>()) {
                    val pos = overlay.position
                    if (pos != null &&
                        Math.abs(pos.latitude - marker.latitude) < 0.00001 &&
                        Math.abs(pos.longitude - marker.longitude) < 0.00001) {
                        mapView.controller.animateTo(pos)
                        overlay.showInfoWindow()
                        break
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun showFolderActionDialog(folder: Folder) {
        val opts = arrayOf(getString(R.string.edit), getString(R.string.delete), getString(R.string.share_kmz))
        val adapter = createModernAdapter(opts)
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(folder.name)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showEditFolderDialog(folder)
                    1 -> viewModel.deleteFolder(folder.id)
                    2 -> shareFolderAsKmz(folder)
                }
            }
            .show()
    }

    private fun shareFolderAsKmz(folder: Folder) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val markers = viewModel.getMarkersInFolderSync(folder.id)
                val file = File(cacheDir, "${folder.name}.kmz")
                val kml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n<Folder><name>${folder.name}</name>")
                markers.forEach { m -> kml.append("<Placemark><name>${m.name}</name><description>${m.description}</description><Point><coordinates>${m.longitude},${m.latitude},0</coordinates></Point></Placemark>") }
                kml.append("</Folder>\n</Document>\n</kml>")
                ZipOutputStream(FileOutputStream(file)).use { z -> z.putNextEntry(ZipEntry("doc.kml")); z.write(kml.toString().toByteArray()); z.closeEntry() }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="application/vnd.google-earth.kmz"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Отправить"))
                }
            } catch (e: Exception) {}
        }
    }

    private fun showEditFolderDialog(folder: Folder) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_folder, null)
        val etName = view.findViewById<EditText>(R.id.etFolderName).apply { setText(folder.name) }
        val cbLabels = view.findViewById<CheckBox>(R.id.cbShowLabels).apply { isChecked = folder.showMarkerLabels }
        val cbIcons = view.findViewById<CheckBox>(R.id.cbShowIcons).apply { isChecked = folder.showMarkerIcons }
        val spinner = view.findViewById<Spinner>(R.id.spinnerFolderIcons)
        val iconTypes = arrayOf("Стандарт", "Сердце", "Звезда", "Закладка", "Стрелка")
        spinner.adapter = createModernAdapter(iconTypes)
        val current = when(folder.defaultIconType) { "ic_map_pin_heart"->"Сердце"; "ic_star"->"Звезда"; "ic_bookmark_stark"->"Закладка"; "ic_arrow_cool_down"->"Стрелка"; else->"Стандарт" }
        spinner.setSelection(iconTypes.indexOf(current).coerceAtLeast(0))

        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle("Папка")
            .setView(view)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = etName.text.toString()
                if (name.isNotBlank()) {
                    val type = when(iconTypes[spinner.selectedItemPosition]) { "Сердце"->"ic_map_pin_heart"; "Звезда"->"ic_star"; "Закладка"->"ic_bookmark_stark"; "Стрелка"->"ic_arrow_cool_down"; else->"default" }
                    viewModel.updateFolder(folder.copy(name=name, showMarkerLabels=cbLabels.isChecked, showMarkerIcons=cbIcons.isChecked, defaultIconType=type))
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddFolderDialog() {
        val input = EditText(this).apply { setPadding(40,20,40,20); hint=getString(R.string.folder_name_hint) }
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(R.string.new_folder)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) viewModel.insertFolder(name)
            }
            .show()
    }

    private fun showAddMarkerDialog(point: GeoPoint) {
        val view = layoutInflater.inflate(R.layout.dialog_add_marker, null)
        val etName = view.findViewById<EditText>(R.id.etMarkerName)
        val etDesc = view.findViewById<EditText>(R.id.etMarkerDesc)
        val spFolder = view.findViewById<Spinner>(R.id.spinnerFolders)
        val spIcon = view.findViewById<Spinner>(R.id.spinnerIcons)
        val spColor = view.findViewById<Spinner>(R.id.spinnerColors)
        val cbLabel = view.findViewById<CheckBox>(R.id.cbShowLabel)
        lastPhotoPreview = view.findViewById(R.id.ivPreview)
        currentPhotoPath = null

        val folders = viewModel.allFolders.value ?: emptyList()
        val folderNames = folders.map { it.name }
        spFolder.adapter = createModernAdapter(folderNames.toTypedArray())
        
        val icons = arrayOf("Стандарт", "Сердце", "Звезда", "Закладка", "Стрелка")
        spIcon.adapter = createModernAdapter(icons)
        
        val colors = arrayOf("Красный", "Синий", "Зеленый", "Желтый")
        spColor.adapter = createModernAdapter(colors)

        view.findViewById<ImageButton>(R.id.btnTakePhoto).setOnClickListener { dispatchTakePictureIntent() }

        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle("Новая метка")
            .setView(view)
            .setPositiveButton("ОК") { _, _ ->
                if (folders.isEmpty()) return@setPositiveButton
                val fId = folders[spFolder.selectedItemPosition].id
                val clr = when(spColor.selectedItemPosition) { 0->Color.RED; 1->Color.BLUE; 2->Color.GREEN; 3->Color.YELLOW; else->Color.RED }
                val type = when(icons[spIcon.selectedItemPosition]) { "Сердце"->"ic_map_pin_heart"; "Звезда"->"ic_star"; "Закладка"->"ic_bookmark_stark"; "Стрелка"->"ic_arrow_cool_down"; else->"default" }
                viewModel.insertMarker(etName.text.toString(), etDesc.text.toString(), point.latitude, point.longitude, fId, type, clr, cbLabel.isChecked, currentPhotoPath)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditMarkerDialog(marker: Marker) {
        val view = layoutInflater.inflate(R.layout.dialog_add_marker, null)
        val etName = view.findViewById<EditText>(R.id.etMarkerName).apply { setText(marker.name) }
        val etDesc = view.findViewById<EditText>(R.id.etMarkerDesc).apply { setText(marker.description) }
        val spFolder = view.findViewById<Spinner>(R.id.spinnerFolders)
        val spIcon = view.findViewById<Spinner>(R.id.spinnerIcons)
        val spColor = view.findViewById<Spinner>(R.id.spinnerColors)
        val cbLabel = view.findViewById<CheckBox>(R.id.cbShowLabel).apply { isChecked = marker.showLabel }
        lastPhotoPreview = view.findViewById(R.id.ivPreview)
        currentPhotoPath = marker.photoPath

        if (marker.photoPath != null && File(marker.photoPath).exists()) {
            lastPhotoPreview?.setImageBitmap(BitmapFactory.decodeFile(marker.photoPath))
            lastPhotoPreview?.visibility = View.VISIBLE
        }

        val folders = viewModel.allFolders.value ?: emptyList()
        val folderNames = folders.map { it.name }
        spFolder.adapter = createModernAdapter(folderNames.toTypedArray())
        spFolder.setSelection(folders.indexOfFirst { it.id == marker.folderId }.coerceAtLeast(0))
        
        val icons = arrayOf("Стандарт", "Сердце", "Звезда", "Закладка", "Стрелка")
        spIcon.adapter = createModernAdapter(icons)
        val currentIcon = when(marker.iconType) { "ic_map_pin_heart"->"Сердце"; "ic_star"->"Звезда"; "ic_bookmark_stark"->"Закладка"; "ic_arrow_cool_down"->"Стрелка"; else->"Стандарт" }
        spIcon.setSelection(icons.indexOf(currentIcon).coerceAtLeast(0))

        val colors = arrayOf("Красный", "Синий", "Зеленый", "Желтый")
        spColor.adapter = createModernAdapter(colors)
        spColor.setSelection(when(marker.color) { Color.RED->0; Color.BLUE->1; Color.GREEN->2; Color.YELLOW->3; else->0 })

        view.findViewById<ImageButton>(R.id.btnTakePhoto).setOnClickListener { dispatchTakePictureIntent() }

        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle("Редактировать")
            .setView(view)
            .setPositiveButton("Обновить") { _, _ ->
                val clr = when(spColor.selectedItemPosition) { 0->Color.RED; 1->Color.BLUE; 2->Color.GREEN; 3->Color.YELLOW; else->Color.RED }
                val type = when(icons[spIcon.selectedItemPosition]) { "Сердце"->"ic_map_pin_heart"; "Звезда"->"ic_star"; "Закладка"->"ic_bookmark_stark"; "Стрелка"->"ic_arrow_cool_down"; else->"default" }
                viewModel.updateMarker(marker.copy(name=etName.text.toString(), description=etDesc.text.toString(), folderId=folders[spFolder.selectedItemPosition].id, iconType=type, color=clr, showLabel=cbLabel.isChecked, photoPath=currentPhotoPath))
            }
            .setNeutralButton("Удалить") { _, _ -> viewModel.deleteMarker(marker) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun dispatchTakePictureIntent() {
        try {
            val file = File.createTempFile("IMG_", ".jpg", getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)).apply { currentPhotoPath = absolutePath }
            takePhotoLauncher.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", file))
        } catch (e: Exception) {}
    }

    private fun updateMarkersOnMap(markers: List<Marker>, visibleFolders: Map<Long, Folder>) {
        mapView.overlays.removeAll { it is LabelMarker && it != tempSearchMarker }
        markers.filter { it.folderId in visibleFolders.keys }.forEach { m ->
            val folder = visibleFolders[m.folderId]!!
            val osm = LabelMarker(mapView).apply {
                markerId = m.id
                position = GeoPoint(m.latitude, m.longitude)
                title = m.name; snippet = m.description
                setAnchor(OsmMarker.ANCHOR_CENTER, OsmMarker.ANCHOR_BOTTOM)
                showNameLabel = folder.showMarkerLabels && m.showLabel
                if (folder.showMarkerIcons) {
                    val icon = when(if (m.iconType=="default") folder.defaultIconType else m.iconType) {
                        "ic_map_pin_heart"->resources.getDrawable(R.drawable.ic_map_pin_heart, theme)
                        "ic_star"->resources.getDrawable(R.drawable.ic_star, theme)
                        "ic_bookmark_stark"->resources.getDrawable(R.drawable.ic_bookmark_stark, theme)
                        "ic_arrow_cool_down"->resources.getDrawable(R.drawable.ic_arrow_cool_down, theme)
                        else->resources.getDrawable(android.R.drawable.ic_menu_mylocation, theme)
                    }.mutate()
                    if (m.id == selectedMarkerId) {
                        icon.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                    } else if (m.color != -1) {
                        icon.setColorFilter(m.color, PorterDuff.Mode.SRC_IN)
                    }
                    this.icon = icon
                } else icon = resources.getDrawable(android.R.color.transparent, theme)
                
                infoWindow = object : MarkerInfoWindow(R.layout.modern_info_window, mapView) {
                    override fun onOpen(item: Any?) {
                        super.onOpen(item)
                        val mm = item as OsmMarker
                        val c = String.format(Locale.US, "%.6f, %.6f", mm.position.latitude, mm.position.longitude)
                        val subDesc = mView.findViewById<TextView>(R.id.bubble_subdescription)
                        subDesc.text = c
                        subDesc.visibility = View.VISIBLE
                        
                        val btnCopy = mView.findViewById<Button>(R.id.bubble_copy_coords)
                        btnCopy.visibility = View.VISIBLE
                        btnCopy.setOnClickListener { copyToClipboard(c) }
                        
                        mView.findViewById<TextView>(R.id.bubble_title).text = mm.title
                        
                        val btnNav = Button(this@MainActivity).apply {
                            text = getString(R.string.navigation)
                            setOnClickListener { startNav(m); close() }
                        }
                        (mView as ViewGroup).addView(btnNav)

                        val descView = mView.findViewById<TextView>(R.id.bubble_description)
                        if (!mm.snippet.isNullOrBlank()) {
                            descView.text = mm.snippet
                            descView.visibility = View.VISIBLE
                        } else {
                            descView.visibility = View.GONE
                        }
                    }
                }
                setOnMarkerClickListener { _, _ -> showMarkerInfoDialog(m); true }
            }
            mapView.overlays.add(osm)
        }
        mapView.invalidate()
    }

    private fun showMarkerInfoDialog(marker: Marker) {
        lifecycleScope.launch(Dispatchers.IO) {
            val addr = try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(marker.latitude, marker.longitude, 1)?.get(0)?.getAddressLine(0) ?: getString(R.string.address_not_found)
            } catch (e: Exception) { getString(R.string.error_obtaining_address) }

            withContext(Dispatchers.Main) {
                val dist = locationOverlay.myLocation?.let { 
                    val r = FloatArray(1)
                    android.location.Location.distanceBetween(it.latitude, it.longitude, marker.latitude, marker.longitude, r)
                    if (r[0] >= 1000) String.format(Locale.getDefault(), "%.1f км", r[0] / 1000) 
                    else String.format(Locale.getDefault(), "%d м", r[0].toInt())
                } ?: "---"
                
                val builder = AlertDialog.Builder(this@MainActivity, R.style.ModernDialog)
                    .setTitle(marker.name)
                    .setMessage("Адрес: $addr\n\n${marker.description}\n\n${getString(R.string.distance_label, dist)}")
                    .setPositiveButton(R.string.navigator) { _, _ -> startNav(marker) }
                    .setNeutralButton(R.string.share) { _, _ -> shareMarkerText(marker) }
                    .setNegativeButton(R.string.edit) { _, _ -> showEditMarkerDialog(marker) }

                if (marker.photoPath != null && File(marker.photoPath).exists()) {
                    val iv = ImageView(this@MainActivity).apply { 
                        setImageBitmap(BitmapFactory.decodeFile(marker.photoPath))
                        setPadding(20, 20, 20, 20)
                        adjustViewBounds = true
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600)
                    }
                    builder.setView(iv)
                }
                builder.show()
            }
        }
    }

    private fun startNav(m: Marker) {
        try {
            val uri = Uri.parse("google.navigation:q=${m.latitude},${m.longitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            mapIntent.setPackage("com.google.android.apps.maps")
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${m.latitude},${m.longitude}?q=${m.latitude},${m.longitude}(${m.name})")))
            }
            
            // Draw a polyline on the map to the target
            drawNavigationLine(GeoPoint(m.latitude, m.longitude))
        } catch (e: Exception) {
            Toast.makeText(this, "Навигатор не найден", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawNavigationLine(target: GeoPoint) {
        navigationTarget = target
        navigationPolyline?.let { mapView.overlays.remove(it) }
        
        val line = Polyline(mapView)
        line.outlinePaint.apply {
            color = Color.BLUE
            strokeWidth = 8f
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
            alpha = 150
        }
        
        navigationPolyline = line
        mapView.overlays.add(line)
        updateNavigationLine()
    }

    private fun updateNavigationLine() {
        val myPos = locationOverlay.myLocation ?: return
        val target = navigationTarget ?: return
        val line = navigationPolyline ?: return
        
        line.setPoints(listOf(myPos, target))
        mapView.invalidate()
    }

    private fun copyToClipboard(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Coords", text))
        Toast.makeText(this, getString(R.string.copy_coords), Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    private fun shareMarkerText(m: Marker) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, "${m.name}\n${m.description}\n${m.latitude}, ${m.longitude}") }, "Поделиться"))
    }

    private fun createModernAdapter(items: Array<String>) = object : ArrayAdapter<String>(this, R.layout.modern_spinner_item, items) {
        override fun getView(pos: Int, conv: View?, parent: ViewGroup): View {
            val v = super.getView(pos, conv, parent)
            v.findViewById<TextView>(android.R.id.text1).apply {
                setTextColor(Color.BLACK)
                textSize = 15f 
            }
            return v
        }
    }

    private fun restoreMapPos() {
        val p = getPreferences(Context.MODE_PRIVATE)
        val lat = p.getFloat("last_lat", 55.751244f).toDouble()
        val lon = p.getFloat("last_lon", 37.618423f).toDouble()
        val zoom = p.getFloat("last_zoom", 10f).toDouble()
        mapView.controller.setCenter(GeoPoint(lat, lon))
        mapView.controller.setZoom(zoom)
    }

    private fun saveMapPos() {
        val c = mapView.mapCenter
        getPreferences(Context.MODE_PRIVATE).edit()
            .putFloat("last_lat", c.latitude.toFloat())
            .putFloat("last_lon", c.longitude.toFloat())
            .putFloat("last_zoom", mapView.zoomLevelDouble.toFloat())
            .apply()
    }

    override fun onPause() { super.onPause(); saveMapPos(); mapView.onPause() }
    override fun onResume() { super.onResume(); mapView.onResume() }

    private fun exportData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("folders", JSONArray(viewModel.allFolders.value?.map { f -> JSONObject().apply { put("name", f.name); put("isVisible", f.isVisible); put("showMarkerLabels", f.showMarkerLabels); put("showMarkerIcons", f.showMarkerIcons); put("defaultIconType", f.defaultIconType) } } ?: emptyList<JSONObject>()))
                    put("markers", JSONArray(viewModel.allMarkers.value?.map { m -> JSONObject().apply { put("name", m.name); put("description", m.description); put("latitude", m.latitude); put("longitude", m.longitude); put("folderName", viewModel.allFolders.value?.find { it.id == m.folderId }?.name ?: "Default"); put("iconType", m.iconType); put("color", m.color); put("showLabel", m.showLabel) } } ?: emptyList<JSONObject>()))
                }
                contentResolver.openOutputStream(uri)?.use { it.write(json.toString().toByteArray()) }
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, getString(R.string.done), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {}
        }
    }

    private fun importData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject(contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "")
                val fMap = mutableMapOf<String, Long>()
                json.optJSONArray("folders")?.let { arr -> for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val name = o.getString("name"); fMap[name] = viewModel.insertFolderSync(name) } }
                json.optJSONArray("markers")?.let { arr -> for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i); val fName = o.optString("folderName", "Default")
                    val fId = fMap[fName] ?: viewModel.insertFolderSync(fName).also { fMap[fName] = it }
                    withContext(Dispatchers.Main) { viewModel.insertMarker(o.getString("name"), o.getString("description"), o.getDouble("latitude"), o.getDouble("longitude"), fId, o.optString("iconType", "default"), o.optInt("color", -1), o.optBoolean("showLabel", true)) }
                } }
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, getString(R.string.import_finished), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {}
        }
    }

    private fun exportKmz(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val kml = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>")
                viewModel.allFolders.value?.forEach { f ->
                    kml.append("<Folder><name>${f.name}</name>")
                    viewModel.allMarkers.value?.filter { it.folderId == f.id }?.forEach { m -> kml.append("<Placemark><name>${m.name}</name><description>${m.description}</description><Point><coordinates>${m.longitude},${m.latitude},0</coordinates></Point></Placemark>") }
                    kml.append("</Folder>")
                }
                kml.append("</Document></kml>")
                contentResolver.openOutputStream(uri)?.use { os -> ZipOutputStream(os).use { z -> z.putNextEntry(ZipEntry("doc.kml")); z.write(kml.toString().toByteArray()); z.closeEntry() } }
                withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, getString(R.string.export_finished), Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {}
        }
    }

    private fun importKmz(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            var importedCount = 0
            var errorCount = 0
            
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val zis = ZipInputStream(stream)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".kml", true)) {
                            try {
                                val content = zis.bufferedReader().readText()
                                val folderName = entry.name.removeSuffix(".kml").substringAfterLast("/").ifBlank { "Imported KMZ" }
                                val fId = viewModel.insertFolderSync(folderName)
                                val count = parseKml(content, fId)
                                importedCount += count
                            } catch (e: Exception) {
                                errorCount++
                                e.printStackTrace()
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                
                withContext(Dispatchers.Main) {
                    when {
                        errorCount > 0 && importedCount > 0 -> 
                            Toast.makeText(this@MainActivity, "Импортировано: $importedCount, Ошибок: $errorCount", Toast.LENGTH_LONG).show()
                        errorCount > 0 -> 
                            Toast.makeText(this@MainActivity, "Ошибка импорта: проверьте формат файла", Toast.LENGTH_LONG).show()
                        else -> 
                            Toast.makeText(this@MainActivity, getString(R.string.import_finished), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Парсит KML содержимое и добавляет метки в базу данных
     * @param content XML содержимое KML файла
     * @param folderId ID папки для добавления меток
     * @return Количество успешно импортированных меток
     */
    private fun parseKml(content: String, folderId: Long): Int {
        var importedCount = 0
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            val xpp = factory.newPullParser()
            xpp.setInput(content.reader())
            var eventType = xpp.eventType
            var currentName = ""
            var currentDesc = ""
            var currentCoords = ""
            var inPlacemark = false
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = xpp.name
                        if (currentTag == "Placemark") {
                            inPlacemark = true
                            currentName = "Метка"
                            currentDesc = ""
                            currentCoords = ""
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inPlacemark) {
                            when (currentTag) {
                                "name" -> currentName = xpp.text ?: "Метка"
                                "description" -> currentDesc = xpp.text ?: ""
                                "coordinates" -> currentCoords = xpp.text ?: ""
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (xpp.name == "Placemark") {
                            inPlacemark = false
                            val coords = currentCoords.trim().split(",")
                            if (coords.size >= 2) {
                                val lon = coords[0].toDoubleOrNull()
                                val lat = coords[1].toDoubleOrNull()
                                
                                // Валидация координат
                                if (lat != null && lon != null && 
                                    lat in -90.0..90.0 && lon in -180.0..180.0) {
                                    viewModel.insertMarker(
                                        name = currentName.takeIf { it.isNotBlank() } ?: "Без названия",
                                        description = currentDesc,
                                        lat = lat,
                                        lon = lon,
                                        folderId = folderId
                                    )
                                    importedCount++
                                }
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = xpp.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
        
        return importedCount
    }
}
