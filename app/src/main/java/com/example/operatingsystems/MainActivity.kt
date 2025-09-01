package com.example.operatingsystems

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.net.http.SslError

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val startUrl = "https://learn.zybooks.com/zybook/WICHITACS540BagaiFall2025"

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserReqCode = 1111

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(true) // turn off later if you like

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true   // OAuth popups/new windows
            mediaPlaybackRequiresUserGesture = false

            // Helpful if the site sniffs for Chrome:
            userAgentString = WebSettings.getDefaultUserAgent(this@MainActivity) +
                    " WebToApp/1.0"
        }

        // Cookies (incl. 3rd-party) help with SSO/login flows
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Keep normal http(s) links inside; punt non-web schemes to external apps
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme?.lowercase() ?: return false
                return when (scheme) {
                    "http", "https" -> false
                    "intent" -> {
                        try {
                            val intent = Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME)
                            startActivity(intent)
                        } catch (_: Exception) {}
                        true
                    }
                    else -> {
                        try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) {}
                        true
                    }
                }
            }

            // Respect SSL errors (don’t proceed on errors)
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
            }
        }

        // Popups/new windows + file uploads
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val pickIntent = fileChooserParams?.createIntent()
                return if (pickIntent != null) {
                    try {
                        startActivityForResult(pickIntent, fileChooserReqCode)
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileChooserCallback = null
                        false
                    }
                } else {
                    fileChooserCallback = null
                    false
                }
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                // Load popup URL into the same WebView
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                transport.webView = webView
                resultMsg.sendToTarget()
                return true
            }
        }

        if (savedInstanceState == null) webView.loadUrl(startUrl)
        else webView.restoreState(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserReqCode) {
            fileChooserCallback?.onReceiveValue(
                if (resultCode == Activity.RESULT_OK)
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                else null
            )
            fileChooserCallback = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
