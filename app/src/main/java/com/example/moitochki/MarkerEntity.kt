package com.example.moitochki

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isVisible: Boolean = true,
    val showMarkerLabels: Boolean = true,
    val showMarkerIcons: Boolean = true,
    val defaultIconType: String = "default"
)

@Entity(
    tableName = "markers",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Marker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val folderId: Long,
    val iconType: String = "default",
    val color: Int = -1,
    val showLabel: Boolean = true,
    val photoPath: String? = null
)
