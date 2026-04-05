package com.qzone.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import com.qzone.android.ui.theme.QQZoneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QQZoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    QQZoneApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun QQZoneApp(viewModel: MainViewModel) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val background = Brush.linearGradient(
        colors = listOf(
            Color(0xFF112A46),
            Color(0xFF0A6E78),
            Color(0xFFF2A541),
        ),
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val treeUri = data.data ?: return@rememberLauncherForActivityResult
        val persistFlags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, persistFlags)
        } catch (_: SecurityException) {
        }

        viewModel.onExportFolderSelected(treeUri, describeTreeUri(context, treeUri))
    }

    val openFolderPicker = {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
            if (state.selectedFolderUri.isNotBlank()) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(state.selectedFolderUri))
            }
        }
        folderPickerLauncher.launch(intent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            PanelCard(
                title = state.headline,
                subtitle = state.statusMessage,
            ) {
                when (state.screen) {
                    AppScreen.Login -> LoginScreen(
                        state = state,
                        onStartLogin = viewModel::beginWebLogin,
                    )

                    AppScreen.LoginWeb -> LoginWebScreen(
                        isBusy = state.isBusy,
                        onLoginDetected = viewModel::completeWebLogin,
                        onBack = viewModel::cancelWebLogin,
                    )

                    AppScreen.AlbumSelection -> AlbumSelectionScreen(
                        state = state,
                        onPickFolder = openFolderPicker,
                        onSelectAll = viewModel::selectAllAlbums,
                        onClearAll = viewModel::clearAllAlbums,
                        onToggleAlbum = viewModel::toggleAlbumSelection,
                        onStartDownload = viewModel::startSelectedDownload,
                    )

                    AppScreen.Downloading -> DownloadingScreen(
                        state = state,
                        onCancel = viewModel::cancelDownload,
                    )

                    AppScreen.Exporting -> ExportingScreen(state = state)

                    AppScreen.Result -> ResultScreen(
                        state = state,
                        onChooseMore = viewModel::returnToAlbums,
                        onSignInAgain = viewModel::resetToLogin,
                    )

                    AppScreen.Error -> ErrorScreen(
                        state = state,
                        onPrimaryAction = viewModel::handlePrimaryErrorAction,
                        onBack = viewModel::resetToLogin,
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF7FFF8EF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF12263A),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF284B63),
            )
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun LoginScreen(state: AppUiState, onStartLogin: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Sign in with the official QQ web page, load your accessible albums, pick the albums you want, and then export them into a folder of your choice.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF284B63),
        )
        StatLine(label = "Current export folder", value = state.selectedFolderLabel)
        Button(
            onClick = onStartLogin,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign In With QQ")
        }
    }
}

@Composable
private fun LoginWebScreen(
    isBusy: Boolean,
    onLoginDetected: (String) -> Unit,
    onBack: () -> Unit,
) {
    var loginHandled by remember { mutableStateOf(false) }
    var collectingCookies by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val observedUrls = remember { linkedSetOf<String>() }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Use the official QQ page below to enter your account and password. After QQ finishes sign in, the app will import the web session and load your albums.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF284B63),
        )

        if (isBusy || collectingCookies) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (collectingCookies) {
            Text(
                text = "QQ has finished signing in. Waiting for the web session cookies to settle before importing the session...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF284B63),
            )
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(20.dp)),
            factory = { context ->
                WebView(context).apply {
                    webViewRef = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(false)

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            recordObservedUrl(request?.url?.toString().orEmpty())
                            maybeHandleSuccess(request?.url?.toString().orEmpty())
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            recordObservedUrl(url.orEmpty())
                            maybeHandleSuccess(url.orEmpty())
                        }

                        private fun recordObservedUrl(url: String) {
                            val trimmed = url.trim()
                            if (trimmed.isNotBlank()) {
                                observedUrls += trimmed
                            }
                        }

                        private fun maybeHandleSuccess(url: String) {
                            if (loginHandled || !isQzoneLoginSuccessUrl(url)) {
                                return
                            }

                            loginHandled = true
                            collectingCookies = true

                            coroutineScope.launch {
                                val loginCookieManager = CookieManager.getInstance()
                                var latestSnapshot = CollectedCookies(
                                    header = "",
                                    keys = emptyList(),
                                    sampledUrls = emptyList(),
                                    hitUrls = emptyList(),
                                )

                                repeat(6) { index ->
                                    recordObservedUrl(webViewRef?.url.orEmpty())
                                    loginCookieManager.flush()
                                    latestSnapshot = collectQzoneCookies(
                                        cookieManager = loginCookieManager,
                                        successUrl = url,
                                        observedUrls = observedUrls.toList(),
                                    )
                                    logCollectedCookies(index + 1, latestSnapshot)

                                    if (hasRequiredQzoneCookies(latestSnapshot.header)) {
                                        collectingCookies = false
                                        onLoginDetected(latestSnapshot.header)
                                        return@launch
                                    }

                                    delay(700)
                                }

                                Log.w(
                                    "QzoneWebLogin",
                                    "Proceeding with the best available cookie snapshot after retries. keys=${
                                        latestSnapshot.keys.joinToString(",")
                                    }",
                                )
                                collectingCookies = false
                                onLoginDetected(latestSnapshot.header)
                            }
                        }
                    }

                    loadUrl(QZONE_WEB_LOGIN_URL)
                }
            },
        )

        OutlinedButton(
            onClick = onBack,
            enabled = !isBusy && !collectingCookies,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back To Home")
        }
    }
}

