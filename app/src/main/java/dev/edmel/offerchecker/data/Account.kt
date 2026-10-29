package dev.edmel.offerchecker.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * The nah.meter account: sign-in by email link, and the licence (a 14-day trial, then paid or free
 * months). This is the app's only network use, and only with edmel.dev: offers and anything read on
 * screen are never sent.
 */
data class Licence(
    val status: String,
    val trialEndsAt: Long,
    val paidUntil: Long?,
    /** Verdicts are allowed until this moment (epoch ms): the later of the trial's end and the paid date. */
    val readUntil: Long,
    val payCode: String,
    val priceCents: Int,
    val payEmail: String,
    val contactEmail: String,
    val checkedAt: Long,
)

data class AccountState(
    val email: String? = null,
    val signedIn: Boolean = false,
    val licence: Licence? = null,
) {
    /** Signed in, and the trial or a paid month covers [now]. Works offline from the cached dates. */
    fun canRead(now: Long = System.currentTimeMillis()): Boolean = signedIn && licence != null && now < licence.readUntil
}

/** A failed call, with a message meant for the screen. [signedOut] means the session is gone. */
class AccountException(message: String, val signedOut: Boolean = false) : Exception(message)

private val Context.accountStore by preferencesDataStore(name = "account")

class AccountRepository(context: Context) {
    private val store = context.applicationContext.accountStore

    private object Keys {
        val email = stringPreferencesKey("email")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val expiresAt = longPreferencesKey("expires_at")
        val status = stringPreferencesKey("status")
        val trialEndsAt = longPreferencesKey("trial_ends_at")
        val paidUntil = longPreferencesKey("paid_until")
        val readUntil = longPreferencesKey("read_until")
        val payCode = stringPreferencesKey("pay_code")
        val priceCents = intPreferencesKey("price_cents")
        val payEmail = stringPreferencesKey("pay_email")
        val contactEmail = stringPreferencesKey("contact_email")
        val checkedAt = longPreferencesKey("checked_at")
    }

    val state: Flow<AccountState> = store.data.map { p ->
        AccountState(
            email = p[Keys.email],
            signedIn = p[Keys.refreshToken] != null,
            licence = p[Keys.readUntil]?.let { readUntil ->
                Licence(
                    status = p[Keys.status] ?: "trial",
                    trialEndsAt = p[Keys.trialEndsAt] ?: 0L,
                    paidUntil = p[Keys.paidUntil],
                    readUntil = readUntil,
                    payCode = p[Keys.payCode] ?: "",
                    priceCents = p[Keys.priceCents] ?: 500,
                    payEmail = p[Keys.payEmail] ?: "",
                    contactEmail = p[Keys.contactEmail] ?: "contact@edmel.dev",
                    checkedAt = p[Keys.checkedAt] ?: 0L,
                )
            },
        )
    }

    /** Emails a sign-in link to [email]. */
    suspend fun startSignIn(email: String) {
        Api.post("auth/start", JSONObject().put("email", email.trim()))
        store.edit { it[Keys.email] = email.trim().lowercase() }
    }

    /** Finishes sign-in with the token from the email link (the link opens the app). */
    suspend fun completeSignIn(tokenHash: String): Licence = lock.withLock {
        val json = Api.post("auth/verify", JSONObject().put("token_hash", tokenHash))
        val licence = parseLicence(json.getJSONObject("licence"))
        store.edit { p ->
            p.putSession(json)
            p.putLicence(licence)
            p[Keys.email] = json.getJSONObject("licence").optString("email", p[Keys.email] ?: "")
        }
        licence
    }

    /**
     * Asks edmel.dev for the current dates (refreshing the session if needed) and saves them. On a
     * network error the cached dates stay, so a shift isn't interrupted.
     */
    suspend fun refreshLicence(): Licence = lock.withLock {
        val token = freshAccessToken()
        val json = try {
            Api.get("licence", token)
        } catch (e: AccountException) {
            if (!e.signedOut) throw e
            Api.get("licence", freshAccessToken(force = true))
        }
        val licence = parseLicence(json)
        store.edit { it.putLicence(licence) }
        licence
    }

