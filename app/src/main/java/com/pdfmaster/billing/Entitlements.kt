package com.pdfmaster.billing

import com.pdfmaster.data.Prefs
import kotlinx.coroutines.flow.StateFlow

sealed interface Access {
    /** Open freely. [remainingToday] is set for daily-limited tools. */
    data class Open(val remainingToday: Int? = null) : Access
    /** Pro tool, but the one-time free trial use is still available. */
    data object Trial : Access
    /** Pro tool and the trial was used. */
    data object Locked : Access
    /** Free daily allowance used up. */
    data class LimitReached(val limit: Int) : Access
    data object ComingSoon : Access
}

/**
 * Decides access *before* a tool opens, so a paywall never appears after the user
 * has done the work.
 */
class Entitlements(private val prefs: Prefs, val isPro: StateFlow<Boolean>) {

    fun access(tool: ToolId): Access {
        if (!tool.available) return Access.ComingSoon
        if (isPro.value) return Access.Open()
        return when (tool.tier) {
            Tier.FREE -> Access.Open()
            Tier.FREE_DAILY -> {
                val left = tool.dailyLimit - prefs.usesToday(tool.name)
                if (left > 0) Access.Open(left) else Access.LimitReached(tool.dailyLimit)
            }
            Tier.PRO -> if (prefs.trialUsed(tool.name)) Access.Locked else Access.Trial
        }
    }

    /** Called when a tool finishes successfully. */
    fun recordCompletion(tool: ToolId) {
        if (isPro.value) return
        when (tool.tier) {
            Tier.FREE_DAILY -> prefs.recordUse(tool.name)
            Tier.PRO -> prefs.markTrialUsed(tool.name)
            Tier.FREE -> Unit
        }
    }
}
