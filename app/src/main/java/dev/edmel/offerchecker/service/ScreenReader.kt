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
                    val buffer = result.hardwareBuffer
                    val screen = runCatching {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                    buffer.close()
                    if (screen == null) {
                        done(null, 0)
                        return
                    }
                    val crop = area?.let { Rect(it).apply { intersect(0, 0, screen.width, screen.height) } }
                        ?.takeIf { it.width() > 0 && it.height() > 0 }
                    val image = crop?.let { Bitmap.createBitmap(screen, it.left, it.top, it.width(), it.height()) } ?: screen
                    val offsetY = crop?.top ?: 0
                    recognizer().process(InputImage.fromBitmap(image, 0))
                        .addOnSuccessListener { text ->
                            val lines = text.textBlocks.flatMap { block ->
                                block.lines.mapNotNull { line ->
                                    line.boundingBox?.let { OcrLine(line.text, it.top + offsetY, it.bottom + offsetY) }
                                }
                            }
                            done(lines, screen.height)
                        }
                        .addOnFailureListener { done(null, screen.height) }
                }

                override fun onFailure(errorCode: Int) = done(null, 0)
            },
        )
    }

    fun close() {
        recognizer?.close()
        recognizer = null
    }
}
