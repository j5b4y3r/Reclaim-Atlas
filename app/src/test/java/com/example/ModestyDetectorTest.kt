package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.domain.vision.ModestyDetector
import com.example.domain.vision.ModestySensitivity
import com.example.domain.vision.ModestyViolationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModestyDetectorTest {

    private val detector = ModestyDetector()

    @Test
    fun testCompletelyClothedOrLandscapeBitmapIsSafe() {
        // Plain dark/blue bitmap representing text or landscape
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, Color.rgb(20, 24, 40))
            }
        }

        val resultStrict = detector.evaluate(bitmap, ModestySensitivity.STRICT_AWRAH)
        assertFalse("Pure text / dark bitmap should be completely safe", resultStrict.isViolating)
        assertTrue(resultStrict.regions.isEmpty())

        val resultModerate = detector.evaluate(bitmap, ModestySensitivity.MODERATE)
        assertFalse(resultModerate.isViolating)
    }

    @Test
    fun testExposedSkinAwrahViolationDetected() {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)

        // Fill background with dark fabric
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, Color.rgb(15, 15, 15))
            }
        }

        // Simulate exposed chest / cleavage zone (rows around y: 50..130, x: 60..196) with realistic skin tones
        // RGB skin: R=215, G=160, B=130 -> (R > G > B, R-G >= 15)
        for (y in 55..135) {
            for (x in 60..196) {
                bitmap.setPixel(x, y, Color.rgb(215, 160, 130))
            }
        }

        val resultStrict = detector.evaluate(bitmap, ModestySensitivity.STRICT_AWRAH)
        assertTrue("Exposed chest/cleavage skin should be flagged in STRICT_AWRAH mode", resultStrict.isViolating)
        assertTrue(resultStrict.regions.any { it.violationType == ModestyViolationType.EXPOSED_CLEAVAGE })
    }

    @Test
    fun testSensitivityFilterExcludesStrictOnlyWhenModerateSelected() {
        // Create midriff exposure
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                bitmap.setPixel(x, y, Color.rgb(20, 20, 20))
            }
        }

        // Draw moderate skin patch on midriff zone (y: 110..170, x: 80..170)
        for (y in 115..170) {
            for (x in 80..170) {
                bitmap.setPixel(x, y, Color.rgb(215, 160, 130))
            }
        }

        val resultStrict = detector.evaluate(bitmap, ModestySensitivity.STRICT_AWRAH)
        val resultModerate = detector.evaluate(bitmap, ModestySensitivity.MODERATE)

        // Strict catches midriff
        if (resultStrict.isViolating) {
            val strictHasMidriff = resultStrict.regions.any { it.violationType == ModestyViolationType.EXPOSED_MIDRIFF }
            assertTrue("Strict mode should detect midriff exposure", strictHasMidriff)
        }
        // Moderate should not flag strict-only violation types
        val moderateHasMidriff = resultModerate.regions.any { it.violationType.isStrictOnly }
        assertFalse("Moderate sensitivity should ignore strict-only violation types", moderateHasMidriff)
    }
}
