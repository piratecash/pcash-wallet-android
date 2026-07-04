package cash.p.terminal.modules.paycore.webview

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Message
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import timber.log.Timber
import androidx.core.net.toUri

@Composable
fun PayCoreWebViewScreen(
    url: String,
    title: String,
    onClose: () -> Unit,
    onInterceptBackUrl: () -> Unit,
    backUrlPrefix: String,
    modifier: Modifier = Modifier,
) {
    val currentBackUrlPrefix by rememberUpdatedState(backUrlPrefix)
    val currentOnInterceptBackUrl by rememberUpdatedState(onInterceptBackUrl)
    var loadingProgress by remember { mutableIntStateOf(100) }

    Scaffold(
        modifier = modifier,
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = title,
                navigationIcon = { HsBackButton(onClick = onClose) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (loadingProgress < 100) {
                LinearProgressIndicator(
                    progress = { loadingProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PayCoreWebViewContent(
                url = url,
                onInterceptBackUrl = { currentOnInterceptBackUrl() },
                backUrlPrefix = { currentBackUrlPrefix },
                onProgressChange = { loadingProgress = it },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PayCoreWebViewContent(
    url: String,
    onInterceptBackUrl: () -> Unit,
    backUrlPrefix: () -> String,
    onProgressChange: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingFileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        applyCameraPermissionResult(permissions, pendingWebPermissionRequest)
        pendingWebPermissionRequest = null
    }
    val fileChooserLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            pendingFileChooserCallback?.onReceiveValue(uri?.let { arrayOf(it) })
            pendingFileChooserCallback = null
        }

    DisposableEffect(Unit) {
        onDispose {
            pendingWebPermissionRequest?.deny()
            pendingFileChooserCallback?.onReceiveValue(null)
            webView?.destroy()
        }
    }
    BackHandler { onClose() }

    PayCoreWebViewHost(
        url = url,
        callbacks = PayCoreWebViewCallbacks(
            onInterceptBackUrl = onInterceptBackUrl,
            backUrlPrefix = backUrlPrefix,
            onCameraPermissionRequest = { request ->
                pendingWebPermissionRequest?.deny()
                pendingWebPermissionRequest = request
                cameraPermissionLauncher.launch(cameraPermissionsFor(request))
            },
            onFileChooserRequest = { fileChooserCallback, mimeType ->
                pendingFileChooserCallback?.onReceiveValue(null)
                pendingFileChooserCallback = fileChooserCallback
                fileChooserLauncher.launch(mimeType)
            },
            onProgressChanged = onProgressChange,
        ),
        onWebViewReady = { webView = it },
    )
}

@Composable
private fun PayCoreWebViewHost(
    url: String,
    callbacks: PayCoreWebViewCallbacks,
    onWebViewReady: (WebView) -> Unit,
) {
    var lastLoadedUrl by remember { mutableStateOf(url) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            createPayCoreWebView(context, url, callbacks).also(onWebViewReady)
        },
        update = { view ->
            if (lastLoadedUrl != url) {
                lastLoadedUrl = url
                view.loadUrl(url)
            }
        }
    )
}

private fun applyCameraPermissionResult(
    permissions: Map<String, Boolean>,
    request: PermissionRequest?,
) {
    request ?: return
    val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
    val audioRequested = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
    val audioGranted = !audioRequested || (permissions[Manifest.permission.RECORD_AUDIO] ?: false)
    if (cameraGranted && audioGranted) request.grant(request.resources) else request.deny()
}

private fun cameraPermissionsFor(request: PermissionRequest): Array<String> {
    val permissions = mutableListOf(Manifest.permission.CAMERA)
    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
        permissions.add(Manifest.permission.RECORD_AUDIO)
    }
    return permissions.toTypedArray()
}

private data class PayCoreWebViewCallbacks(
    val onInterceptBackUrl: () -> Unit,
    val backUrlPrefix: () -> String,
    val onCameraPermissionRequest: (PermissionRequest) -> Unit,
    val onFileChooserRequest: (ValueCallback<Array<Uri>>, String) -> Unit,
    val onProgressChanged: (Int) -> Unit,
)

private fun createPayCoreWebView(
    context: Context,
    url: String,
    callbacks: PayCoreWebViewCallbacks,
): WebView {
    val onInterceptBackUrl = callbacks.onInterceptBackUrl
    val backUrlPrefix = callbacks.backUrlPrefix
    val onCameraPermissionRequest = callbacks.onCameraPermissionRequest
    val onFileChooserRequest = callbacks.onFileChooserRequest
    val onProgressChanged = callbacks.onProgressChanged
    return WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.isAlgorithmicDarkeningAllowed = false
        }
        CookieManager.getInstance().also { cookieManager ->
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val requestUrl = request.url.toString()
                Timber.tag(LOG_TAG).d("shouldOverrideUrlLoading ${request.url}")
                if (requestUrl.startsWith(backUrlPrefix())) {
                    onInterceptBackUrl()
                    return true
                }
                if (isExternalAppUri(request.url)) {
                    launchExternalAppOrFallback(view.context, requestUrl)
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                Timber.e("$LOG_TAG resource error ${error.errorCode} '${error.description}' for ${request.url}")
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Timber.e("$LOG_TAG http error ${errorResponse.statusCode} for ${request.url}")
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                onProgressChanged(newProgress)
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val popupWebView = WebView(view.context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            popupView: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val requestUrl = request.url.toString()
                            if (requestUrl.startsWith(backUrlPrefix())) {
                                onInterceptBackUrl()
                                return true
                            }
                            if (isExternalAppUri(request.url)) {
                                launchExternalAppOrFallback(view.context, requestUrl)
                                return true
                            }
                            view.loadUrl(requestUrl)
                            return true
                        }
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popupWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val neededPermissions = mutableListOf<String>()
                if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                    !hasCameraPermission(context)
                ) {
                    neededPermissions.add(Manifest.permission.CAMERA)
                }
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                    !hasAudioPermission(context)
                ) {
                    neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                }

                if (neededPermissions.isEmpty()) {
                    request.grant(request.resources)
                } else {
                    onCameraPermissionRequest(request)
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                request.deny()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                onFileChooserRequest(
                    filePathCallback,
                    resolveFileChooserMimeType(fileChooserParams)
                )
                return true
            }
        }
        loadUrl(url)
    }
}

