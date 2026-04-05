package com.qzone.android

enum class AppScreen {
    Login,
    LoginWeb,
    AlbumSelection,
    Downloading,
    Exporting,
    Result,
    Error,
}

enum class ErrorAction {
    Login,
    Albums,
    Export,
}

data class AlbumItem(
    val id: String,
    val name: String,
    val total: Int,
    val selected: Boolean = true,
)

data class LoginPayload(
    val status: String,
    val message: String,
    val qq: String,
    val nickname: String,
)

data class AlbumListPayload(
    val status: String,
    val message: String,
    val qq: String,
    val nickname: String,
    val hiddenCount: Int,
    val albums: List<AlbumItem>,
)

data class JobPayload(
    val status: String,
    val phase: String,
    val total: Int,
    val success: Int,
    val failed: Int,
    val images: Int,
    val videos: Int,
    val currentAlbum: String,
    val currentFile: String,
    val saveDir: String,
    val message: String,
)

data class AppUiState(
    val screen: AppScreen = AppScreen.Login,
    val headline: String = "QQ Zone Android",
    val statusMessage: String = "Sign in with QQ to load your albums.",
    val qq: String = "",
    val nickname: String = "",
    val albums: List<AlbumItem> = emptyList(),
    val hiddenAlbumCount: Int = 0,
    val selectedFolderUri: String = "",
    val selectedFolderLabel: String = "No export folder selected.",
    val selectedFolderReady: Boolean = false,
    val downloadJobId: String = "",
    val stagingDir: String = "",
    val total: Int = 0,
    val success: Int = 0,
    val failed: Int = 0,
    val images: Int = 0,
    val videos: Int = 0,
    val currentAlbum: String = "",
    val currentFile: String = "",
    val exportTotalFiles: Int = 0,
    val exportCompletedFiles: Int = 0,
    val exportCurrentFile: String = "",
    val isBusy: Boolean = false,
    val errorAction: ErrorAction = ErrorAction.Login,
)
