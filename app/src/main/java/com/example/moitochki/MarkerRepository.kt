package com.example.moitochki

import kotlinx.coroutines.flow.Flow

class MarkerRepository(private val markerDao: MarkerDao) {
    val allMarkers: Flow<List<Marker>> = markerDao.getAllMarkers()
    val allFolders: Flow<List<Folder>> = markerDao.getAllFolders()

    suspend fun insertMarker(marker: Marker) = markerDao.insertMarker(marker)
    suspend fun updateMarker(marker: Marker) = markerDao.updateMarker(marker)
    suspend fun deleteMarker(marker: Marker) = markerDao.deleteMarker(marker)

    suspend fun insertFolder(folder: Folder) = markerDao.insertFolder(folder)
    suspend fun updateFolder(folder: Folder) = markerDao.updateFolder(folder)
    suspend fun deleteFolderById(folderId: Long) = markerDao.deleteFolderById(folderId)
    suspend fun updateFolderVisibility(folderId: Long, isVisible: Boolean) = markerDao.updateFolderVisibility(folderId, isVisible)
    
    fun getMarkersInFolder(folderId: Long) = markerDao.getMarkersInFolder(folderId)
    suspend fun getMarkersInFolderSync(folderId: Long) = markerDao.getMarkersInFolderSync(folderId)
    
    fun searchMarkers(query: String) = markerDao.searchMarkers(query)
}
