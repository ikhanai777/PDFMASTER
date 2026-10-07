package com.pdfmaster.billing

import androidx.test.core.app.ApplicationProvider
import com.pdfmaster.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntitlementsTest {
    private val prefs = Prefs(ApplicationProvider.getApplicationContext())

    @Test fun proUnlocksEveryAvailableTool() {
        val e = Entitlements(prefs, MutableStateFlow(true))
        ToolId.entries.filter { it.available }.forEach { assertEquals(it.name, Access.Open(), e.access(it)) }
    }

    @Test fun freeUserGetsTrialThenLock() {
        val e = Entitlements(prefs, MutableStateFlow(false))
        assertEquals(Access.Trial, e.access(ToolId.WATERMARK))
        e.recordCompletion(ToolId.WATERMARK)
        assertEquals(Access.Locked, e.access(ToolId.WATERMARK))
    }

    @Test fun freeUserCompressLimitIsThreePerDay() {
        val e = Entitlements(prefs, MutableStateFlow(false))
        repeat(3) { assertEquals(Access.Open(3 - it), e.access(ToolId.COMPRESS)); e.recordCompletion(ToolId.COMPRESS) }
        assertEquals(Access.LimitReached(3), e.access(ToolId.COMPRESS))
    }
}
