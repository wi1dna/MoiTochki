package com.example.moitochki

import androidx.lifecycle.*
import kotlinx.coroutines.launch

class MapViewModel(private val repository: MarkerRepository) : ViewModel() {
    val allMarkers: LiveData<List<Marker>> = repository.allMarkers.asLiveData()
    val allFolders: LiveData<List<Folder>> = repository.allFolders.asLiveData()

    fun insertMarker(name: String, description: String, lat: Double, lon: Double, folderId: Long, iconType: String = "default", color: Int = -1, showLabel: Boolean = true, photoPath: String? = null) {
        viewModelScope.launch {
            repository.insertMarker(Marker(name = name, description = description, latitude = lat, longitude = lon, folderId = folderId, iconType = iconType, color = color, showLabel = showLabel, photoPath = photoPath))
        }
    }

    fun updateMarker(marker: Marker) {
        viewModelScope.launch {
            repository.updateMarker(marker)
        }
    }

    fun deleteMarker(marker: Marker) {
        viewModelScope.launch {
            repository.deleteMarker(marker)
        }
    }

    fun insertFolder(name: String) {
        viewModelScope.launch {
            repository.insertFolder(Folder(name = name))
        }
    }

    suspend fun insertFolderSync(name: String): Long {
        return repository.insertFolder(Folder(name = name))
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch {
            repository.updateFolder(folder)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolderById(folderId)
        }
    }

    fun updateFolderVisibility(folderId: Long, isVisible: Boolean) {
        viewModelScope.launch {
            repository.updateFolderVisibility(folderId, isVisible)
        }
    }

    suspend fun getMarkersInFolderSync(folderId: Long): List<Marker> {
        return repository.getMarkersInFolderSync(folderId)
    }

    fun searchMarkers(query: String): LiveData<List<Marker>> {
        return repository.searchMarkers(query).asLiveData()
    }
}

class MapViewModelFactory(private val repository: MarkerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
