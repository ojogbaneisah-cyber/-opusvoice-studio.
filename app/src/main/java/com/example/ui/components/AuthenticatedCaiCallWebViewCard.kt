package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.PermissionRequest
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.model.SyncedCharacterProfile
import com.example.ui.theme.OpusRed
import com.example.ui.theme.StudioGreen

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AuthenticatedCaiCallWebViewCard(
    character: SyncedCharacterProfile,
    savedToken: String,
    savedCookies: String,
    holdoffWindowMs: Int,
    spectralNotchEnabled: Boolean,
    isDark: Boolean,
    borderCol: Color,
    onArmDspBridge: () -> Unit
) {
    val externalId = character.characterId.removePrefix("cai_")
    val targetCharacterUrl = remember(externalId) {
        "https://character.ai/chat/$externalId"
    }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var dspHookStatus by remember {
        mutableStateOf("WebAudio 48kHz Notch + ${holdoffWindowMs}ms Chime Gate Armed")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF141720) else Color(0xFFF8FAFC))
            .border(1.5.dp, StudioGreen, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .testTag("authenticated_cai_call_webview_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = StudioGreen,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "AUTHENTICATED C.AI CALL WEBVIEW (${character.name})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioGreen
                    )
                    Text(
                        text = dspHookStatus,
                        fontSize = 9.sp,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        onArmDspBridge()
                        webViewInstance?.let { wv ->
                            injectWebAudioChimeBlockerDsp(wv, holdoffWindowMs, spectralNotchEnabled)
                            dspHookStatus = "Re-armed 48kHz Chime-Blocker Pipeline (${holdoffWindowMs}ms)"
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StudioGreen),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync DSP", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Character.AI Call WebView",
                        tint = OpusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .testTag("cai_authenticated_call_webview"),
                factory = { ctx ->
                    onArmDspBridge()
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.databaseEnabled = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        // Seed shared session token & cookies into CookieManager
                        if (savedToken.isNotBlank()) {
                            cookieManager.setCookie(
                                "https://character.ai",
                                "HTTP_AUTHORIZATION=Token $savedToken; Domain=.character.ai; Path=/; Secure"
                            )
                            cookieManager.setCookie(
                                "https://character.ai",
                                "char_token=$savedToken; Domain=.character.ai; Path=/; Secure"
                            )
                        }
                        if (savedCookies.isNotBlank()) {
                            savedCookies.split(";").forEach { cookiePair ->
                                val trimmed = cookiePair.trim()
                                if (trimmed.isNotBlank()) {
                                    cookieManager.setCookie("https://character.ai", "$trimmed; Domain=.character.ai; Path=/")
                                }
                            }
                        }
                        cookieManager.flush()

                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (view != null) {
                                    if (savedToken.isNotBlank()) {
                                        view.evaluateJavascript(
                                            """
                                            try {
                                                window.localStorage.setItem('char_token', '$savedToken');
                                                window.localStorage.setItem('HTTP_AUTHORIZATION', 'Token $savedToken');
                                            } catch(e) {}
                                            """.trimIndent(),
                                            null
                                        )
                                    }
                                    injectWebAudioChimeBlockerDsp(view, holdoffWindowMs, spectralNotchEnabled)
                                }
                            }
                        }

                        loadUrl(targetCharacterUrl)
                        webViewInstance = this
                    }
                },
                update = { wv ->
                    injectWebAudioChimeBlockerDsp(wv, holdoffWindowMs, spectralNotchEnabled)
                }
            )
        }
    }
}

private fun injectWebAudioChimeBlockerDsp(
    webView: WebView,
    holdoffMs: Int,
    spectralNotchEnabled: Boolean
) {
    val notchGainDb = if (spectralNotchEnabled) -28.0 else 0.0
    val script = """
        (function() {
            try {
                window.__OPUS_CHIME_HOLDOFF_MS = $holdoffMs;
                window.__OPUS_NOTCH_DB = $notchGainDb;
                if (!window.__OPUS_CHIME_DSP_INSTALLED) {
                    window.__OPUS_CHIME_DSP_INSTALLED = true;
                    var OrigAudioCtx = window.AudioContext || window.webkitAudioContext;
                    if (OrigAudioCtx) {
                        var origConnect = AudioNode.prototype.connect;
                        AudioNode.prototype.connect = function(dest) {
                            try {
                                if (dest && dest.context && dest === dest.context.destination && !this.__opusFiltered) {
                                    this.__opusFiltered = true;
                                    var ctx = dest.context;
                                    var notch = ctx.createBiquadFilter();
                                    notch.type = 'peaking';
                                    notch.frequency.value = 1650;
                                    notch.Q.value = 2.4;
                                    notch.gain.value = window.__OPUS_NOTCH_DB || -28.0;
                                    origConnect.call(this, notch);
                                    return origConnect.call(notch, dest);
                                }
                            } catch (err) {}
                            return origConnect.apply(this, arguments);
                        };
                    }
                }
            } catch (e) {}
        })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
}
