# qq-zone

QQ 空间相册下载项目，现阶段以 **Android App** 为主。

项目当前的主要目标，是让用户在手机端完成 QQ 登录、相册选择、导出目录选择和下载导出，不再以早期的 Windows 终端程序为核心使用方式。

## 当前定位

这已经不是一个单纯的 Go 命令行爬虫仓库，而是一个：

- Android 前端：`Kotlin + Jetpack Compose`
- Go 核心：负责 QQ 空间登录会话导入、相册列表读取、媒体下载
- Go/Android 桥接：通过 `gomobile bind` 生成 `AAR` 供 Android 调用

当前 App 主要流程为：

1. 在 App 内打开 QQ 官方网页登录页
2. 登录成功后导入网页 Cookie 会话
3. 拉取当前账号可访问的相册列表
4. 勾选要下载的相册
5. 选择导出文件夹
6. 先下载到应用工作目录，再导出到用户选择的目录

## 当前功能

- QQ 官方网页登录
- 拉取当前账号可访问相册
- 相册多选下载
- 自定义导出文件夹
- 下载进度显示
- 导出进度显示
- 生成 Android Debug APK

## 当前限制

- 目前以 Android 为主，不再优先维护桌面终端体验
- 目前只支持“当前登录账号自己可访问的相册”
- 私密相册、需要密码的相册不会出现在可下载列表中
- 下载阶段需要保持 App 存活；被系统杀掉后不保证续传
- 文件会先落到应用工作目录，再导出到目标目录，因此下载过程中会临时占用额外空间
- 当前默认生成的是 `Debug APK`，不是正式发布包

## 目录结构

```text
android/               Android Studio 工程
mobile/core/           Go 核心下载逻辑
mobile/bridge/         gomobile 对外桥接层
utils/qzone/           QQ 空间接口与登录相关底层逻辑
utils/net/http/        下载和请求封装
scripts/               构建 AAR / APK 的脚本
tools/                 构建工具依赖声明
```

## 开发环境

- Go `1.25+`
- Android Studio
- Android SDK `33`
- Android NDK `27.3.13750724`
- `gomobile`

如果本机还没有准备好 NDK，可以先运行：

```powershell
.\scripts\install_android_ndk.ps1
```

## 构建 Android APK

在仓库根目录执行：

```powershell
.\scripts\build_android_debug.ps1
```

构建完成后，产物位于：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

如果只想重新生成 Go Bridge 的 AAR，可以执行：

```powershell
.\scripts\bind_android_bridge.ps1
```

生成结果位于：

```text
android/app/libs/qqzone-mobile.aar
```

## 在模拟器或手机上运行

### 方式一：Android Studio

1. 用 Android Studio 打开 `android/`
2. 等待 Gradle 同步完成
3. 选择模拟器或真机
4. 点击运行，或直接 `Build > Build APK(s)`

### 方式二：命令行

先构建 APK：

```powershell
.\scripts\build_android_debug.ps1
```

再安装到设备：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r ".\android\app\build\outputs\apk\debug\app-debug.apk"
```

## 项目分层说明

### `android/app`

负责界面、WebView 登录、相册选择、目录选择和导出流程。

关键文件：

- `android/app/src/main/java/com/qzone/android/MainActivity.kt`
- `android/app/src/main/java/com/qzone/android/MainViewModel.kt`
- `android/app/src/main/java/com/qzone/android/QzoneWebLogin.kt`
- `android/app/src/main/java/com/qzone/android/StorageExport.kt`

### `mobile/core`

负责登录会话导入、相册拉取、任务下载、任务状态查询。

关键文件：

- `mobile/core/client.go`
- `mobile/core/albums.go`
- `mobile/core/download.go`

### `mobile/bridge`

负责把 Go 能力暴露给 Android 调用。

关键文件：

- `mobile/bridge/bridge.go`

## 未來可能的改進方向
- 保持登錄狀態
- 優化相冊選擇界面
- 實現直接下載在用戶選擇的目錄，而不是先下載到應用工作目錄