package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.VoiceClip

@Database(entities = [VoiceClip::class], version = 1, exportSchema = false)
abstract class VoiceStudioDatabase : RoomDatabase() {
    abstract fun voiceClipDao(): VoiceClipDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceStudioDatabase? = null

        fun getDatabase(context: Context): VoiceStudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceStudioDatabase::class.java,
                    "voix_studio_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
