package com.devtools.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.devtools.browser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: android.content.SharedPreferences
    private var devToolsEnabled = false
    private var popupsBlocked = true
    private var erudaSource: String? = null

    private val homeUrl = "https://www.google.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("devtools_browser", Context.MODE_PRIVATE)
        devToolsEnabled = prefs.getBoolean("devtools", false)
        popupsBlocked = prefs.getBoolean("block_popups", true)

        applyWindowInsets()
        setupWebView()
        setupToolbar()
        setupBackHandling()
        updateDevToolsButton()
        updatePopupsButton()

        val startUrl = savedInstanceState?.getString("lastUrl")
            ?: intent?.dataString
            ?: prefs.getString("lastUrl", homeUrl)
            ?: homeUrl
        if (savedInstanceState == null) {
            binding.webView.loadUrl(startUrl)
        } else {
            binding.webView.restoreState(savedInstanceState)
        }
        binding.urlInput.setText(startUrl)
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progress.visibility = View.VISIBLE
                binding.errorView.visibility = View.GONE
                if (url != null && !binding.urlInput.hasFocus()) {
                    binding.urlInput.setText(url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progress.visibility = View.GONE
                updateNavButtons()
                if (url != null) {
                    prefs.edit().putString("lastUrl", url).apply()
                    if (!binding.urlInput.hasFocus()) binding.urlInput.setText(url)
                }
                if (devToolsEnabled) injectEruda()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return handleUri(uri)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    binding.errorView.visibility = View.VISIBLE
                }
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progress.progress = newProgress
                binding.progress.visibility =
                    if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                if (popupsBlocked) {
                    Toast.makeText(this@MainActivity, R.string.popup_blocked_toast, Toast.LENGTH_SHORT).show()
                    return true
                }
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(this@MainActivity)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        r: WebResourceRequest?
                    ): Boolean {
                        r?.url?.let { binding.webView.loadUrl(it.toString()) }
                        return true
                    }
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { if (binding.webView.canGoBack()) binding.webView.goBack() }
        binding.btnForward.setOnClickListener { if (binding.webView.canGoForward()) binding.webView.goForward() }
        binding.btnReload.setOnClickListener {
            if (binding.webView.progress < 100) binding.webView.stopLoading()
            else binding.webView.reload()
        }
        binding.btnDevTools.setOnClickListener { toggleDevTools() }
        binding.btnPopups.setOnClickListener { togglePopups() }

        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isGo) {
                navigate(binding.urlInput.text.toString())
                true
            } else false
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun navigate(input: String) {
        val query = input.trim()
        if (query.isEmpty()) return
        val url = when {
            query.startsWith("http://") || query.startsWith("https://") -> query
            query.startsWith("about:") || query.startsWith("file:") -> query
            query.contains(" ") || !query.contains(".") ->
                "https://www.google.com/search?q=" + Uri.encode(query)
            else -> "https://$query"
        }
        binding.webView.loadUrl(url)
        binding.urlInput.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
    }

    private fun handleUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https" || scheme == "about") {
            return false
        }
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app handles ${uri.scheme}", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun toggleDevTools() {
        devToolsEnabled = !devToolsEnabled
        prefs.edit().putBoolean("devtools", devToolsEnabled).apply()
        updateDevToolsButton()
        if (devToolsEnabled) {
            injectEruda()
            Toast.makeText(this, R.string.devtools_on, Toast.LENGTH_SHORT).show()
        } else {
            binding.webView.evaluateJavascript(
                "if (window.eruda && window.eruda.destroy) window.eruda.destroy();", null
            )
            Toast.makeText(this, R.string.devtools_off, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDevToolsButton() {
        val color = ContextCompat.getColor(
            this,
            if (devToolsEnabled) R.color.primary else R.color.text_primary
        )
        binding.btnDevTools.imageTintList = ColorStateList.valueOf(color)
    }

    private fun togglePopups() {
        popupsBlocked = !popupsBlocked
        prefs.edit().putBoolean("block_popups", popupsBlocked).apply()
        updatePopupsButton()
        Toast.makeText(
            this,
            if (popupsBlocked) R.string.popups_blocked else R.string.popups_allowed,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updatePopupsButton() {
        val color = ContextCompat.getColor(
            this,
            if (popupsBlocked) R.color.primary else R.color.text_secondary
        )
        binding.btnPopups.imageTintList = ColorStateList.valueOf(color)
    }

    private fun injectEruda() {
        val source = erudaSource ?: runCatching {
            assets.open("eruda.min.js").bufferedReader().use { it.readText() }
        }.getOrNull()?.also { erudaSource = it } ?: return

        val call = "(function(){ if (window.eruda) { window.eruda.show(); return; } " +
            source + "\n;window.eruda.init(); })();"
        binding.webView.evaluateJavascript(call, null)
    }

    private fun updateNavButtons() {
        binding.btnBack.alpha = if (binding.webView.canGoBack()) 1f else 0.4f
        binding.btnForward.alpha = if (binding.webView.canGoForward()) 1f else 0.4f
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { binding.webView.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
        outState.putString("lastUrl", binding.webView.url)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
