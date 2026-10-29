package dev.edmel.offerchecker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.edmel.offerchecker.data.AccountRepository
import dev.edmel.offerchecker.data.AccountState
import dev.edmel.offerchecker.data.Licence
import dev.edmel.offerchecker.service.ServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

/**
 * The sign-in email's link opens the app (an Android App Link to edmel.dev, or `nahmeter://auth`).
 * MainActivity drops its token here and [FinishSignIn] completes it, wherever the app is.
 */
object SignInLink {
    val pending = MutableStateFlow<String?>(null)
    /** "Signing in…" while working, an error to show, or null. */
    val status = MutableStateFlow<String?>(null)

    fun tokenFrom(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val isAuth = (uri.scheme == "nahmeter" && uri.host == "auth") ||
            (uri.scheme == "https" && uri.path == "/nahmeter/auth/confirm")
        return if (isAuth) uri.getQueryParameter("token_hash")?.takeIf { it.isNotBlank() } else null
    }
}

/** Completes a sign-in link that opened the app. */
@Composable
fun FinishSignIn() {
    val ctx = LocalContext.current
    val repo = remember { AccountRepository(ctx) }
    val pending by SignInLink.pending.collectAsState()
    LaunchedEffect(pending) {
        val token = pending ?: return@LaunchedEffect
        SignInLink.status.value = "Signing in…"
        runCatching { repo.completeSignIn(token) }
            .onSuccess {
                SignInLink.status.value = null
                ServiceState.log("Signed in", "${it.status}, verdicts until ${day(it.readUntil)}")
            }
            .onFailure { SignInLink.status.value = it.message ?: "Couldn't sign in. Send a new link." }
        SignInLink.pending.value = null
    }
}

internal fun day(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

/** "Trial · 9 days left", "Paid until Dec 3, 2026" or "Ended Nov 12, 2026 · no verdicts". */
fun licenceLine(l: Licence?, now: Long = System.currentTimeMillis()): String = when {
    l == null -> "Not checked yet"
    now >= l.readUntil -> "Ended ${day(l.readUntil)} · no verdicts"
    l.paidUntil != null && l.paidUntil > now -> "Paid until ${day(l.paidUntil)}"
    else -> {
        val days = ceil((l.trialEndsAt - now) / 86_400_000.0).toInt().coerceAtLeast(1)
        "Trial · $days day${if (days == 1) "" else "s"} left"
    }
}

/** Needs attention: ended, or the trial ends within 3 days with nothing paid. */
fun licenceAlert(l: Licence?, now: Long = System.currentTimeMillis()): Boolean =
    l == null || now >= l.readUntil || (l.paidUntil == null && l.trialEndsAt - now < 3 * 86_400_000L)

/** Email → "Send sign-in link" → "Check your email"; the link itself opens the app. */
@Composable
fun SignInPanel(state: AccountState) {
    val ctx = LocalContext.current
    val repo = remember { AccountRepository(ctx) }
    val scope = rememberCoroutineScope()
    val c = Nah.c
    var email by rememberSaveable { mutableStateOf(state.email ?: "") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val linkStatus by SignInLink.status.collectAsState()

    if (state.signedIn) {
        NahGroup { NahRow(state.email ?: "Signed in", licenceLine(state.licence)) }
        return
    }

    fun send() {
        busy = true
        error = null
        scope.launch {
            runCatching { repo.startSignIn(email) }
                .onSuccess { sent = true }
                .onFailure { error = it.message ?: "Couldn't send the email. Try again." }
            busy = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!sent) {
            EmailField(email) { email = it; error = null }
            NahButton(
                if (busy) "Sending…" else "Send sign-in link", { send() },
                modifier = Modifier.fillMaxWidth(), enabled = !busy && looksLikeEmail(email),
            )
        } else {
            Text(
                "Check $email. Tap the link in it on this phone to sign in.",
                style = Nah.style(16.sp, 400, 24.sp, color = c.ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NahButton(if (busy) "Sending…" else "Send again", { send() }, kind = NahButtonKind.Secondary, small = true, enabled = !busy)
                NahButton("Use another email", { sent = false }, kind = NahButtonKind.Tertiary, small = true)
            }
        }
        (linkStatus ?: error)?.let {
            Text(it, style = Nah.style(15.sp, 500, 22.sp, color = if (it == linkStatus && it.endsWith("…")) c.inkMuted else c.nahInk))
        }
    }
}

private fun looksLikeEmail(s: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(s.trim())

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    val c = Nah.c
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = Nah.style(17.sp, 500, 24.sp, color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Email" },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
                    .border(1.5.dp, c.controlBorder, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text("you@example.com", style = Nah.style(17.sp, 400, 24.sp, color = c.inkMuted))
                inner()
            }
        },
    )
}

/** Shown instead of the app until the courier signs in: the trial and the licence need an account. */
@Composable
fun SignInScreen(state: AccountState, modifier: Modifier = Modifier) {
    val c = Nah.c
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Wordmark()
        Text("Sign in", style = Nah.title.copy(color = c.ink), modifier = Modifier.semantics { heading() })
        Text(
            "Your 14-day free trial and your licence are tied to your email. Offers you see never leave the phone.",
            style = Nah.style(16.sp, 400, 24.sp, color = c.inkMuted),
        )
        SignInPanel(state)
    }
}

/** How to pay: the e-Transfer address and the pay code, and asking for free months. */
@Composable
fun PayInstructions(l: Licence) {
    val ctx = LocalContext.current
    val c = Nah.c
    val price = "$" + (l.priceCents / 100)
    NahGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Send $price per month by Interac e-Transfer to ${l.payEmail}, with ${l.payCode} in the message. " +
                    "The months are added when it arrives, usually within a day.",
                style = Nah.style(16.sp, 400, 24.sp, color = c.ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NahButton(
                    "Copy details",
                    {
                        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("nah.meter e-Transfer", "${l.payEmail}\n${l.payCode}"))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    kind = NahButtonKind.Secondary, small = true,
                )
                NahButton(
                    "Ask for free months",
                    {
                        val uri = Uri.parse(
                            "mailto:${l.contactEmail}?subject=" + Uri.encode("Free months for ${l.payCode}") +
                                "&body=" + Uri.encode("Hi, I can't pay for nah.meter right now. My pay code is ${l.payCode}."),
                        )
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
                            .onFailure { Toast.makeText(ctx, "Email ${l.contactEmail} with ${l.payCode}", Toast.LENGTH_LONG).show() }
                    },
                    kind = NahButtonKind.Tertiary, small = true,
                )
            }
        }
    }
}