@Composable
private fun AlbumSelectionScreen(
    state: AppUiState,
    onPickFolder: () -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onToggleAlbum: (String) -> Unit,
    onStartDownload: () -> Unit,
) {
    val selectedCount = state.albums.count { it.selected }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatLine(label = "Account", value = state.nickname.ifBlank { state.qq })
        StatLine(label = "QQ", value = state.qq.ifBlank { "-" })
        StatLine(label = "Export folder", value = state.selectedFolderLabel)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPickFolder,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.selectedFolderReady) "Change Folder" else "Choose Folder")
            }

            OutlinedButton(
                onClick = onSelectAll,
                enabled = state.albums.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Select All")
            }
        }

        OutlinedButton(
            onClick = onClearAll,
            enabled = state.albums.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear All")
        }

        if (state.hiddenAlbumCount > 0) {
            Text(
                text = "${state.hiddenAlbumCount} private or password-protected albums are not available for download.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF7A1C1C),
            )
        }

        if (state.albums.isEmpty()) {
            Text(
                text = "No accessible albums were found for this account.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF284B63),
            )
        } else {
            state.albums.forEach { album ->
                AlbumRow(
                    album = album,
                    onToggle = { onToggleAlbum(album.id) },
                )
            }
        }

        Button(
            onClick = onStartDownload,
            enabled = state.selectedFolderReady && selectedCount > 0 && !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Download Selected Albums ($selectedCount)")
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x0F12263A))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = album.selected,
            onCheckedChange = { onToggle() },
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name.ifBlank { "(Unnamed Album)" },
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF12263A),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${album.total} items",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF284B63),
            )
        }
    }
}

@Composable
private fun DownloadingScreen(state: AppUiState, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatLine(label = "Account", value = state.nickname.ifBlank { state.qq })
        StatLine(label = "Export folder", value = state.selectedFolderLabel)
        StatLine(label = "Working directory", value = state.stagingDir.ifBlank { "Preparing..." })
        StatLine(label = "Total / Success / Failed", value = "${state.total} / ${state.success} / ${state.failed}")
        StatLine(label = "Images / Videos", value = "${state.images} / ${state.videos}")
        StatLine(label = "Current album", value = state.currentAlbum.ifBlank { "-" })
        StatLine(label = "Current file", value = state.currentFile.ifBlank { "-" })

        if (state.total > 0) {
            LinearProgressIndicator(
                progress = ((state.success + state.failed).toFloat() / state.total.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel Download")
        }
    }
}

@Composable
private fun ExportingScreen(state: AppUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatLine(label = "Account", value = state.nickname.ifBlank { state.qq })
        StatLine(label = "Export folder", value = state.selectedFolderLabel)
        StatLine(label = "Working directory", value = state.stagingDir.ifBlank { "-" })
        StatLine(
            label = "Export progress",
            value = "${state.exportCompletedFiles} / ${state.exportTotalFiles.takeIf { it > 0 } ?: 0}",
        )
        StatLine(label = "Current file", value = state.exportCurrentFile.ifBlank { "-" })

        if (state.exportTotalFiles > 0) {
            LinearProgressIndicator(
                progress = (state.exportCompletedFiles.toFloat() / state.exportTotalFiles.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ResultScreen(
    state: AppUiState,
    onChooseMore: () -> Unit,
    onSignInAgain: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "The selected albums were downloaded and exported into your chosen folder.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF284B63),
        )
        StatLine(label = "Account", value = state.nickname.ifBlank { state.qq })
        StatLine(label = "Export folder", value = state.selectedFolderLabel)
        StatLine(label = "Success / Failed", value = "${state.success} / ${state.failed}")
        StatLine(label = "Images / Videos", value = "${state.images} / ${state.videos}")

        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onChooseMore, modifier = Modifier.fillMaxWidth()) {
            Text("Choose Other Albums")
        }
        OutlinedButton(onClick = onSignInAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Sign In Again")
        }
    }
}

@Composable
private fun ErrorScreen(
    state: AppUiState,
    onPrimaryAction: () -> Unit,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = state.statusMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF7A1C1C),
        )

        Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
            Text(
                when (state.errorAction) {
                    ErrorAction.Login -> "Retry Login"
                    ErrorAction.Albums -> "Back To Albums"
                    ErrorAction.Export -> "Retry Export"
                },
            )
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back To Home")
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF284B63),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF12263A),
        )
    }
}

private fun describeTreeUri(context: android.content.Context, uri: Uri): String {
    return DocumentFile.fromTreeUri(context, uri)?.name
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment
        ?: "Selected export folder"
}