    suspend fun signOut() {
        store.edit { it.clear() }
    }

    /** Deletes the account on edmel.dev (sign-in, dates, payments), then signs out here. */
    suspend fun deleteAccount() {
        lock.withLock {
            Api.post("account/delete", JSONObject(), freshAccessToken())
            store.edit { it.clear() }
        }
    }

    /** The access token, refreshed first when it's about to expire (or [force]). Signs out if refreshing is refused. */
    private suspend fun freshAccessToken(force: Boolean = false): String {
        val p = store.data.first()
        val refresh = p[Keys.refreshToken] ?: throw AccountException("Sign in first.", signedOut = true)
        val access = p[Keys.accessToken]
        val expiresAt = p[Keys.expiresAt] ?: 0L
        if (!force && access != null && System.currentTimeMillis() / 1000 < expiresAt - 60) return access
        val json = try {
            Api.post("auth/refresh", JSONObject().put("refresh_token", refresh))
        } catch (e: AccountException) {
            if (e.signedOut) store.edit { it.clear() }
            throw e
        }
        store.edit { it.putSession(json) }
        return json.getString("accessToken")
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putSession(json: JSONObject) {
        this[Keys.accessToken] = json.getString("accessToken")
        this[Keys.refreshToken] = json.getString("refreshToken")
        this[Keys.expiresAt] = json.optLong("expiresAt", 0L)
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putLicence(l: Licence) {
        this[Keys.status] = l.status
        this[Keys.trialEndsAt] = l.trialEndsAt
        if (l.paidUntil != null) this[Keys.paidUntil] = l.paidUntil else remove(Keys.paidUntil)
        this[Keys.readUntil] = l.readUntil
        this[Keys.payCode] = l.payCode
        this[Keys.priceCents] = l.priceCents
        this[Keys.payEmail] = l.payEmail
        this[Keys.contactEmail] = l.contactEmail
        this[Keys.checkedAt] = l.checkedAt
    }

    private fun parseLicence(j: JSONObject): Licence {
        fun time(key: String) = Instant.parse(j.getString(key)).toEpochMilli()
        return Licence(
            status = j.getString("status"),
            trialEndsAt = time("trialEndsAt"),
            paidUntil = if (j.isNull("paidUntil")) null else time("paidUntil"),
            readUntil = time("readUntil"),
            payCode = j.getString("payCode"),
            priceCents = j.optInt("priceCents", 500),
            payEmail = j.optString("payEmail"),
            contactEmail = j.optString("contactEmail", "contact@edmel.dev"),
            checkedAt = System.currentTimeMillis(),
        )
    }

    private companion object {
        /** One sign-in or refresh at a time across the app and the reader (refresh tokens work once). */
        val lock = Mutex()
    }

    /** edmel.dev's account endpoints, over HTTPS. */
    private object Api {
        const val BASE = "https://edmel.dev/api/nahmeter/"

        suspend fun get(path: String, token: String): JSONObject = call("GET", path, null, token)
        suspend fun post(path: String, body: JSONObject, token: String? = null): JSONObject = call("POST", path, body, token)

        private suspend fun call(method: String, path: String, body: JSONObject?, token: String?): JSONObject =
            withContext(Dispatchers.IO) {
                val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Accept", "application/json")
                    if (token != null) setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                    }
                }
                try {
                    if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray()) }
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                    if (code !in 200..299) {
                        throw AccountException(json.optString("error").ifEmpty { "Something went wrong ($code)." }, signedOut = code == 401)
                    }
                    json
                } catch (e: IOException) {
                    throw AccountException("No connection. Check your internet and try again.")
                } finally {
                    conn.disconnect()
                }
            }
    }
}