/** Settings → Account: who's signed in, the licence, paying, and signing out or deleting the account. */
@Composable
fun AccountPage(state: AccountState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val repo = remember { AccountRepository(ctx) }
    val scope = rememberCoroutineScope()
    val c = Nah.c
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackHeader("Account", onBack)
        if (!state.signedIn) {
            SignInPanel(state)
            return@Column
        }
        NahGroup { NahRow(state.email ?: "Signed in", licenceLine(state.licence), descColor = if (licenceAlert(state.licence)) c.nahInk else c.inkMuted) }
        NahButton(
            if (busy) "Checking…" else "Check again",
            {
                busy = true
                scope.launch {
                    message = runCatching { repo.refreshLicence() }
                        .fold({ "Checked: ${licenceLine(it)}" }, { it.message ?: "Couldn't check. Try again." })
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(), kind = NahButtonKind.Secondary, enabled = !busy,
        )
        message?.let { Text(it, style = Nah.style(15.sp, 400, 22.sp, color = c.inkMuted)) }

        state.licence?.let { l ->
            Column {
                SectionLabel("Paying")
                PayInstructions(l)
            }
        }
        Text(
            "nah.meter is a community app run at cost. The money covers hosting and upkeep; if you can't pay, ask and you'll get free months.",
            style = Nah.style(14.sp, 400, 20.sp, color = c.inkMuted),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NahButton("Sign out", { scope.launch { repo.signOut() } }, kind = NahButtonKind.Tertiary, small = true)
            NahButton("Delete account", { confirmDelete = true }, kind = NahButtonKind.Tertiary, small = true)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.surface,
            title = { Text("Delete your account?", style = Nah.heading.copy(color = c.ink)) },
            text = {
                Text(
                    "This deletes your sign-in, your dates and your payment records on edmel.dev. Your offers and settings stay on this phone.",
                    style = Nah.style(15.sp, 400, 22.sp, color = c.ink),
                )
            },
            confirmButton = {
                NahButton("Delete", {
                    confirmDelete = false
                    scope.launch {
                        runCatching { repo.deleteAccount() }
                            .onFailure { message = it.message ?: "Couldn't delete the account." }
                    }
                }, small = true)
            },
            dismissButton = { NahButton("Cancel", { confirmDelete = false }, kind = NahButtonKind.Tertiary, small = true) },
        )
    }
}
