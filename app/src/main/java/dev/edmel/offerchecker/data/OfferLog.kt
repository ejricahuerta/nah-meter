package dev.edmel.offerchecker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offers")
data class OfferEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val pay: Double,
    /** Uber's minutes plus the wait buffer, as scored. */
    val minutes: Int,
    val km: Double,
    val hourly: Double,
    val perKm: Double?,
    val verdict: String,
    val stacked: Boolean,
) {
    /** Same offer: same pay, minutes, km and order count (time and verdict aside). */
    fun sameOfferAs(o: OfferEntry) = pay == o.pay && minutes == o.minutes && km == o.km && stacked == o.stacked
}

@Dao
abstract class OfferDao {
    @Query("SELECT * FROM offers ORDER BY time DESC")
    abstract fun observeAll(): Flow<List<OfferEntry>>

    @Query("SELECT * FROM offers ORDER BY time DESC")
    abstract suspend fun all(): List<OfferEntry>

    @Insert
    abstract suspend fun insert(entry: OfferEntry)

    @Query("DELETE FROM offers WHERE id NOT IN (SELECT id FROM offers ORDER BY time DESC LIMIT :keep)")
    abstract suspend fun trim(keep: Int)

    @Query("DELETE FROM offers")
    abstract suspend fun clear()

    @Query("SELECT * FROM offers ORDER BY time DESC LIMIT 1")
    abstract suspend fun latest(): OfferEntry?

    @Query("SELECT * FROM offers WHERE time >= :since")
    abstract suspend fun since(since: Long): List<OfferEntry>

    /**
     * Logs [entry] unless it's an offer already logged: the same as the last one logged, or as any
     * logged in the last [REPEAT_WINDOW_MS]. Uber shows the same card again (after you close the
     * app and come back, or when it re-offers it), and that is still one offer. Keeps only the
     * last [MAX_OFFERS]. Returns whether it was logged.
     */
    @Transaction
    open suspend fun addIfNew(entry: OfferEntry): Boolean {
        if (latest()?.sameOfferAs(entry) == true) return false
        if (since(entry.time - REPEAT_WINDOW_MS).any { it.sameOfferAs(entry) }) return false
        insert(entry)
        trim(MAX_OFFERS)
        return true
    }

    companion object {
        const val MAX_OFFERS = 200
        const val REPEAT_WINDOW_MS = 30 * 60 * 1000L
    }
}

@Database(entities = [OfferEntry::class], version = 1, exportSchema = false)
abstract class OfferDatabase : RoomDatabase() {
    abstract fun offers(): OfferDao

    companion object {
        @Volatile private var instance: OfferDatabase? = null

        fun get(context: Context): OfferDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfferDatabase::class.java, "offers.db")
                .build().also { instance = it }
        }
    }
}

object OfferCsv {
    fun write(entries: List<OfferEntry>): String = buildString {
        appendLine("time,pay,minutes,km,dollars_per_hr,dollars_per_km,verdict,stacked")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        for (e in entries) {
            appendLine(
                listOf(
                    fmt.format(java.util.Date(e.time)),
                    "%.2f".format(java.util.Locale.US, e.pay),
                    e.minutes,
                    e.km,
                    "%.2f".format(java.util.Locale.US, e.hourly),
                    e.perKm?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
                    e.verdict,
                    e.stacked,
                ).joinToString(","),
            )
        }
    }
}
