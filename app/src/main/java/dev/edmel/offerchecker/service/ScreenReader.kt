package dev.edmel.offerchecker.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.edmel.offerchecker.core.OcrLine

/**
 * Reads the whole screen as text: an accessibility screenshot (Android 11+, no screen-recording
 * prompt) run through ML Kit's on-device text recognition. Used because Uber's offer pop-up
 * isn't visible to accessibility. Uber's card can't be located as a window, so the full screen
 * is read; [dev.edmel.offerchecker.core.OfferParser.cardFromOcr] then picks out the card.
 */
object ScreenReader {
    /** Screenshots are scaled down to this width: the card's text is large, and it's faster. */
    private const val WIDTH = 720

    private var recognizer: TextRecognizer? = null

    // Skip recognition when the screen hasn't changed since last time.
    private var lastFingerprint: Int? = null
    private var lastLines: List<OcrLine> = emptyList()
    private var lastHeight = 0

    private fun recognizer(): TextRecognizer =
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }

    /**
     * Calls [done] on the main thread with the recognised lines and the (scaled) screen height,
     * or null lines and an error description on failure.
     */
    @RequiresApi(30)
    fun capture(service: AccessibilityService, done: (List<OcrLine>?, Int, String?) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val screen = runCatching {
                        val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        soft
                    }.getOrNull()
                    buffer.close()
                    if (screen == null) {
                        done(null, 0, "couldn't copy screenshot")
                        return
                    }
                    val image = if (screen.width > WIDTH) {
                        Bitmap.createScaledBitmap(screen, WIDTH, screen.height * WIDTH / screen.width, true)
                            .also { screen.recycle() }
                    } else screen

                    val fingerprint = fingerprint(image)
                    if (fingerprint == lastFingerprint) {
                        image.recycle()
                        done(lastLines, lastHeight, null)
                        return
                    }
                    val height = image.height
                    recognizer().process(InputImage.fromBitmap(image, 0))
                        .addOnSuccessListener { text ->
                            val lines = text.textBlocks.flatMap { block ->
                                block.lines.mapNotNull { line ->
                                    line.boundingBox?.let { OcrLine(line.text, it.top, it.bottom) }
                                }
                            }
                            lastFingerprint = fingerprint
                            lastLines = lines
                            lastHeight = height
                            image.recycle()
                            done(lines, height, null)
                        }
                        .addOnFailureListener { e ->
                            image.recycle()
                            done(null, height, "text recognition: ${e.message}")
                        }
                }

                override fun onFailure(errorCode: Int) = done(null, 0, "screenshot error $errorCode")
            },
        )
    }

    /** A tiny thumbnail's pixels, ignoring the status bar (its clock changes every minute). */
    private fun fingerprint(b: Bitmap): Int {
        val top = b.height / 20
        val crop = Bitmap.createBitmap(b, 0, top, b.width, b.height - top)
        val thumb = Bitmap.createScaledBitmap(crop, 24, 48, true)
        val px = IntArray(24 * 48)
        thumb.getPixels(px, 0, 24, 0, 0, 24, 48)
        if (crop !== b) crop.recycle()
        thumb.recycle()
        return px.contentHashCode()
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        lastFingerprint = null
    }
}
