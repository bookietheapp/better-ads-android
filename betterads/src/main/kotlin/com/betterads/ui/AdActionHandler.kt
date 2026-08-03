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

        // Serve payloads sometimes label app schemes as `type: url`. Custom Tabs only
        // support http/https — route everything else (e.g. bookie://) as a deeplink.
        if (shouldOpenInBrowser(uri) && action.type == AdCtaActionType.URL) {
            openExternalUrl(context, uri)
        } else {
            openDeeplink(context, uri)
        }
    }

    /** HTTP(S) only — safe for Custom Tabs / SFSafariViewController. */
    internal fun shouldOpenInBrowser(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun openExternalUrl(context: Context, uri: Uri) {
        if (!shouldOpenInBrowser(uri)) {
            openDeeplink(context, uri)
            return
        }
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