private const val LOG_TAG = "PayCoreWebView"

private fun resolveFileChooserMimeType(fileChooserParams: WebChromeClient.FileChooserParams): String {
    val acceptedTypes = fileChooserParams.acceptTypes
        .asSequence()
        .flatMap { it.split(',').asSequence() }
        .map { it.trim() }
        .filter { '/' in it }
        .distinct()
        .toList()

    return when (acceptedTypes.size) {
        0 -> "image/*"
        1 -> acceptedTypes.first()
        else -> "*/*"
    }
}

private val WEB_HANDLED_SCHEMES = setOf(
    "http", "https", "about", "blob", "data", "javascript", "file", "ftp"
)

private fun isExternalAppUri(uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false
    return scheme !in WEB_HANDLED_SCHEMES
}

private fun launchExternalAppOrFallback(context: Context, requestUrl: String) {
    val intent = parseExternalAppIntent(requestUrl) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "$LOG_TAG no app for $requestUrl")
        intent.getStringExtra("browser_fallback_url")?.takeIf { it.isNotBlank() }?.let { fallback ->
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, fallback.toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: ActivityNotFoundException) {
                Timber.w(e2, "$LOG_TAG fallback failed $fallback")
            }
        }
    }
}

private fun parseExternalAppIntent(requestUrl: String): Intent? = try {
    if (requestUrl.startsWith("intent:", ignoreCase = true)) {
        Intent.parseUri(requestUrl, Intent.URI_INTENT_SCHEME).apply {
            component = null
            selector = null
        }
    } else {
        Intent(Intent.ACTION_VIEW, requestUrl.toUri())
    }
} catch (e: Exception) {
    Timber.e(e, "$LOG_TAG failed to parse $requestUrl")
    null
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PayCoreWebViewScreenPreview() {
    ComposeAppTheme {
        PayCoreWebViewScreen(
            url = "https://example.com",
            title = "KYC",
            onClose = {},
            onInterceptBackUrl = {},
            backUrlPrefix = "https://example.com/close"
        )
    }
}
