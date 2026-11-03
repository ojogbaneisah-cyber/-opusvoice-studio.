package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.OpusRed
import com.example.ui.theme.StudioGreen
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CharacterAiWebViewLoginDialog(
    onDismiss: () -> Unit,
    onTokenExtracted: (token: String, cookieHeader: String) -> Unit
) {
    var currentUrl by remember { mutableStateOf("https://character.ai/login") }
    var isLoadingPage by remember { mutableStateOf(true) }
    var statusBannerText by remember {
        mutableStateOf("Sign in with Google, Apple, or Email link. Token auto-extracts on login.")
    }
    var hasExtractedToken by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1117))
                .testTag("cai_webview_login_dialog"),
            color = Color(0xFF0F1117)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Broadcast Security Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24))
                        .border(1.dp, Color(0xFF2E3446))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = StudioGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "1-Tap Character.AI Sign-In (Auto-Token)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = currentUrl,
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLoadingPage) {
                            CircularProgressIndicator(
                                color = OpusRed,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                webViewRef?.let { wv ->
                                    inspectWebViewForCaiToken(wv) { token, cookies ->
                                        if (!hasExtractedToken && token.isNotBlank()) {
                                            hasExtractedToken = true
                                            onTokenExtracted(token, cookies)
                                        }
                                    }
                                    wv.reload()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh & Check Token",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("cai_webview_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close WebView",
                                tint = OpusRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Auto-Detection Status Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222634))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = OpusRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = statusBannerText,
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val cookieStr = CookieManager.getInstance()
                                .getCookie("https://character.ai")
                                .orEmpty()
                            val extractedFromCookie = extractCaiTokenFromCookies(cookieStr)
                            if (extractedFromCookie != null) {
                                hasExtractedToken = true
                                onTokenExtracted(extractedFromCookie, cookieStr)
                            } else {
                                webViewRef?.let { wv ->
                                    inspectWebViewForCaiToken(wv) { token, cookies ->
                                        if (token.isNotBlank()) {
                                            hasExtractedToken = true
                                            onTokenExtracted(token, cookies)
                                        } else {
                                            statusBannerText = "No session token detected yet—complete sign-in below first."
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGreen),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("cai_webview_check_token_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Extract Now", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Main Android WebView
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("cai_login_webview"),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(false)
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoadingPage = true
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoadingPage = false
                                        if (!url.isNullOrBlank()) {
                                            currentUrl = url
                                        }
                                        if (view != null && !hasExtractedToken) {
                                            inspectWebViewForCaiToken(view) { detectedToken, cookieHeader ->
                                                if (!hasExtractedToken && detectedToken.isNotBlank()) {
                                                    hasExtractedToken = true
                                                    statusBannerText = "Authenticated token detected! Syncing Character.AI account..."
                                                    CookieManager.getInstance().flush()
                                                    onTokenExtracted(detectedToken, cookieHeader)
                                                }
                                            }
                                        }
                                    }
                                }

                                loadUrl("https://character.ai/login")
                                webViewRef = this
                            }
                        }
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
        }
    }
}

private fun inspectWebViewForCaiToken(
    webView: WebView,
    onFound: (token: String, cookieHeader: String) -> Unit
) {
    val cookieManager = CookieManager.getInstance()
    val mainCookies = cookieManager.getCookie("https://character.ai").orEmpty()
    val plusCookies = cookieManager.getCookie("https://plus.character.ai").orEmpty()
    val betaCookies = cookieManager.getCookie("https://beta.character.ai").orEmpty()
    val combinedCookies = listOf(mainCookies, plusCookies, betaCookies)
        .filter { it.isNotBlank() }
        .joinToString("; ")

    val tokenFromCookies = extractCaiTokenFromCookies(combinedCookies)
    if (!tokenFromCookies.isNullOrBlank()) {
        onFound(tokenFromCookies, combinedCookies)
        return
    }

    val jsExtractionScript = """
        (function() {
            try {
                var keys = ['char_token', 'HTTP_AUTHORIZATION', 'token', 'auth_token', 'cai_token'];
                for (var i = 0; i < keys.length; i++) {
                    var val = window.localStorage.getItem(keys[i]);
                    if (val && val.length > 15) return val;
                }
                for (var j = 0; j < window.localStorage.length; j++) {
                    var k = window.localStorage.key(j);
                    var v = window.localStorage.getItem(k);
                    if (k && (k.indexOf('token') !== -1 || k.indexOf('auth') !== -1 || k.indexOf('char') !== -1)) {
                        if (v && v.length > 15) return v;
                    }
                }
                if (window.__NEXT_DATA__ && window.__NEXT_DATA__.props && window.__NEXT_DATA__.props.pageProps && window.__NEXT_DATA__.props.pageProps.token) {
                    return window.__NEXT_DATA__.props.pageProps.token;
                }
                return "";
            } catch (e) {
                return "";
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsExtractionScript) { rawResult ->
        val cleaned = cleanEvaluatedJsToken(rawResult)
        if (cleaned.length >= 16) {
            onFound(cleaned, combinedCookies)
        }
    }
}

fun extractCaiTokenFromCookies(cookieHeader: String): String? {
    if (cookieHeader.isBlank()) return null
    val parts = cookieHeader.split(";")
    for (part in parts) {
        val trimmed = part.trim()
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx <= 0) continue
        val key = trimmed.substring(0, eqIdx).trim()
        val rawVal = trimmed.substring(eqIdx + 1).trim().trim('"')
        if (key.equals("HTTP_AUTHORIZATION", ignoreCase = true) ||
            key.equals("char_token", ignoreCase = true) ||
            key.equals("authorization", ignoreCase = true) ||
            key.equals("__Secure-next-auth.session-token", ignoreCase = true)
        ) {
            val decoded = rawVal
                .replace("%20", " ")
                .removePrefix("Token ")
                .removePrefix("Bearer ")
                .trim()
            if (decoded.length >= 16) {
                return decoded
            }
        }
    }
    return null
}

private fun cleanEvaluatedJsToken(rawJsValue: String?): String {
    if (rawJsValue.isNullOrBlank() || rawJsValue == "null" || rawJsValue == "\"\"") return ""
    var unwrapped = rawJsValue.trim()
    if (unwrapped.startsWith("\"") && unwrapped.endsWith("\"") && unwrapped.length >= 2) {
        unwrapped = unwrapped.substring(1, unwrapped.length - 1)
    }
    unwrapped = unwrapped.replace("\\\"", "\"")
    if (unwrapped.startsWith("{") && unwrapped.endsWith("}")) {
        try {
            val json = JSONObject(unwrapped)
            val inner = json.optString("value", "")
                .ifBlank { json.optString("token", "") }
                .ifBlank { json.optString("access_token", "") }
            if (inner.isNotBlank()) {
                unwrapped = inner
            }
        } catch (_: Exception) {
        }
    }
    return unwrapped
        .removePrefix("Token ")
        .removePrefix("Bearer ")
        .trim()
}
