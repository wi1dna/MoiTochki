package com.example.moitochki

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    // Folders
    @Query("SELECT * FROM folders")
    fun getAllFolders(): Flow<List<Folder>>

    @Insert
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("UPDATE folders SET isVisible = :isVisible WHERE id = :folderId")
    suspend fun updateFolderVisibility(folderId: Long, isVisible: Boolean)

    // Markers
    @Query("SELECT * FROM markers")
    fun getAllMarkers(): Flow<List<Marker>>

    @Query("SELECT * FROM markers WHERE folderId = :folderId")
    fun getMarkersInFolder(folderId: Long): Flow<List<Marker>>
    
    @Query("SELECT * FROM markers WHERE folderId = :folderId")
    suspend fun getMarkersInFolderSync(folderId: Long): List<Marker>

    @Insert
    suspend fun insertMarker(marker: Marker): Long

    @Update
    suspend fun updateMarker(marker: Marker)

    @Delete
    suspend fun deleteMarker(marker: Marker)
    
    @Query("SELECT * FROM markers WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchMarkers(query: String): Flow<List<Marker>>
}
