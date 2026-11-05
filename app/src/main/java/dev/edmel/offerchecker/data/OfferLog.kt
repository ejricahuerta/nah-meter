package dev.edmel.offerchecker.data

import android.content.Context
import androidx.room.ColumnInfo
import dev.edmel.offerchecker.core.OfferParser
import dev.edmel.offerchecker.core.samePlace
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offers")
data class OfferEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val pay: Double,
    /** The minutes the rates were worked out on: Uber's, plus the wait buffer, pickup waits and drive back. */
    val minutes: Int,
    val km: Double,
    val hourly: Double,
    val perKm: Double?,
    val verdict: String,
    val stacked: Boolean,
    /** The restaurant, e.g. "Lazeez Shawarma (2900 Steeles)", if it could be read. */
    val pickup: String? = null,
    /** Where it goes, e.g. "Annibale Dr & Bembridge Dr, Markham", if it could be read. */
    val dropoff: String? = null,
    /** How many orders the card bundled ("Delivery (3)" = 3). */
    @ColumnInfo(defaultValue = "1") val orders: Int = if (stacked) 2 else 1,
    /** An "Add a delivery" card: pay, minutes and km are what it adds to the current trip. */
    @ColumnInfo(defaultValue = "0") val addOn: Boolean = false,
    /** Uber's own minutes, as the card showed them; null on offers logged before v4. */
    val cardMinutes: Int? = null,
    /** The unpaid km added for the drive back; null when none, or logged before v4. */
    val extraKm: Double? = null,
    /** The dollars taken off the pay for tips that may not come; null when none, or logged before v4. */
    val tipCushion: Double? = null,
    /** Minutes since Uber last paid (or since going online), when known; null before v5. */
    val idleMin: Int? = null,
    /** The minutes added for the drive back; null when none, or logged before v5. */
    val returnMin: Int? = null,
    /** The drop-off area the drive back came from ("Stouffville"), under the idle-aware rules. */
    val returnArea: String? = null,
    /** The idle-aware $/hr bar; null when those rules were off, or logged before v5. */
    val barHourly: Double? = null,
    /** The idle-aware rules' verdict (shown, or compared); null when they were off, or logged before v5. */
    val idleVerdict: String? = null,
) {
    /**
     * Same offer: same pay, minutes, km and order count (time and verdict aside), and the same
     * restaurant when both were read.
     */
    fun sameOfferAs(o: OfferEntry) = pay == o.pay && minutes == o.minutes && km == o.km && orders == o.orders &&
        addOn == o.addOn && samePlace(pickup, o.pickup)

    /**
     * The same card read again with other numbers: within [OfferDao.SAME_CARD_MS], the same
     * restaurant and drop-off (both read) and order count. Text recognition varies between reads.
     */
    fun sameCardAs(o: OfferEntry) = kotlin.math.abs(time - o.time) <= OfferDao.SAME_CARD_MS &&
        pickup != null && o.pickup != null && dropoff != null && o.dropoff != null &&
        orders == o.orders && addOn == o.addOn && samePlace(pickup, o.pickup) && samePlace(dropoff, o.dropoff)
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
     * app and come back, or when it re-offers it), and that is still one offer. So is the same
     * card read again within [SAME_CARD_MS] with other numbers ([OfferEntry.sameCardAs]). Keeps
     * only the last [MAX_OFFERS]. Returns whether it was logged.
     */
    @Transaction
    open suspend fun addIfNew(entry: OfferEntry): Boolean {
        if (latest()?.sameOfferAs(entry) == true) return false
        val recent = since(entry.time - REPEAT_WINDOW_MS)
        if (recent.any { it.sameOfferAs(entry) || it.sameCardAs(entry) }) return false
        insert(entry)
        trim(MAX_OFFERS)
        return true
    }

    companion object {
        /** About two weeks of shifts; 200 kept less than one night (2026-11-04: 7:30pm on only). */
        const val MAX_OFFERS = 2000
        const val REPEAT_WINDOW_MS = 30 * 60 * 1000L
        const val SAME_CARD_MS = 60 * 1000L
    }
}

