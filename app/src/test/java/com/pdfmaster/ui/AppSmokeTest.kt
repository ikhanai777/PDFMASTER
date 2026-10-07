package com.pdfmaster.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.pdfmaster.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Launches the real activity and walks the main screens to prove they compose without
 * crashing. Screenshots land in app/build/screenshots for review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        runCatching {
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun walkThroughMainScreens() {
        compose.onNodeWithText("Scan document").assertExists()
        compose.onNodeWithText("Quick tools").assertExists()
        shot("01-home")

        compose.onAllNodesWithText("Tools").onFirst().performClick()
        compose.onNodeWithText("Organise").assertExists()
        shot("02-tools")
        compose.onNode(hasSetTextAction()).performTextInput("compress")
        compose.onAllNodesWithText("Compress").onFirst().assertExists()
        shot("03-tools-search")

        compose.onAllNodesWithText("Files").onFirst().performClick()
        compose.onNodeWithText("Import").assertExists()
        shot("04-files")

        compose.onAllNodesWithText("Me").onFirst().performClick()
        compose.onNodeWithText("Free plan").assertExists()
        shot("05-me")
        compose.onNodeWithText("Free plan").performClick()
        compose.onNodeWithText("Restore purchases").assertExists()
        shot("06-paywall")
    }

    @Test fun toolScreensOpen() {
        compose.onAllNodesWithText("Tools").onFirst().performClick()
        compose.onAllNodesWithText("Merge").onFirst().performClick()
        compose.onNodeWithText("Add files, then drag to set the order.").assertExists()
        shot("07-merge")
        compose.activity.onBackPressedDispatcher.onBackPressed()

        compose.onAllNodesWithText("Split").onFirst().performClick()
        compose.onNodeWithText("Choose PDF").assertExists()
        shot("08-split")
        compose.activity.onBackPressedDispatcher.onBackPressed()

        compose.onAllNodesWithText("Compress").onFirst().performClick()
        compose.onNodeWithText("Balanced").assertExists()
        shot("09-compress")
    }

    @Test fun proToolShowsGateBeforeOpening() {
        compose.onAllNodesWithText("Tools").onFirst().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("watermark")
        compose.onAllNodesWithText("Watermark").onFirst().performClick()
        compose.onNodeWithText("Try once free").assertExists()
        shot("10-pro-gate")
    }

    @Test fun signatureStudioOpens() {
        compose.onAllNodesWithText("Me").onFirst().performClick()
        compose.onNodeWithText("Signatures").performClick()
        compose.onNodeWithText("Draw").assertExists()
        shot("11-signatures")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ar-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArabicSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun arabicHomeIsRightToLeft() {
        compose.onNodeWithText("مسح مستند ضوئيًا").assertExists()
        var direction: LayoutDirection? = null
        compose.activity.runOnUiThread { direction = if (compose.activity.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr }
        compose.waitForIdle()
        assertEquals(LayoutDirection.Rtl, direction)
        val dir = File("build/screenshots").apply { mkdirs() }
        runCatching {
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "12-home-arabic.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onAllNodesWithText("الأدوات").onFirst().performClick()
        compose.onNodeWithText("التنظيم").assertExists()
        runCatching {
            val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
            File(dir, "13-tools-arabic.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Suppress("unused") private val keep = LocalLayoutDirection
}
