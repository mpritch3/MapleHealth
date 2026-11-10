package com.example.medtrackv1.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.math.abs
import kotlin.math.ln
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.medtrackv1.data.model.Counter
import com.example.medtrackv1.data.model.CounterHit
import com.example.medtrackv1.data.model.DoseLog
import com.example.medtrackv1.data.model.Medication

@Database(
    entities = [Medication::class, DoseLog::class, Counter::class, CounterHit::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MedTrackDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun counterDao(): CounterDao

    companion object {
        @Volatile
        private var INSTANCE: MedTrackDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN groupName TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN halfLifeMinHours REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN halfLifeMaxHours REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN tmaxMinHours REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN tmaxMaxHours REAL")

                val oldParameters = mutableListOf<Triple<Long, Double, Double>>()
                db.query("SELECT id, halfLifeHours, absorptionTimeHours FROM medications").use { cursor ->
                    while (cursor.moveToNext()) {
                        oldParameters += Triple(cursor.getLong(0), cursor.getDouble(1), cursor.getDouble(2))
                    }
                }
                oldParameters.forEach { (id, halfLifeHours, oldAbsorptionHalfTimeHours) ->
                    val ke = if (halfLifeHours > 0.0) ln(2.0) / halfLifeHours else 0.0
                    val ka = if (oldAbsorptionHalfTimeHours > 0.0) ln(2.0) / oldAbsorptionHalfTimeHours else 0.0
                    val tmaxHours = when {
                        ka <= 0.0 || ke <= 0.0 -> 0.0
                        abs(ka - ke) < 1e-12 -> 1.0 / ke
                        else -> ln(ka / ke) / (ka - ke)
                    }
                    db.execSQL(
                        "UPDATE medications SET absorptionTimeHours = ?, tmaxMinHours = ?, tmaxMaxHours = ? WHERE id = ?",
                        arrayOf<Any>(tmaxHours, tmaxHours, tmaxHours, id)
                    )
                }
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN scheduledTimes TEXT")
                db.execSQL("UPDATE medications SET scheduledTimes = scheduledTime WHERE scheduledTime IS NOT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS counters (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS counter_hits (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, counterId INTEGER NOT NULL, counterName TEXT NOT NULL, timestamp INTEGER NOT NULL, elapsedMillis INTEGER NOT NULL, concentrationsJson TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_counter_hits_counterId ON counter_hits (counterId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN groupDoseMultiplier INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): MedTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedTrackDatabase::class.java,
                    "medtrack_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
