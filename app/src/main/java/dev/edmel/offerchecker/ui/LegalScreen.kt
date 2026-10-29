package dev.edmel.offerchecker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The legal documents, bundled in `assets/legal/` (also linked from the README on GitHub). */
enum class LegalDoc(val file: String, val title: String) {
    TERMS("terms.md", "Terms of use"),
    PRIVACY("privacy.md", "Privacy policy"),
    LICENCES("licences.md", "Open-source licences"),
}

/** One legal document, full screen, with Back. */
@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val text = remember(doc) {
        runCatching { ctx.assets.open("legal/${doc.file}").bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackHeader(doc.title, onBack)
        MarkdownBlocks(text)
    }
}

/**
 * Shown instead of the app while the current terms haven't been agreed to: after an update that
 * changes them, or for anyone who never agreed. The reader gives no verdicts until then.
 */
@Composable
fun AgreeScreen(onAgree: () -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<LegalDoc?>(null) }
    open?.let { doc ->
        LegalScreen(doc, onBack = { open = null }, modifier = modifier)
        return
    }
    val c = Nah.c
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Wordmark()
            Text("Agree to continue", style = Nah.title.copy(color = c.ink), modifier = Modifier.semantics { heading() })
            Text(
                "nah.meter has terms of use and a privacy policy. Until you agree, offers get no verdict.",
                style = Nah.style(16.sp, 400, 24.sp, color = c.inkMuted),
            )
            NahGroup {
                LegalLink(LegalDoc.TERMS) { open = it }
                NahDivider()
                LegalLink(LegalDoc.PRIVACY) { open = it }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            NahButton("Agree", onAgree, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A row that opens a legal document. */
@Composable
fun LegalLink(doc: LegalDoc, onOpen: (LegalDoc) -> Unit) {
    val c = Nah.c
    Box(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(doc) }) {
        NahRow(doc.title) {
            Icon(NahIcons.chevron, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/** The small Markdown the legal files use: `#`/`##` headings, paragraphs, `- ` bullets, `**bold**`. */
@Composable
private fun MarkdownBlocks(markdown: String) {
    val c = Nah.c
    val body = Nah.style(15.sp, 400, 22.sp, color = c.ink)
    for (block in markdownBlocks(markdown)) {
        when (block) {
            // The page header already shows the document's title.
            is Block.Title -> Unit
            is Block.Heading -> Text(
                block.text, style = Nah.heading.copy(color = c.ink),
                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
            )
            is Block.Paragraph -> Text(inline(block.text), style = body)
            is Block.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("•", style = body)
                Text(inline(block.text), style = body, modifier = Modifier.weight(1f))
            }
        }
    }
}

private sealed interface Block {
    data class Title(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullet(val text: String) : Block
}

/** Blank lines end a paragraph; lines inside one are joined with spaces. */
private fun markdownBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph.toString())
        paragraph.clear()
    }
    for (raw in markdown.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            line.startsWith("## ") -> { flush(); blocks += Block.Heading(line.removePrefix("## ")) }
            line.startsWith("# ") -> { flush(); blocks += Block.Title(line.removePrefix("# ")) }
            line.startsWith("- ") -> { flush(); blocks += Block.Bullet(line.removePrefix("- ")) }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flush()
    return blocks
}

/** `**bold**` spans; everything else as written. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1 && i < parts.size - 1) withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append(part) }
        else append(part)
    }
}
