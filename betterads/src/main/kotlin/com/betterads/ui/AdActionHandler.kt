package com.betterads.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.betterads.model.AdCtaAction
import com.betterads.model.AdCtaActionType

/**
 * Opens CTA destinations. Owned by the SDK — host apps should not open ad URLs themselves.
 * Matches iOS `AdActionHandler`.
 */
object AdActionHandler {
    fun open(context: Context, action: AdCtaAction) {
        val trimmed = action.value.trim()
        if (trimmed.isEmpty()) return
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return

        when (action.type) {
            AdCtaActionType.URL -> openExternalUrl(context, uri)
            AdCtaActionType.DEEPLINK -> openDeeplink(context, uri)
        }
    }

    private fun openExternalUrl(context: Context, uri: Uri) {
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }.onFailure {
            openDeeplink(context, uri)
        }
    }

    private fun openDeeplink(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
