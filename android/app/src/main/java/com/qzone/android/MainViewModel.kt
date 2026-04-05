package com.qzone.android

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qzone.mobile.bridge.Bridge
import com.qzone.mobile.bridge.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf(AppUiState())
        private set

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var bridgeClient: Client? = null
    private var downloadPollJob: Job? = null
    private var exportJob: Job? = null

    init {
        restoreExportFolder()
    }

    fun beginWebLogin() {
        cancelBackgroundJobs()
        uiState = uiState.copy(
            screen = AppScreen.LoginWeb,
            headline = "QQ Account Sign In",
            statusMessage = "Sign in using the official QQ page inside the app.",
            qq = "",
            nickname = "",
            albums = emptyList(),
            hiddenAlbumCount = 0,
            downloadJobId = "",
            stagingDir = "",
            total = 0,
            success = 0,
            failed = 0,
            images = 0,
            videos = 0,
            currentAlbum = "",
            currentFile = "",
            exportTotalFiles = 0,
            exportCompletedFiles = 0,
            exportCurrentFile = "",
            isBusy = false,
            errorAction = ErrorAction.Login,
        )
    }

    fun cancelWebLogin() {
        resetToLogin()
    }

    fun completeWebLogin(cookieHeader: String) {
        viewModelScope.launch {
            uiState = uiState.copy(
                screen = AppScreen.LoginWeb,
                headline = "Finishing Sign In",
                statusMessage = "Importing your QQ session...",
                isBusy = true,
            )
            try {
                val client = ensureClient()
                val login = withContext(Dispatchers.IO) { parseLoginPayload(client.importWebLogin(cookieHeader)) }
                if (login.status != "success") {
                    throw IllegalStateException(login.message.ifBlank { "Failed to import the QQ web login session." })
                }
                loadAlbums(client, login)
            } catch (error: Exception) {
                showError(error.message ?: "Failed to finish QQ sign in.", ErrorAction.Login)
            }
        }
    }

    fun resetToLogin() {
        cancelBackgroundJobs()
        bridgeClient = null
        uiState = uiState.copy(
            screen = AppScreen.Login,
            headline = "QQ Zone Android",
            statusMessage = "Sign in with QQ to load your albums.",
            qq = "",
            nickname = "",
            albums = emptyList(),
            hiddenAlbumCount = 0,
            downloadJobId = "",
            stagingDir = "",
            total = 0,
            success = 0,
            failed = 0,
            images = 0,
            videos = 0,
            currentAlbum = "",
            currentFile = "",
            exportTotalFiles = 0,
            exportCompletedFiles = 0,
            exportCurrentFile = "",
            isBusy = false,
            errorAction = ErrorAction.Login,
        )
    }

    fun returnToAlbums(message: String = "Choose albums and a target folder, then start the download.") {
        cancelBackgroundJobs()
        if (uiState.albums.isEmpty()) {
            resetToLogin()
            return
        }

        uiState = uiState.copy(
            screen = AppScreen.AlbumSelection,
            headline = "Choose Albums",
            statusMessage = message,
            downloadJobId = "",
            total = 0,
            success = 0,
            failed = 0,
            images = 0,
            videos = 0,
            currentAlbum = "",
            currentFile = "",
            exportTotalFiles = 0,
            exportCompletedFiles = 0,
            exportCurrentFile = "",
            isBusy = false,
            errorAction = ErrorAction.Albums,
        )
    }

    fun toggleAlbumSelection(albumId: String) {
        uiState = uiState.copy(
            albums = uiState.albums.map { album ->
                if (album.id == albumId) album.copy(selected = !album.selected) else album
            },
        )
    }

    fun selectAllAlbums() {
        uiState = uiState.copy(albums = uiState.albums.map { it.copy(selected = true) })
    }

    fun clearAllAlbums() {
        uiState = uiState.copy(albums = uiState.albums.map { it.copy(selected = false) })
    }

    fun onExportFolderSelected(uri: Uri, label: String) {
        prefs.edit()
            .putString(PREF_EXPORT_TREE_URI, uri.toString())
            .putString(PREF_EXPORT_TREE_LABEL, label)
            .apply()

        uiState = uiState.copy(
            selectedFolderUri = uri.toString(),
            selectedFolderLabel = label,
            selectedFolderReady = true,
            statusMessage = if (uiState.screen == AppScreen.AlbumSelection) {
                "Export folder updated. You can start the download now."
            } else {
                uiState.statusMessage
            },
        )
    }

    fun startSelectedDownload() {
        val selectedAlbums = uiState.albums.filter { it.selected }
        if (selectedAlbums.isEmpty()) {
            showError("Please select at least one album.", ErrorAction.Albums)
            return
        }
        if (!uiState.selectedFolderReady || uiState.selectedFolderUri.isBlank()) {
            showError("Please choose an export folder before downloading.", ErrorAction.Albums)
            return
        }

        viewModelScope.launch {
            try {
                val client = ensureClient()
                val selectedJson = JSONArray(selectedAlbums.map { it.id }).toString()
                val jobId = withContext(Dispatchers.IO) { client.startSelectedDownload(selectedJson) }

                uiState = uiState.copy(
                    screen = AppScreen.Downloading,
                    headline = "Downloading Albums",
                    statusMessage = "Downloading the selected albums into the app working directory...",
                    downloadJobId = jobId,
                    stagingDir = "",
                    total = 0,
                    success = 0,
                    failed = 0,
                    images = 0,
                    videos = 0,
                    currentAlbum = "",
                    currentFile = "",
                    exportTotalFiles = 0,
                    exportCompletedFiles = 0,
                    exportCurrentFile = "",
                    isBusy = false,
                    errorAction = ErrorAction.Albums,
                )

                startDownloadPolling(client, jobId)
            } catch (error: Exception) {
                showError(error.message ?: "Failed to start the selected download.", ErrorAction.Albums)
            }
        }
    }

    fun cancelDownload() {
        val client = bridgeClient ?: return
        val currentJobId = uiState.downloadJobId
        if (currentJobId.isBlank()) {
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { client.cancelJob(currentJobId) }
                uiState = uiState.copy(statusMessage = "Cancelling the download job...")
            } catch (error: Exception) {
                showError(error.message ?: "Failed to cancel the download job.", ErrorAction.Albums)
            }
        }
    }

    fun handlePrimaryErrorAction() {
        when (uiState.errorAction) {
            ErrorAction.Login -> beginWebLogin()
            ErrorAction.Albums -> returnToAlbums("Choose albums and try again.")
            ErrorAction.Export -> retryExport()
        }
    }

    private suspend fun loadAlbums(client: Client, login: LoginPayload) {
        val albumsPayload = withContext(Dispatchers.IO) { parseAlbumListPayload(client.listSelfAlbums()) }
        if (albumsPayload.status != "success") {
            throw IllegalStateException(albumsPayload.message.ifBlank { "Failed to load your QQ albums." })
        }

        val nickname = albumsPayload.nickname.ifBlank {
            login.nickname.ifBlank {
                login.qq
            }
        }

        uiState = uiState.copy(
            screen = AppScreen.AlbumSelection,
            headline = "Choose Albums",
            statusMessage = if (albumsPayload.albums.isEmpty()) {
                "No accessible albums were found for this account."
            } else {
                "Choose albums and a target folder, then start the download."
            },
            qq = login.qq,
            nickname = nickname,
            albums = if (albumsPayload.albums.isEmpty()) {
                emptyList()
            } else {
                albumsPayload.albums.map { it.copy(selected = true) }
            },
            hiddenAlbumCount = albumsPayload.hiddenCount,
            downloadJobId = "",
            stagingDir = "",
            total = 0,
            success = 0,
            failed = 0,
            images = 0,
            videos = 0,
            currentAlbum = "",
            currentFile = "",
            exportTotalFiles = 0,
            exportCompletedFiles = 0,
            exportCurrentFile = "",
            isBusy = false,
            errorAction = ErrorAction.Albums,
        )
    }

    private fun startDownloadPolling(client: Client, jobId: String) {
        downloadPollJob?.cancel()
        downloadPollJob = viewModelScope.launch {
            while (isActive) {
                val payload = try {
                    withContext(Dispatchers.IO) { parseJobPayload(client.getJobStatus(jobId)) }
                } catch (error: Exception) {
                    showError(error.message ?: "Failed to poll the download status.", ErrorAction.Albums)
                    return@launch
                }

                uiState = uiState.copy(
                    screen = AppScreen.Downloading,
                    headline = "Downloading Albums",
                    statusMessage = payload.message,
                    downloadJobId = jobId,
                    stagingDir = payload.saveDir,
                    total = payload.total,
                    success = payload.success,
                    failed = payload.failed,
                    images = payload.images,
                    videos = payload.videos,
                    currentAlbum = payload.currentAlbum,
                    currentFile = payload.currentFile,
                )

                when (payload.status) {
                    "success" -> {
                        startExport(payload.saveDir)
                        return@launch
                    }

                    "cancelled" -> {
                        returnToAlbums("Download cancelled.")
                        return@launch
                    }

                    "error" -> {
                        showError(payload.message.ifBlank { "The download failed." }, ErrorAction.Albums)
                        return@launch
                    }
                }

                delay(1_000)
            }
        }
    }

    private fun retryExport() {
        val stagingDir = uiState.stagingDir
        if (stagingDir.isBlank()) {
            showError("There is no staged download to export.", ErrorAction.Albums)
            return
        }
        startExport(stagingDir)
    }

    private fun startExport(stagingDir: String) {
        val folderUri = uiState.selectedFolderUri
        if (folderUri.isBlank()) {
            showError("Please choose an export folder before exporting files.", ErrorAction.Albums)
            return
        }
        if (stagingDir.isBlank()) {
            showError("The staged download directory is missing.", ErrorAction.Albums)
            return
        }

        val qq = uiState.qq
        val nickname = uiState.nickname
        val folderLabel = uiState.selectedFolderLabel
        val sourceRoot = File(stagingDir)

        uiState = uiState.copy(
            screen = AppScreen.Exporting,
            headline = "Exporting Files",
            statusMessage = "Copying downloaded files to $folderLabel...",
            stagingDir = stagingDir,
            exportTotalFiles = 0,
            exportCompletedFiles = 0,
            exportCurrentFile = "",
            isBusy = true,
        )

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    exportWorkingTree(
                        context = getApplication(),
                        sourceRoot = sourceRoot,
                        targetTreeUri = Uri.parse(folderUri),
                        qq = qq,
                    ) { snapshot ->
                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                screen = AppScreen.Exporting,
                                headline = "Exporting Files",
                                statusMessage = "Copying downloaded files to $folderLabel...",
                                exportTotalFiles = snapshot.totalFiles,
                                exportCompletedFiles = snapshot.completedFiles,
                                exportCurrentFile = snapshot.currentFile,
                                currentFile = snapshot.currentFile,
                            )
                        }
                    }
                }

                uiState = uiState.copy(
                    screen = AppScreen.Result,
                    headline = "Export Complete",
                    statusMessage = "The selected albums were exported successfully.",
                    qq = qq,
                    nickname = nickname,
                    isBusy = false,
                )
            } catch (error: Exception) {
                showError(error.message ?: "Failed to export the downloaded files.", ErrorAction.Export)
            }
        }
    }

    private fun ensureClient(): Client {
        bridgeClient?.let { return it }

        val app = getApplication<Application>()
        val root = app.getExternalFilesDir(null) ?: app.filesDir
        val workRoot = File(root, "qq-zone-work")
        if (!workRoot.exists()) {
            workRoot.mkdirs()
        }

        return Bridge.newClient(workRoot.absolutePath).also { bridgeClient = it }
    }

    private fun restoreExportFolder() {
        val uri = prefs.getString(PREF_EXPORT_TREE_URI, "").orEmpty()
        val label = prefs.getString(PREF_EXPORT_TREE_LABEL, "").orEmpty()
        if (uri.isBlank()) {
            return
        }

        uiState = uiState.copy(
            selectedFolderUri = uri,
            selectedFolderLabel = if (label.isBlank()) "Selected export folder" else label,
            selectedFolderReady = true,
        )
    }

    private fun cancelBackgroundJobs() {
        downloadPollJob?.cancel()
        exportJob?.cancel()
    }

    private fun showError(message: String, action: ErrorAction) {
        cancelBackgroundJobs()
        uiState = uiState.copy(
            screen = AppScreen.Error,
            headline = "Something Went Wrong",
            statusMessage = message,
            isBusy = false,
            errorAction = action,
        )
    }

    private fun parseLoginPayload(json: String): LoginPayload {
        val payload = JSONObject(json)
        return LoginPayload(
            status = payload.optString("status"),
            message = payload.optString("message"),
            qq = payload.optString("qq"),
            nickname = payload.optString("nickname"),
        )
    }

    private fun parseAlbumListPayload(json: String): AlbumListPayload {
        val payload = JSONObject(json)
        val rawAlbums = payload.optJSONArray("albums") ?: JSONArray()
        val albums = buildList {
            for (index in 0 until rawAlbums.length()) {
                val album = rawAlbums.optJSONObject(index) ?: continue
                add(
                    AlbumItem(
                        id = album.optString("id"),
                        name = album.optString("name"),
                        total = album.optInt("total"),
                        selected = true,
                    ),
                )
            }
        }

        return AlbumListPayload(
            status = payload.optString("status"),
            message = payload.optString("message"),
            qq = payload.optString("qq"),
            nickname = payload.optString("nickname"),
            hiddenCount = payload.optInt("hiddenCount"),
            albums = albums,
        )
    }

    private fun parseJobPayload(json: String): JobPayload {
        val payload = JSONObject(json)
        return JobPayload(
            status = payload.optString("status"),
            phase = payload.optString("phase"),
            total = payload.optInt("total"),
            success = payload.optInt("success"),
            failed = payload.optInt("failed"),
            images = payload.optInt("images"),
            videos = payload.optInt("videos"),
            currentAlbum = payload.optString("currentAlbum"),
            currentFile = payload.optString("currentFile"),
            saveDir = payload.optString("saveDir"),
            message = payload.optString("message"),
        )
    }

    companion object {
        private const val PREFS_NAME = "qq_zone_android_prefs"
        private const val PREF_EXPORT_TREE_URI = "pref_export_tree_uri"
        private const val PREF_EXPORT_TREE_LABEL = "pref_export_tree_label"
    }
}
