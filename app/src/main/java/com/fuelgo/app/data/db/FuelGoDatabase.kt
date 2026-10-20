package com.fuelgo.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StationEntity::class, ReportEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FuelGoDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao
    abstract fun reportDao(): ReportDao

    companion object {
        fun build(context: Context): FuelGoDatabase =
            Room.databaseBuilder(context, FuelGoDatabase::class.java, "fuelgo.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
