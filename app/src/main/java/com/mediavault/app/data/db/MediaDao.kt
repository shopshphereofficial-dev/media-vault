package com.mediavault.app.data.db

import androidx.room.*
import com.mediavault.app.data.model.MediaItem
import com.mediavault.app.data.model.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isVaulted = 0 ORDER BY addedTimestamp DESC")
    fun getAllMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE mediaType = :type AND isVaulted = 0 ORDER BY addedTimestamp DESC")
    fun getMediaByType(type: MediaType): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isVaulted = 1 ORDER BY addedTimestamp DESC")
    fun getVaultMedia(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 AND isVaulted = 0 ORDER BY addedTimestamp DESC")
    fun getFavorites(): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE title LIKE '%' || :query || '%' AND isVaulted = 0")
    fun searchMedia(query: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaById(id: Long): MediaItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: MediaItem): Long

    @Update
    suspend fun updateMedia(item: MediaItem)

    @Delete
    suspend fun deleteMedia(item: MediaItem)

    @Query("UPDATE media_items SET isVaulted = :vaulted WHERE id = :id")
    suspend fun setVaulted(id: Long, vaulted: Boolean)

    @Query("UPDATE media_items SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)
}
