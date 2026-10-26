package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VoiceClip
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceClipDao {
    @Query("SELECT * FROM voice_clips ORDER BY createdAt DESC")
    fun getAllClips(): Flow<List<VoiceClip>>

    @Query("SELECT * FROM voice_clips WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteClips(): Flow<List<VoiceClip>>

    @Query("SELECT * FROM voice_clips WHERE id = :id LIMIT 1")
    suspend fun getClipById(id: Long): VoiceClip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: VoiceClip): Long

    @Update
    suspend fun updateClip(clip: VoiceClip)

    @Delete
    suspend fun deleteClip(clip: VoiceClip)

    @Query("UPDATE voice_clips SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM voice_clips WHERE id = :id")
    suspend fun deleteById(id: Long)
}
