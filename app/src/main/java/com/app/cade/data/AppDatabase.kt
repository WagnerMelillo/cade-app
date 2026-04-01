package com.app.cade.data

import android.content.Context

class AppDatabase {
    private val mockDao = ContactDao()
    
    fun contactDao(): ContactDao = mockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase()
                INSTANCE = instance
                instance
            }
        }
    }
}