@Database(entities = [OfferEntry::class], version = 5, exportSchema = false)
abstract class OfferDatabase : RoomDatabase() {
    abstract fun offers(): OfferDao

    companion object {
        @Volatile private var instance: OfferDatabase? = null

        /** v5: idle time, drive back, the idle-aware bar and verdict. Existing offers have none (null). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN idleMin INTEGER")
                db.execSQL("ALTER TABLE offers ADD COLUMN returnMin INTEGER")
                db.execSQL("ALTER TABLE offers ADD COLUMN returnArea TEXT")
                db.execSQL("ALTER TABLE offers ADD COLUMN barHourly REAL")
                db.execSQL("ALTER TABLE offers ADD COLUMN idleVerdict TEXT")
            }
        }

        /** v4: Uber's own minutes, the drive-back km and the tip cushion. Existing offers have none (null). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN cardMinutes INTEGER")
                db.execSQL("ALTER TABLE offers ADD COLUMN extraKm REAL")
                db.execSQL("ALTER TABLE offers ADD COLUMN tipCushion REAL")
            }
        }

        /** v3: order count and add-on flag. Existing stacked offers count as 2 orders. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN orders INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE offers ADD COLUMN addOn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE offers SET orders = 2 WHERE stacked = 1")
            }
        }

        /** v2: the restaurant and drop-off area. Existing offers keep their rows, with no places. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE offers ADD COLUMN pickup TEXT")
                db.execSQL("ALTER TABLE offers ADD COLUMN dropoff TEXT")
            }
        }

        fun get(context: Context): OfferDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OfferDatabase::class.java, "offers.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}

/**
 * The offer log as CSV. minutes and km are Uber's, as on the card (minutes is blank for offers
 * logged before v4); scored_minutes, extra_km and tip_cushion are what the rates were worked out
 * on, so dollars_per_hr = (pay − tip_cushion) × 60 / scored_minutes and
 * dollars_per_km = (pay − tip_cushion) / (km + extra_km). The effective rates: they already count
 * the drive back (return_min of scored_minutes, from return_area when the idle-aware rules set it).
 * idle_min is the minutes since Uber last paid; threshold_hr and idle_verdict are the idle-aware
 * rules' bar and verdict (blank when they were off). suspect marks rows that look misread
 * ([OfferParser.suspect]). Columns added later go at the end.
 */
object OfferCsv {
    /** Quotes a text field; addresses have commas. */
    private fun csv(s: String?): String = if (s == null) "" else "\"" + s.replace("\"", "\"\"") + "\""

    fun write(entries: List<OfferEntry>): String = buildString {
        appendLine(
            "time,pay,minutes,km,scored_minutes,extra_km,tip_cushion,dollars_per_hr,dollars_per_km,verdict,orders,add_on,restaurant,dropoff," +
                "idle_min,return_min,return_area,threshold_hr,idle_verdict,suspect",
        )
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        for (e in entries) {
            // Offers logged before v4 don't know their drive back or cushion, so those stay blank, not 0.
            val v4 = e.cardMinutes != null
            val extraKm = e.extraKm ?: if (v4) 0.0 else null
            val cushion = e.tipCushion ?: if (v4) 0.0 else null
            appendLine(
                listOf(
                    fmt.format(java.util.Date(e.time)),
                    "%.2f".format(java.util.Locale.US, e.pay),
                    e.cardMinutes?.toString() ?: "",
                    e.km,
                    e.minutes,
                    extraKm?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
                    cushion?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
                    "%.2f".format(java.util.Locale.US, e.hourly),
                    e.perKm?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
                    e.verdict,
                    e.orders,
                    e.addOn,
                    csv(e.pickup),
                    csv(e.dropoff),
                    e.idleMin?.toString() ?: "",
                    e.returnMin?.toString() ?: "",
                    csv(e.returnArea),
                    e.barHourly?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
                    e.idleVerdict ?: "",
                    OfferParser.suspect(e.perKm?.toBigDecimal(), e.pickup, e.dropoff),
                ).joinToString(","),
            )
        }
    }
}
