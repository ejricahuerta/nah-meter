package dev.edmel.offerchecker.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.edmel.offerchecker.core.OcrLine

/**
 * Reads the screen as text: an accessibility screenshot (Android 11+, no screen-recording
 * prompt) run through ML Kit's on-device text recognition. Used when Uber hides its text
 * from accessibility.
 */
object ScreenReader {
    private var recognizer: TextRecognizer? = null

    private fun recognizer(): TextRecognizer =
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }

    /**
     * Calls [done] on the main thread with the recognised lines (in screen coordinates) and the
     * screen height, or null lines on failure. Only the [area] (Uber's windows) is read when given.
     */
    @RequiresApi(30)
    fun capture(service: AccessibilityService, area: Rect?, done: (List<OcrLine>?, Int) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    // Runs about once a second for hours while Uber is open: every bitmap is freed
                    // as soon as it's done with, or the screenshots pile up in memory until Android
                    // closes the app. Any failure is a failed read, never a crash.
                    var screen: Bitmap? = null
                    var image: Bitmap? = null
                    try {
                        val buffer = result.hardwareBuffer
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        screen = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        wrapped?.recycle()
                        buffer.close()
                        val full = screen ?: return done(null, 0)
                        val crop = area?.let { a ->
                            Rect(a).takeIf { it.intersect(0, 0, full.width, full.height) && it.width() > 0 && it.height() > 0 }
                        }
                        val input = crop?.let { Bitmap.createBitmap(full, it.left, it.top, it.width(), it.height()) } ?: full
                        image = input
                        val offsetY = crop?.top ?: 0
                        val offsetX = crop?.left ?: 0
                        val height = full.height
                        recognizer().process(InputImage.fromBitmap(input, 0))
                            .addOnCompleteListener { task ->
                                val lines = if (task.isSuccessful) {
                                    runCatching {
                                        task.result.textBlocks.flatMap { block ->
                                            block.lines.mapNotNull { line ->
                                                line.boundingBox?.let {
                                                    OcrLine(line.text, it.top + offsetY, it.bottom + offsetY, it.left + offsetX, it.right + offsetX)
                                                }
                                            }
                                        }
                                    }.getOrNull()
                                } else {
                                    null
                                }
                                if (input !== full) input.recycle()
                                full.recycle()
                                done(lines, height)
                            }
                    } catch (e: Throwable) {
                        logError(LogEntry.error("Screenshot error", e, frames = 3))
                        runCatching { result.hardwareBuffer.close() }
                        if (image != null && image !== screen) image.recycle()
                        screen?.recycle()
                        done(null, 0)
                    }
                }

                override fun onFailure(errorCode: Int) {
                    logError(LogEntry(System.currentTimeMillis(), "Screenshot refused", listOf("Android error code $errorCode")))
                    done(null, 0)
                }
            },
        )
    }

    private var lastError: LogEntry? = null

    /** Logs a screenshot error once, not once a second while it keeps happening. */
    private fun logError(entry: LogEntry) {
        if (lastError?.sameAs(entry) == true) return
        lastError = entry
        ServiceState.log(entry)
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
