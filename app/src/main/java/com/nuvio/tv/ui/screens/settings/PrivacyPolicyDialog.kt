package com.nuvio.tv.ui.screens.settings

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme

private const val PRIVACY_POLICY_URL = "https://nuvio.tv/privacy-policy"

/**
 * Shows the privacy policy inside this activity. Android TV often has no browser, and
 * [android.content.Intent.ACTION_VIEW] then leaves the app on the system home screen.
 */
@Composable
internal fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val closeFocusRequester = remember { FocusRequester() }
    var unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.about_privacy_policy),
        subtitle = stringResource(R.string.about_privacy_policy_subtitle),
        width = 960.dp,
        usePlatformDefaultWidth = false
    ) {
        if (unavailable) {
            Text(
                text = stringResource(R.string.about_privacy_policy_unavailable, PRIVACY_POLICY_URL),
                style = androidx.tv.material3.MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
        } else {
            PrivacyPolicyWebView(
                onUnavailable = { unavailable = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            )
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.focusRequester(closeFocusRequester),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.Secondary,
                focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                contentColor = NuvioTheme.colors.OnSecondary,
                focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
            )
        ) {
            Text(text = stringResource(R.string.action_close))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PrivacyPolicyWebView(
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            try {
                WebView(context).apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val target = request.url?.toString().orEmpty()
                            if (target.startsWith("https://") || target.startsWith("http://")) {
                                view.loadUrl(target)
                            }
                            return true
                        }
                    }
                    loadUrl(PRIVACY_POLICY_URL)
                }
            } catch (_: Throwable) {
                Handler(Looper.getMainLooper()).post(onUnavailable)
                View(context)
            }
        }
    )
}
